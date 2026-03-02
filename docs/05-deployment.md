# Deployment

The application runs on AWS ECS Fargate, deployed via CodePipeline + CodeBuild. All infrastructure definitions live in `cpsc-cicd-pipelines/`.

---

## Environments

| Environment | ECS Service | Trigger | Branch | Task CPU/Memory |
|------------|-------------|---------|--------|-----------------|
| devl | `cpsc-backend-service-devl` | Manual (any branch) | Configurable | 256/512 MB |
| acpt | `cpsc-backend-service-acpt` | Auto on main push | main | 512/1024 MB |
| prod | `cpsc-backend-service-prod` | Manual only | main | 1024/2048 MB |

**Cluster:** `cpsc-cluster`  
**ECR repo:** `453410498610.dkr.ecr.us-east-1.amazonaws.com/cpsc-backend-api`  
**Image tags:** `devl-latest`, `acpt-latest`, `prod-latest`

---

## Triggering Deployments

### Acceptance (automatic)
Push or merge to `main`:
```bash
git push origin main
```
`cpsc-backend-pipeline-acpt` triggers automatically via CodeStar connection.

### Development (manual, any branch)
```powershell
# Deploy main (default)
aws codepipeline start-pipeline-execution --name cpsc-backend-pipeline-devl --profile cpsc-devops

# Deploy a feature branch
aws codepipeline start-pipeline-execution --name cpsc-backend-pipeline-devl `
  --variables name=BRANCH_NAME,value=feature/my-branch --profile cpsc-devops
```

### Production (manual, main only)
```powershell
aws codepipeline start-pipeline-execution --name cpsc-backend-pipeline-prod --profile cpsc-devops
```

---

## Build Process

1. **CodeBuild** clones the repo (or reads source artifact), builds the Docker image:
   ```
   docker build → tag as {env}-latest → push to ECR
   ```
   See `buildspec.yml` in the repo root for the full build steps.

2. **CodeBuild** generates `imagedefinitions.json` pointing to the new image.

3. **CodePipeline Deploy stage** updates the ECS service with the new task definition (rolling deployment — no downtime).

Approximate build times:
- devl/acpt (SMALL compute): 5–7 min
- prod (MEDIUM compute): 8–10 min

---

## Environment Variables (ECS Task Definitions)

Set in `cpsc-cicd-pipelines/backend/ecs/task-definition-{env}.json`:

| Variable | devl | acpt | prod |
|----------|------|------|------|
| `AWS_SECRET_NAME` | `cpsc-backend/cognito-devl` | `cpsc-backend/cognito-acpt` | `cpsc-backend/cognito-prod` |
| `AWS_REGION` | `us-east-1` | `us-east-1` | `us-east-1` |
| `DYNAMODB_TABLE_NAME` | `Institutions-devl` | `Institutions-acpt` | `Institutions-prod` |
| `DYNAMODB_TRANSACTION_TABLE_NAME` | `Transactions-devl` | `Transactions-acpt` | `Transactions-prod` |
| `DYNAMODB_GOALS_TABLE_NAME` | `Goals-devl` | `Goals-acpt` | `Goals-prod` |
| `LAMBDA_ANALYTICS_FUNCTION` | `cpsc-analytics-generate-devl` | `cpsc-analytics-generate-acpt` | `cpsc-analytics-generate-prod` |
| `LAMBDA_REPORT_FUNCTION` | `cpsc-analytics-report-devl` | `cpsc-analytics-report-acpt` | `cpsc-analytics-report-prod` |
| `LAMBDA_ENDPOINT_URL` | *(empty)* | *(empty)* | *(empty)* |
| `SPRING_PROFILES_ACTIVE` | `devl` | `acpt` | `prod` |
| `JAVA_OPTS` | `-Xmx384m -Xms192m ...` | `-Xmx768m -Xms384m ...` | `-Xmx1536m -Xms768m ...` |

`LAMBDA_ENDPOINT_URL` is left empty in all deployed environments — the app uses the AWS SDK to invoke real Lambda functions using the ECS task's IAM role.

---

## Updating Task Definitions

When you add new environment variables or change resource limits:

1. Edit the task definition JSON in `cpsc-cicd-pipelines/backend/ecs/`:
   ```
   task-definition-devl.json
   task-definition-acpt.json
   task-definition-prod.json
   ```

2. Register the new revision:
   ```powershell
   aws ecs register-task-definition `
     --cli-input-json file://cpsc-cicd-pipelines/backend/ecs/task-definition-devl.json `
     --profile cpsc-devops
   ```

3. Force a new deployment (picks up the latest task definition revision):
   ```powershell
   aws ecs update-service `
     --cluster cpsc-cluster `
     --service cpsc-backend-service-devl `
     --force-new-deployment `
     --profile cpsc-devops
   ```

---

## Starting / Stopping Services

```powershell
# Start all services (from cpsc-cicd-pipelines/)
.\startup-services.ps1

# Stop all services (saves ECS costs; ALB continues billing)
.\shutdown-services.ps1
```

Or manually per environment:
```powershell
# Stop devl
aws ecs update-service --cluster cpsc-cluster --service cpsc-backend-service-devl --desired-count 0 --profile cpsc-devops

# Start devl
aws ecs update-service --cluster cpsc-cluster --service cpsc-backend-service-devl --desired-count 1 --profile cpsc-devops
```

---

## Access URLs

| Environment | API Base URL |
|------------|-------------|
| devl | `https://devl.fullstackcashtrack.com` |
| acpt | `https://acpt.fullstackcashtrack.com` |
| prod | `https://prod.fullstackcashtrack.com` |

**Set `baseUrl` in Postman** to the appropriate URL for the environment you're testing.

---

## Networking

- **ALB:** `cpsc-shared-alb` (shared across all backend environments, host-based routing)
- **Security group** `cpsc-backend-sg`: inbound port 8080 from ALB security group only
- **Health check:** `GET /api/hello` — checked every 30s, 2 healthy / 3 unhealthy threshold

---

## CloudWatch Logs

| Log Group | Contents |
|-----------|----------|
| `/ecs/cpsc-backend-api-devl` | devl application logs |
| `/ecs/cpsc-backend-api-acpt` | acpt application logs |
| `/ecs/cpsc-backend-api-prod` | prod application logs |
| `/aws/codebuild/cpsc-backend-build-devl` | devl build logs |
| `/aws/codebuild/cpsc-backend-build-acpt` | acpt build logs |
| `/aws/codebuild/cpsc-backend-build-prod` | prod build logs |

```powershell
# Tail devl logs
aws logs tail /ecs/cpsc-backend-api-devl --follow --profile cpsc-devops
```
