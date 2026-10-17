# AWS EC2 deployment plan (not ready to execute)

This PR adds CI and **manual-only** CD in the `villa-serena-api` repo.
PostgreSQL is a **different Compose project** (`villa-serena-db`), and
deployment uses `docker compose -p villa-serena-api ... up -d --no-deps api`.

## Prerequisites (all must be completed before dispatch)

1. The `villa-serena-api-deploy-role` exists from infra IAM, but **initially
   had no permissions**. Add least-privilege ECR push and SSM Run Command
   permissions for this EC2 and `AWS-RunShellScript`; add narrowly scoped
   ecr pull and SSM parameter read permissions to the EC2 instance role.
   Review IAM policies in an **infra PR**, not here.
2. Create a private **ECR repository** named by `AWS_ECR_REPOSITORY`.
   Keep it in the same AWS region and account. Use Terraform in infra.
3. Create these GitHub **repository variables** in `villa-serena-api`:
   `AWS_API_DEPLOY_ROLE_ARN`, `AWS_REGION`,
   `AWS_ECR_REPOSITORY`, `AWS_EC2_INSTANCE_ID`.
4. In AWS SSM Parameter Store, create these **SecureString** parameters:
   `/villa-serena/prod/api/SPRING_DATASOURCE_PASSWORD`
   (same value as database initialization password),
   `/villa-serena/prod/api/DEMO_PASSWORD_HASH`,
   `/villa-serena/prod/api/CANAL1_KEY_HASH`,
   `/villa-serena/prod/api/CANAL2_KEY_HASH`.
   Do not commit or print secret values. The deploy script writes a
   root-only env file on EC2.
5. Verify production application configuration, Flyway and Spring Security.
   The current `application.yaml` defines Flyway placeholders but no
   explicit database profile configuration. The deployment injects JDBC
   URL and username through Compose; JDBC password comes from SSM.
6. Confirm `/actuator/health` is accessible locally without auth for the
   deployment health gate, or update the gate to a suitable non-secret
   readiness endpoint. Ensure the API's Spring Security remains enabled.

## Notes

- Docker image is ARM64 for EC2 Graviton.
- Images are tagged by commit SHA for traceable deployments.
- CI uses the Maven wrapper with Java 21.
- CD currently uses `workflow_dispatch`, not push-to-main auto deploy.
  Turn on automatic deployment **only after** successful end-to-end test.
- EC2 exposes only host TCP 8080 to the API Gateway VPC Link security group.
  The PostgreSQL container does not publish port 5432.
- Dockerfile presently has assumptions copied from Neversion. In particular
  `api-*.jar` should be verified against Spring Boot's generated JAR name,
  and its `prod` profile should be examined before the first deployment.
