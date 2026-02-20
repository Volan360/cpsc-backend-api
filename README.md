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
- **Analytics (Lambda-backed)**: Financial analytics and HTML report generation via AWS Lambda — cash flow, category breakdown, goal progress, institution analysis, network analysis, and composite health scoring
- **Postman Collection**: Pre-configured API testing collection with automatic token management
- **Password Reset**: Forgot password flow with email verification codes
- **Account Management**: Update screen name, delete account with cascade deletion
- **Comprehensive Testing**: 296 tests with full coverage of all endpoints and business logic

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

- `LAMBDA_ANALYTICS_FUNCTION`: Name of the analytics Lambda function (default: `cpsc-analytics-generate-devl`)
  - **devl**: `cpsc-analytics-generate-devl`
  - **acpt**: `cpsc-analytics-generate-acpt`
  - **prod**: `cpsc-analytics-generate-prod`

- `LAMBDA_REPORT_FUNCTION`: Name of the report Lambda function (default: `cpsc-analytics-report-devl`)
  - **devl**: `cpsc-analytics-report-devl`
  - **acpt**: `cpsc-analytics-report-acpt`
  - **prod**: `cpsc-analytics-report-prod`

- `LAMBDA_ENDPOINT_URL`: Lambda endpoint URL override (default: empty — uses real AWS Lambda)
  - **Local development**: `http://localhost:9001` (points to the local Lambda server in `cpsc-analytics-scripts`)
  - **AWS**: Leave empty

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
$env:LAMBDA_ENDPOINT_URL = "http://localhost:9001"  # Points to local Lambda server
```

The easiest way to run locally is with the provided scripts, which handle environment variable loading automatically:

**Terminal 1 — Analytics Lambda server** (required for analytics endpoints):
```powershell
cd cpsc-analytics-scripts
.\run-local.ps1
```

**Terminal 2 — Spring Boot API**:
```powershell
cd cpsc-backend-api
.\run-local.ps1
```

`run-local.ps1` in the backend repo reads `.env.local`, auto-sets `LAMBDA_ENDPOINT_URL=http://localhost:9001` if not already configured, and starts the application. See `.env.local` to customise values.

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
- **Password reset flow**: Forgot password → Confirm forgot password
- **User management**: Update screen name, delete account
- **Institution management**: Create, get, edit, delete institutions
- **Transaction management**: Create, get, update, delete transactions
- **Goal management**: Create goals with institution allocations, get all goals
- **Analytics**: Generate analytics data (cash flow, categories, goals, institutions, network, health), health score, and HTML reports
- **Protected endpoint examples**: All endpoints use proper Bearer token authentication

**Environment Variable Setup**:
Set `baseUrl` in your Postman environment:
- Local: `http://localhost:8080`

## API Endpoints

### Authentication

#### Public Endpoints (No Authentication Required)
- `POST /api/auth/signup` - Register new user with email, password, and screen name
- `POST /api/auth/confirm` - Confirm email with verification code
- `POST /api/auth/resend-code` - Resend verification code
- `POST /api/auth/forgot-password` - Initiate password reset (sends verification code via email)
- `POST /api/auth/confirm-forgot-password` - Complete password reset with verification code and new password
- `POST /api/auth/login` - Login and receive JWT tokens (idToken, accessToken, refreshToken)

#### Protected Endpoints (Require Authentication)
- `GET /api/secure/profile` - Get authenticated user's profile (email, screenName) **[Requires Access Token]**
- `PATCH /api/secure/update-screen-name` - Update user's screen name (display name) **[Requires Access Token]**
- `DELETE /api/secure/delete-account` - Permanently delete user account and all associated data **[Requires Access Token]**

**Token Usage**:
- **ID Token** (`idToken`): Use for most protected endpoints (Institutions, Transactions, Goals). Contains user identity and is validated by the JWT filter.
- **Access Token** (`accessToken`): Required for `/api/secure/profile`, `/api/secure/update-screen-name`, and `/api/secure/delete-account` endpoints, which interact with AWS Cognito's user management APIs.
- **Refresh Token** (`refreshToken`): Used to obtain new tokens when access/ID tokens expire (not implemented yet).

**Password Reset Flow**:
1. User requests password reset via `POST /api/auth/forgot-password` with email
2. Cognito sends verification code to user's email
3. User submits code and new password via `POST /api/auth/confirm-forgot-password`
4. User can now login with new password

**Account Deletion Flow**:
1. User authenticates and calls `DELETE /api/secure/delete-account`
2. System deletes all user's goals (updates linked institutions)
3. System deletes all user's institutions (cascades to transactions)
4. System deletes Cognito user account
5. All data is permanently removed (irreversible)

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

### Analytics (Protected - Requires ID Token)

Analytics endpoints delegate computation to AWS Lambda functions in `cpsc-analytics-scripts`. For local development, start the Lambda server (`cpsc-analytics-scripts/run-local.ps1`) before calling these endpoints.

#### `POST /api/analytics/generate` — Generate Analytics Data

Invokes the analytics Lambda to compute metrics for the specified type and date range.

