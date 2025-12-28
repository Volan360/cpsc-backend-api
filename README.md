# CPSC Backend API

A Spring Boot REST API with AWS Cognito authentication, built with Gradle and OpenAPI code generation.

## Prerequisites

- Java 17 or higher
- No need to install Gradle - the project includes the Gradle wrapper

## Running the Application

### Windows:
```bash
.\gradlew.bat bootRun
```

### Linux/Mac:
```bash
./gradlew bootRun
```

## Features

- **AWS Cognito Authentication**: Email-based user authentication with JWT tokens
- **Email Verification**: Required email confirmation before login
- **Secure Endpoints**: JWT-protected API routes
- **OpenAPI Code Generation**: API-first development with OpenAPI 3.0 specification
- **AWS Secrets Manager**: Secure credential storage for Cognito configuration
- **DynamoDB Integration**: Financial institution and transaction management with environment-specific tables
- **Transaction Management**: Create and track deposits/withdrawals with tags and descriptions
- **Postman Collection**: Pre-configured API testing collection
- **Comprehensive Testing**: 206 tests with 90% instruction coverage and 85% branch coverage

## Environment Configuration

The application requires AWS credentials and environment-specific configuration:

### Environment Variables (set in ECS task definitions)

- `AWS_SECRET_NAME`: Name of the Cognito configuration secret in AWS Secrets Manager
  - **devl**: `cpsc-backend/cognito-devl`
  - **acpt**: `cpsc-backend/cognito-acpt`
  - **prod**: `cpsc-backend/cognito-prod`

- `DYNAMODB_TABLE_NAME`: Name of the DynamoDB institutions table
  - **devl**: `Institutions-devl`
  - **acpt**: `Institutions-acpt`
  - **prod**: `Institutions-prod`

- `DYNAMODB_TRANSACTION_TABLE_NAME`: Name of the DynamoDB transactions table
  - **devl**: `Transactions-devl`
  - **acpt**: `Transactions-acpt`
  - **prod**: `Transactions-prod`

- `AWS_REGION`: AWS region (default: `us-east-1`)

### Local Development

For local testing, you need AWS credentials with access to:
- AWS Secrets Manager (to read Cognito configuration)
- DynamoDB (to access institution tables)
- AWS Cognito (via application authentication flow)

Configure AWS CLI with the DevOps role before running locally:
```bash
aws configure
```

Or set environment variables directly:
```bash
$env:AWS_SECRET_NAME = "cpsc-backend/cognito-devl"
$env:DYNAMODB_TABLE_NAME = "Institutions-devl"
$env:DYNAMODB_TRANSACTION_TABLE_NAME = "Transactions-devl"
$env:AWS_REGION = "us-east-1"
```

## Deployment

The application is deployed to AWS ECS Fargate using CodePipeline:

1. **Build**: CodeBuild creates Docker image from Dockerfile
2. **Push**: Image pushed to ECR repository
3. **Deploy**: ECS service updated with new task definition

Each environment (devl, acpt, prod) has:
- Isolated Cognito user pool
- Isolated DynamoDB tables (Institutions and Transactions)
- Environment-specific secrets
- Separate ECS service and task definition

### Deploying Code Changes

1. Build and test locally:
   ```bash
   .\gradlew.bat clean build
   ```

2. Commit and push to GitHub:
   ```bash
   git add .
   git commit -m "Your commit message"
   git push origin main
   ```

3. CodePipeline automatically:
   - Triggers on push to `main` branch
   - Runs tests via CodeBuild
   - Builds Docker image
   - Deploys to devl environment
   - (Manual approval gates for acpt and prod)

### Manual Task Definition Updates

If you update task definitions (e.g., new environment variables):

1. Register new task definition:
   ```powershell
   aws ecs register-task-definition --cli-input-json file://cpsc-cicd-pipelines/backend/ecs/task-definition-devl.json
   ```

2. Update service to use new revision:
   ```powershell
   aws ecs update-service --cluster cpsc-ecs-cluster-devl --service cpsc-backend-service-devl --task-definition cpsc-backend-task-devl --force-new-deployment
   ```

3. Repeat for acpt and prod environments as needed.


