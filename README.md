# CPSC Backend API

A Spring Boot REST API with AWS Cognito authentication, built with Gradle and OpenAPI code generation.

## Prerequisites

- Java 24 or higher (Java 24 currently installed and fully supported)
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
- **Screen Names**: User display names (screen names) stored in Cognito
- **Secure Endpoints**: JWT-protected API routes
- **OpenAPI Code Generation**: API-first development with OpenAPI 3.0 specification
- **AWS Secrets Manager**: Secure credential storage for Cognito configuration
- **DynamoDB Integration**: Financial institution, transaction, and goal management with environment-specific tables
- **Institution Management**: Create, edit, and delete financial institutions with starting/current balances
- **Transaction Management**: Create, update, and delete deposits/withdrawals with tags and descriptions
- **Goal Management**: Create financial goals with institution allocation percentages (validates ownership and allocation limits)
- **Postman Collection**: Pre-configured API testing collection with automatic token management
- **Comprehensive Testing**: 291 tests with full coverage of all endpoints and business logic

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

- `DYNAMODB_GOALS_TABLE_NAME`: Name of the DynamoDB goals table
  - **devl**: `Goals-devl`
  - **acpt**: `Goals-acpt`
  - **prod**: `Goals-prod`

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
$env:DYNAMODB_GOALS_TABLE_NAME = "Goals-devl"
$env:AWS_REGION = "us-east-1"
```

## Deployment

The application is deployed to AWS ECS Fargate using CodePipeline:

1. **Build**: CodeBuild creates Docker image from Dockerfile
2. **Push**: Image pushed to ECR repository
3. **Deploy**: ECS service updated with new task definition

Each environment (devl, acpt, prod) has:
- Isolated Cognito user pool
- Isolated DynamoDB tables (Institutions, Transactions, and Goals)
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

Import the collection:
- `CPSC_Backend_API.postman_collection.json` - Complete API request collection

The collection includes:
- **Automatic JWT token saving**: Login response automatically saves idToken, accessToken, and refreshToken to environment variables
- **Authentication flow**: Sign up → Confirm → Login workflow
- **Institution management**: Create, get, edit, delete institutions
- **Transaction management**: Create, get, update, delete transactions
- **Goal management**: Create goals with institution allocations, get all goals
- **Protected endpoint examples**: All endpoints use proper Bearer token authentication

**Environment Variable Setup**:
Set `baseUrl` in your Postman environment:
- Local: `http://localhost:8080`

## API Endpoints

### Authentication
- `POST /api/auth/signup` - Register new user with email, password, and screen name
- `POST /api/auth/confirm` - Confirm email with verification code
- `POST /api/auth/resend-code` - Resend verification code
- `POST /api/auth/login` - Login and receive JWT tokens (idToken, accessToken, refreshToken)
- `GET /api/secure/profile` - Get authenticated user's profile (email, screenName) **[Requires Access Token]**

**Token Usage**:
- **ID Token** (`idToken`): Use for most protected endpoints (Institutions, Transactions, Goals). Contains user identity and is validated by the JWT filter.
- **Access Token** (`accessToken`): Required ONLY for `/api/secure/profile` endpoint, which calls AWS Cognito's GetUser API.
- **Refresh Token** (`refreshToken`): Used to obtain new tokens when access/ID tokens expire (not implemented yet).

### Institutions (Protected - Requires ID Token)
- `POST /api/institutions` - Create new financial institution with starting balance
- `GET /api/institutions?limit=50&lastEvaluatedKey=...` - Get all user's institutions (paginated)
- `PATCH /api/institutions/{institutionId}` - Edit institution name, starting balance, or allocated percent
- `DELETE /api/institutions/{institutionId}` - Delete an institution

**Institution Fields**:
- `institutionName` (string, required): Name of the institution
- `startingBalance` (number, required): Initial balance
- `currentBalance` (number, auto-calculated): Current balance (adjusted by transactions)
- `allocatedPercent` (integer, 0-100): Percentage allocated to goals (default 0, max 100)

### Transactions (Protected - Requires ID Token)
- `POST /api/institutions/{institutionId}/transactions` - Create deposit or withdrawal
- `GET /api/institutions/{institutionId}/transactions` - Get all transactions (sorted newest first)
- `PUT /api/institutions/{institutionId}/transactions/{transactionId}` - Update a transaction
- `DELETE /api/institutions/{institutionId}/transactions/{transactionId}` - Delete a transaction

