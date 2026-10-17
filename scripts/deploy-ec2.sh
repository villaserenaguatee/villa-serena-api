#!/usr/bin/env bash
set -euo pipefail
# Executed on EC2 by SSM Run Command, not by a public SSH endpoint.
: "${IMAGE_URI:?IMAGE_URI required}"
: "${AWS_REGION:?AWS_REGION required}"

deploy_dir=/opt/villa-serena/api
parameter_root=/villa-serena/prod/api
mkdir -p "$deploy_dir"
chmod 700 "$deploy_dir"

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
required = {"SPRING_DATASOURCE_PASSWORD", "DEMO_PASSWORD_HASH",
            "CANAL1_KEY_HASH", "CANAL2_KEY_HASH"}
values = {p["Name"].rsplit("/", 1)[-1]: p["Value"] for p in entries}
missing = required - values.keys()
if missing:
    raise SystemExit("Missing required application SSM parameters: " + ", ".join(sorted(missing)))
# Docker env_file supports quoted strings; reject newlines and controls.
for key, value in values.items():
    if not key.isidentifier() or not key.isupper():
        raise SystemExit("Unexpected parameter name")
    if any(ch in value for ch in "\r\n\x00"):
        raise SystemExit("Unsupported character in parameter")
with open(dest + ".tmp", "w") as fh:
    for key, value in sorted(values.items()):
        # Double-quoted Compose env values need escaped dollars and quotes.
        safe = value.replace("\\", "\\\\").replace('"', '\\"').replace("$", "$$")
        fh.write(f'{key}="{safe}"\n')
import os
os.chmod(dest + ".tmp", 0o600)
os.replace(dest + ".tmp", dest)
PY

# Docker auth on the EC2 requires ecr:GetAuthorizationToken on its instance role.
registry="${IMAGE_URI%%/*}"
aws ecr get-login-password --region "$AWS_REGION" |
  docker login --username AWS --password-stdin "$registry"

cd "$deploy_dir"
# Compose file is installed by the SSM command as a separate payload.
IMAGE_URI="$IMAGE_URI" docker compose -p villa-serena-api -f compose.yml pull api
IMAGE_URI="$IMAGE_URI" docker compose -p villa-serena-api -f compose.yml up -d --no-deps api
# Check via local EC2 port (the path may be protected; 401/403 indicates
# server is responding but isn't sufficient to prove application readiness).
for i in $(seq 1 30); do
  if curl -fsS http://127.0.0.1:8080/actuator/health >/dev/null; then
    echo "API healthy"
    exit 0
  fi
  sleep 5
done
echo "API health check did not pass" >&2
exit 1