### OpenAPI Code Generation

The project uses OpenAPI Generator to create API interfaces from the specification:

```bash
.\gradlew.bat openApiGenerate
```

This generates:
- API interfaces in `build/generated/src/main/java/com/cpsc/backend/api/`
- Model classes in `build/generated/src/main/java/com/cpsc/backend/model/`

Controllers implement these generated interfaces for type safety.

### Modifying the API

1. Update `src/main/resources/openapi.yaml`
2. Run `.\gradlew.bat openApiGenerate`
3. Implement new methods in controllers
4. Build and test

## Security

- **Credentials**: Stored in AWS Secrets Manager, never in code
- **Authentication**: JWT tokens from AWS Cognito
- **Session**: Stateless (no server-side sessions)
- **Endpoints**: Public auth routes, protected resource routes
- **Email Verification**: Required before login


## Troubleshooting

### "Cannot load credentials from Secrets Manager"
- Verify IAM role has Secrets Manager permissions
- Check secret name is `cpsc/cognito`
- Ensure secret exists in the correct region

### "User is not confirmed" on login
- User must confirm email first using verification code
- See [EMAIL_VERIFICATION.md](EMAIL_VERIFICATION.md)

### Build errors after updating openapi.yaml
- Check YAML syntax (indentation matters)
- Validate schema at [Swagger Editor](https://editor.swagger.io/)
- Run `.\gradlew.bat clean openApiGenerate`POST /api/auth/confirm` - Confirm email with verification code
- `POST /api/auth/resend-code` - Resend verification code
- `POST /api/auth/login` - Login and receive JWT tokens

## Email Verification Flow

Users must verify their email before logging in.

1. **Sign Up** → Receive verification code via email
2. **Confirm** → Enter code to activate account
3. **Login** → Authenticate and receive JWT tokens

## Testing with Postman

Import the collection and environment:
- `CPSC_Backend_API.postman_collection.json` - API requests

The collection includes:
- Automatic JWT token saving after login
- All authentication flow requests
- Protected endpoint examples

## API Endpoints

### Authentication
- `POST /api/auth/signup` - Register new user
- `POST /api/auth/confirm` - Confirm email with verification code
- `POST /api/auth/resend-code` - Resend verification code
- `POST /api/auth/login` - Login and receive JWT tokens

### Institutions (Protected - Requires JWT)
- `POST /api/institutions` - Create new financial institution
- `GET /api/institutions` - Get all user's institutions
- `DELETE /api/institutions/{id}` - Delete an institution

### Transactions (Protected - Requires JWT)
- `POST /api/institutions/{institutionId}/transactions` - Create deposit or withdrawal
- `GET /api/institutions/{institutionId}/transactions` - Get all transactions (sorted newest first)
- `DELETE /api/institutions/{institutionId}/transactions/{transactionId}` - Delete a transaction

### Transaction Request Example
```json
{
  "type": "DEPOSIT",
  "amount": 1000.50,
  "tags": ["salary", "monthly"],
  "description": "January salary deposit"
}
```

**Transaction Types**: `DEPOSIT` or `WITHDRAWAL`

**Validation**:
- Amount must be > 0 and ≤ 1,000,000,000
- Amount cannot be NaN or Infinite
- Type is required
- Tags and description are optional

### Public Endpoints
- `GET /api/hello` - Health check endpoint

## Test Coverage

The project maintains high test coverage with comprehensive unit tests:

- **Total Tests**: 206 (all passing)
- **Instruction Coverage**: 90%
- **Branch Coverage**: 85%

### Running Tests
```bash
.\.gradlew.bat test
```

### Generate Coverage Report
```bash
.\.gradlew.bat jacocoTestReport
```
View report at: `build/reports/jacoco/test/html/index.html`

### Test Structure
- **Repository Tests**: 22 tests (InstitutionRepository, TransactionRepository)
- **Service Tests**: 92 tests (CognitoService, InstitutionService, TransactionService)
- **Controller Tests**: 16 tests (AuthController, InstitutionController, TransactionController, TestController)
- **Security Tests**: 35 tests (JwtValidator)
- **Exception Handler Tests**: 28 tests (GlobalExceptionHandler)