**Request body**:
```json
{
  "analyticsType": "cash_flow",
  "dateRange": { "start": "2025-01-01", "end": "2025-12-31" },
  "options": { "groupBy": "month" }
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `analyticsType` | string | Yes | One of: `cash_flow`, `categories`, `goals`, `institutions`, `network`, `health` |
| `dateRange` | object | Yes (except `goals`) | `start` and `end` dates in `YYYY-MM-DD` format |
| `options` | object | No | Optional settings — see per-type details below |

**Analytics types**:

| Type | `dateRange` | `options` | What it computes |
|------|-------------|-----------|-----------------|
| `cash_flow` | Required | `groupBy`: `day`/`week`/`month` (default `month`) | Running totals of deposits vs withdrawals in each time bucket |
| `categories` | Required | — | Spending breakdown by transaction tags with totals and percentages |
| `goals` | **Not required** (snapshot) | — | Current progress, completion status, at-risk goals, priority ranking, and allocation details for all goals |
| `institutions` | Required | — | Per-institution balance trends and transaction volumes |
| `network` | Required | — | Relationships between institutions and goals with allocation percentages |
| `health` | Required | `includeRecommendations`: boolean (default `true`) | Composite health score (0–100) across 5 dimensions — see `/health-score` for the dedicated endpoint |

**Response**:
```json
{
  "analyticsType": "cash_flow",
  "userId": "cognito-sub-here",
  "generatedAt": "2026-02-19T10:30:00Z",
  "dateRange": { "start": "2025-01-01", "end": "2025-12-31" },
  "data": { ... }
}
```
`data` structure varies by `analyticsType`. `dateRange` is omitted from the response when not applicable (e.g. `goals`).

---

#### `POST /api/analytics/report` — Generate HTML Report

Invokes the report Lambda to generate a styled HTML report, uploads it to S3, and returns a presigned URL valid for 30 days.

**Request body**:
```json
{
  "reportType": "comprehensive",
  "dateRange": { "start": "2025-01-01", "end": "2025-12-31" },
  "options": {
    "groupBy": "month",
    "includeRecommendations": true,
    "userName": "John Doe"
  }
}
```

| `reportType` | What's included |
|--------------|----------------|
| `cash_flow` | Monthly/weekly income vs expense chart |
| `category` | Tag-based spending breakdown with percentages |
| `goal` | Progress bars, completion status, and projections for all goals |
| `health_score` | Score gauges, component breakdown, and recommendations |
| `network` | Institution-goal relationship diagram with allocation percentages |
| `comprehensive` | All of the above in a single document |

**Response**:
```json
{
  "reportType": "cash_flow",
  "userId": "cognito-sub-here",
  "generatedAt": "2026-02-19T10:30:00Z",
  "dateRange": { "start": "2025-01-01", "end": "2025-12-31" },
  "reportUrl": "https://s3.amazonaws.com/cpsc-analytics-devl/reports/...",
  "s3Key": "reports/user/2025/01/01/cash_flow_report_100000.html",
  "bucket": "cpsc-analytics-devl"
}
```

---

#### `GET /api/analytics/health-score` — Financial Health Score

Returns the composite financial health score (0–100) for the authenticated user. Shortcut for `POST /api/analytics/generate` with `analyticsType: health` that extracts and maps the structured score fields directly.

**Query parameters** (both optional — default to last 30 days):
- `startDate` — `YYYY-MM-DD`
- `endDate` — `YYYY-MM-DD`

**Example**: `GET /api/analytics/health-score?startDate=2025-01-01&endDate=2025-12-31`

**Response**:
```json
{
  "overallScore": 78.5,
  "rating": "Good",
  "components": {
    "savings_rate": 82.0,
    "goal_progress": 65.0,
    "spending_diversity": 88.0,
    "account_utilization": 74.0,
    "transaction_regularity": 83.5
  },
  "recommendations": [
    "Increase allocation to your Emergency Fund goal",
    "Consider diversifying spending across more categories"
  ],
  "periodDays": 365,
  "computedAt": "2026-02-19T10:30:00Z",
  "userId": "cognito-sub-here"
}
```

| Rating | Score range |
|--------|------------|
| Excellent | 90–100 |
| Good | 75–89 |
| Fair | 60–74 |
| Poor | 45–59 |
| Needs Improvement | 0–44 |

---

#### Analytics Architecture

```
POST /api/analytics/*
  └─ AnalyticsController
       ├─ Builds Lambda event: { requestContext.authorizer.claims.sub, body: "{...}" }
       ├─ LambdaInvokerService.invoke(functionName, event)
       │    ├─ Local:  HTTP POST http://localhost:9001/2015-03-31/functions/{name}/invocations
       │    └─ AWS:    SDK invocation of cpsc-analytics-generate-devl / cpsc-analytics-report-devl
       └─ Parses Lambda proxy response → maps to OpenAPI model
```

**Lambda functions** (in `cpsc-analytics-scripts`):
- `cpsc-analytics-generate-devl` → `analytics_handler.handler`
- `cpsc-analytics-report-devl` → `report_handler.handler`

**Local Lambda server**: `cpsc-analytics-scripts/local_lambda_server.py` — pure Python stdlib HTTP server on port 9001, no extra dependencies required.

### Public Endpoints
- `GET /api/hello` - Health check endpoint

## Test Coverage

The project maintains6comprehensive test coverage with unit tests for all layers:

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