**Transaction Request Example**:
```json
{
  "type": "DEPOSIT",
  "amount": 1000.50,
  "tags": ["salary", "monthly"],
  "description": "January salary deposit",
  "transactionDate": 1735041600
}
```

**Transaction Types**: `DEPOSIT` or `WITHDRAWAL`

**Validation**:
- Amount must be > 0 and ≤ 1,000,000,000
- Amount cannot be NaN or Infinite
- Type is required
- Tags, description, and transactionDate are optional
- TransactionDate defaults to current time if not provided

### Goals (Protected - Requires ID Token)
- `POST /api/goals` - Create a new goal with linked institutions
- `GET /api/goals` - Get all user's goals with linked institution allocations
- `PATCH /api/goals/{goalId}` - Edit an existing goal (name, description, targetAmount, linkedInstitutions)
- `DELETE /api/goals/{goalId}` - Delete a goal and update all linked institutions

**Create Goal Request Example**:
```json
{
  "name": "Emergency Fund",
  "description": "Save 6 months of expenses",
  "targetAmount": 10000.00,
  "linkedInstitutions": {
    "550e8400-e29b-41d4-a716-446655440000": 50,
    "550e8400-e29b-41d4-a716-446655440001": 30
  }
}
```
**Goal Fields**:
- `name` (string, required on create): Goal name (max 100 characters)
- `description` (string, optional): Goal description (max 500 characters)
- `targetAmount` (number, required on create): Total amount to save (must be > 0)
- `linkedInstitutions` (map, required on create): Institution IDs mapped to allocation percentages (0-100)
- `isCompleted` (boolean, auto-calculated): Whether the goal has been met
- `linkedGoals` (array, institution field): List of goal IDs linked to each institution

**Goal Completion Calculation**:
- `isCompleted` is automatically calculated when a goal is created or when linked institution balances change
- Calculation: Sum of (institutionBalance × allocationPercent ÷ 100) ≥ targetAmount
- Updates automatically when:
  - Institution balance is edited directly
  - Transactions change institution balance
  - Goal is edited (recalculates based on new linkedInstitutions or targetAmount)

**Goal Validation**:
- Name is required on create (max 100 characters)
- Description is optional (max 500 characters)
- targetAmount is required on create (must be > 0)
- linkedInstitutions is a map of institution IDs to allocation percentages (0-100)
- System validates that:
  - All linked institutions exist and belong to the user
  - Each institution has sufficient unallocated percentage
  - Institution's current allocation + requested allocation ≤ 100%

**Cascade Behavior**:
- Deleting an institution automatically removes it from all linked goals
- Goal completion status is recalculated after institution removal
- If a goal has no remaining linked institutions, `isCompleted` is set to false
- **Deleting a goal** automatically:
  - Reduces each linked institution's `allocatedPercent` by the amount allocated to the goal
  - Removes the goal ID from each linked institution's `linkedGoals` list

### Public Endpoints
- `GET /api/hello` - Health check endpoint

## Test Coverage

The project maintains comprehensive test coverage with unit tests for all layers:

- **Total Tests**: 291 (all passing ✓)
- **Test Execution Time**: ~7 seconds

### Running Tests
```bash
.\gradlew.bat test
```

### Generate Coverage Report
```bash
.\gradlew.bat jacocoTestReport
```
View report at: `build/reports/jacoco/test/html/index.html`

### Test Structure
- **Entity Tests**: Goal, Institution, Transaction entity validation
- **Repository Tests**: GoalRepository, InstitutionRepository, TransactionRepository with DynamoDB mocking
- **Service Tests**: CognitoService, GoalService, InstitutionService, TransactionService with comprehensive business logic validation
- **Controller Tests**: AuthController, GoalController, InstitutionController, TransactionController, TestController
- **Security Tests**: JwtAuthenticationFilter, JWT token validation
- **Exception Handler Tests**: GlobalExceptionHandler with error response mapping

**Key Test Features**:
- Full coverage of CRUD operations
- Business logic validation (allocation limits, ownership checks)
- Error handling and edge cases
- DynamoDB pagination testing
- JWT authentication flow testing