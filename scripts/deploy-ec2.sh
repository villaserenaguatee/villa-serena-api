#!/usr/bin/env bash
set -euo pipefail
# Executed on EC2 by SSM Run Command, not by a public SSH endpoint.
: "${IMAGE_URI:?IMAGE_URI required}"
: "${AWS_REGION:?AWS_REGION required}"

deploy_dir=/opt/villa-serena/api
parameter_root=/villa-serena/prod/api
mkdir -p "$deploy_dir"
chmod 700 "$deploy_dir"

echo "[deploy:1/4] Retrieving application parameters from AWS SSM ($parameter_root)..."
# Values must exist before first deployment. Preserve shell metacharacters
# by serializing via Python (no eval/source of untrusted parameter contents).
python3 - "$parameter_root" "$AWS_REGION" "$deploy_dir/.env" <<'PY'
import json, subprocess, sys
root, region, dest = sys.argv[1:]
out = subprocess.check_output(
    ["aws", "ssm", "get-parameters-by-path", "--path", root,
     "--recursive", "--with-decryption", "--region", region,
     "--output", "json"], text=True)
entries = json.loads(out)["Parameters"]
required = {"SPRING_DATASOURCE_PASSWORD", "JWT_SECRET", "WEB_URL", "CORS_ORIGENES"}
values = {p["Name"].rsplit("/", 1)[-1]: p["Value"] for p in entries}
missing = {key for key in required if not values.get(key)}
if missing:
    raise SystemExit("Missing required application SSM parameters: " + ", ".join(sorted(missing)))
# Docker env_file supports quoted strings; reject newlines and controls.
for key, value in values.items():
    if not key.isidentifier() or not key.isupper():
        raise SystemExit(f"Unexpected parameter name: {key}")
    if any(ch in value for ch in "\r\n\x00"):
        raise SystemExit(f"Unsupported character in parameter: {key}")
with open(dest + ".tmp", "w") as fh:
    for key, value in sorted(values.items()):
        # Double-quoted Compose env values need escaped dollars and quotes.
        safe = value.replace("\\", "\\\\").replace('"', '\\"').replace("$", "$$")
        fh.write(f'{key}="{safe}"\n')
import os
os.chmod(dest + ".tmp", 0o600)
os.replace(dest + ".tmp", dest)
PY
echo "[deploy:1/4] Parameters successfully validated and written to $deploy_dir/.env"

# Public GHCR image: EC2 pulls without registry credentials.
cd "$deploy_dir"
# Compose file is installed by the SSM command as a separate payload.
echo "[deploy:2/4] Pulling Docker image: $IMAGE_URI..."
API_ENV_FILE=.env IMAGE_URI="$IMAGE_URI" docker compose -p villa-serena-api -f compose.yml pull api
echo "[deploy:2/4] Docker image pulled successfully."

echo "[deploy:3/4] Recreating API service container..."
API_ENV_FILE=.env IMAGE_URI="$IMAGE_URI" docker compose -p villa-serena-api -f compose.yml up -d --no-deps api
echo "[deploy:3/4] Container started."

echo "[deploy:4/4] Verifying API health on http://127.0.0.1:8080/actuator/health..."
# Check via local EC2 port (the path may be protected; 401/403 indicates
# server is responding but isn't sufficient to prove application readiness).
for i in $(seq 1 30); do
  if curl -fsS http://127.0.0.1:8080/actuator/health >/dev/null; then
    echo "[deploy:4/4] API health check passed successfully."
    exit 0
  fi
  sleep 5
done
echo "API health check did not pass within 150 seconds" >&2
echo "--- Docker Compose Status ---" >&2
docker compose -p villa-serena-api -f compose.yml ps >&2
echo "--- Recent Application Logs (last 50 lines) ---" >&2
docker compose -p villa-serena-api -f compose.yml logs --tail=50 api >&2
exit 1
