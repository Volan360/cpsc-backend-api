# Local Development

## Prerequisites

- **Java 24** or higher (Java 24 currently installed and fully supported)
- No global Gradle installation needed — use the included Gradle wrapper

## Quick Start

```powershell
# Terminal 1 — Analytics Lambda server (required for analytics endpoints)
cd cpsc-analytics-scripts
.\run-local.ps1

# Terminal 2 — Spring Boot API
cd cpsc-backend-api
.\run-local.ps1
```

`run-local.ps1` reads `.env.local`, automatically sets `LAMBDA_ENDPOINT_URL=http://localhost:9001` if not already configured, and starts the application with `.\gradlew.bat bootRun`.

Once running:
- **API base:** `http://localhost:8080`
- **Swagger UI:** `http://localhost:8080/swagger-ui.html`
- **OpenAPI spec:** `http://localhost:8080/api-docs`
- **Health check:** `http://localhost:8080/api/hello`

## Manual Start (without run-local.ps1)

Set environment variables, then run Gradle:

```powershell
$env:AWS_SECRET_NAME            = "cpsc-backend/cognito-devl"
$env:DYNAMODB_TABLE_NAME        = "Institutions-devl"
$env:DYNAMODB_TRANSACTION_TABLE_NAME = "Transactions-devl"
$env:DYNAMODB_GOALS_TABLE_NAME  = "Goals-devl"
$env:LAMBDA_ANALYTICS_FUNCTION  = "cpsc-analytics-generate-devl"
$env:LAMBDA_REPORT_FUNCTION     = "cpsc-analytics-report-devl"
$env:LAMBDA_ENDPOINT_URL        = "http://localhost:9001"
$env:AWS_REGION                 = "us-east-1"

.\gradlew.bat bootRun
```

## Environment Variables

All variables have sensible defaults for local development against the `devl` environment:

| Variable | Default | Description |
|----------|---------|-------------|
| `AWS_SECRET_NAME` | `cpsc-backend/cognito-devl` | Secrets Manager secret containing Cognito config |
| `AWS_REGION` | `us-east-1` | AWS region |
| `DYNAMODB_TABLE_NAME` | `Institutions-devl` | DynamoDB institutions table |
| `DYNAMODB_TRANSACTION_TABLE_NAME` | `Transactions-devl` | DynamoDB transactions table |
| `DYNAMODB_GOALS_TABLE_NAME` | `Goals-devl` | DynamoDB goals table |
| `LAMBDA_ANALYTICS_FUNCTION` | `cpsc-analytics-generate-devl` | Analytics Lambda function name |
| `LAMBDA_REPORT_FUNCTION` | `cpsc-analytics-report-devl` | Report Lambda function name |
| `LAMBDA_ENDPOINT_URL` | *(empty — uses AWS SDK)* | Override to `http://localhost:9001` for local Lambda server |

## AWS Credentials

Local development requires AWS credentials with access to:
- **Secrets Manager** — to read Cognito configuration from `cpsc-backend/cognito-devl`
- **DynamoDB** — to read/write `Institutions-devl`, `Transactions-devl`, `Goals-devl`
- **Cognito** — for user authentication flows
- **Lambda** — to invoke `cpsc-analytics-generate-devl` and `cpsc-analytics-report-devl` (unless using local Lambda server)

Configure your credentials using the `cpsc-devops` profile:
```powershell
aws configure --profile cpsc-devops
```

## Local Lambda Server (Analytics)

For analytics endpoints without invoking real AWS Lambda:

```powershell
cd cpsc-analytics-scripts
.\run-local.ps1
```

This starts a pure Python stdlib HTTP server on port 9001 that simulates the Lambda invocation API. No extra dependencies required beyond the analytics project's existing venv.

The Spring Boot app automatically routes analytics calls to `http://localhost:9001` when `LAMBDA_ENDPOINT_URL` is set (handled by `run-local.ps1` automatically).

## Docker Compose (Local)

A `docker-compose.yml` is included for running the API in a container locally:

```powershell
docker-compose up
```

This builds the Docker image locally and runs it on port 8080. AWS credentials and environment variables are passed from the host via the compose file.

## CORS Configuration

The API allows cross-origin requests from these origins:
- `http://localhost:4200` (Angular dev server)
- `http://localhost:3000` (alternative local port)
- `http://localhost` (Docker frontend)
- `https://app-devl.fullstackcashtrack.com`
- `https://app-acpt.fullstackcashtrack.com`
- `https://www.fullstackcashtrack.com`
- `https://fullstackcashtrack.com`

## Troubleshooting

### "Cannot load credentials from Secrets Manager"
- Verify AWS credentials are configured: `aws sts get-caller-identity --profile cpsc-devops`
- Ensure the secret `cpsc-backend/cognito-devl` exists in `us-east-1`
- Check `AWS_SECRET_NAME` and `AWS_REGION` env vars are set correctly

### "User is not confirmed" on login
- User must confirm email first using the verification code sent at signup
- Use `POST /api/auth/confirm` or the Postman "Confirm Sign Up" request

### Build errors after updating openapi.yaml
- Check YAML syntax (indentation-sensitive)
- Validate at [Swagger Editor](https://editor.swagger.io/)
- Run `.\gradlew.bat clean openApiGenerate`

### Analytics endpoint returns 500
- Ensure the local Lambda server is running: `cd cpsc-analytics-scripts; .\run-local.ps1`
- Verify `LAMBDA_ENDPOINT_URL=http://localhost:9001` is set
- Check Lambda server logs for Python errors
