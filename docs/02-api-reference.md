# API Reference

**Base URL (local):** `http://localhost:8080`  
**Interactive docs:** `http://localhost:8080/swagger-ui.html`  
**OpenAPI spec:** `http://localhost:8080/api-docs`

## Authentication

All protected endpoints require a JWT token in the `Authorization` header:
```
Authorization: Bearer <token>
```

**Which token to use:**
- **ID Token** (`idToken`) — use for all Institutions, Transactions, Goals, and Analytics endpoints
- **Access Token** (`accessToken`) — required for `/api/secure/*` endpoints that call Cognito user management APIs

Tokens are obtained from `POST /api/auth/login`. The Postman collection saves them automatically.

---

## Public Endpoints

### `GET /api/hello`
Health check. No authentication required.

**Response 200:**
```json
{ "message": "Hello from CPSC Backend API!", "status": "success" }
```

---

## Authentication Endpoints

### `POST /api/auth/signup`
Register a new user. Sends a verification code to the provided email.

**Request:**
```json
{ "email": "user@example.com", "password": "Password1!", "screenName": "JohnDoe" }
```
**Response 201:** `{ "message": "...", "email": "user@example.com" }`  
**Response 400:** User already exists or invalid input

---

### `POST /api/auth/confirm`
Confirm email with the verification code.

**Request:**
```json
{ "email": "user@example.com", "confirmationCode": "123456" }
```
**Response 200:** `{ "message": "User confirmed successfully" }`  
**Response 400:** Invalid/expired code

---

### `POST /api/auth/resend-code`
Resend the email verification code.

**Request:** `{ "email": "user@example.com" }`  
**Response 200:** `{ "message": "Verification code resent" }`

---

### `POST /api/auth/forgot-password`
Initiate password reset — sends a code to the user's email.

**Request:** `{ "email": "user@example.com" }`  
**Response 200:** `{ "message": "Password reset code sent" }`

---

### `POST /api/auth/confirm-forgot-password`
Complete password reset using the code from email.

**Request:**
```json
{ "email": "user@example.com", "confirmationCode": "123456", "newPassword": "NewPass1!" }
```
**Response 200:** `{ "message": "Password reset successfully" }`  
**Response 400:** Invalid/expired code or password doesn't meet requirements

---

### `POST /api/auth/login`
Authenticate and receive JWT tokens. User must be confirmed first.

**Request:** `{ "email": "user@example.com", "password": "Password1!" }`

**Response 200:**
```json
{
  "idToken": "eyJ...",
  "accessToken": "eyJ...",
  "refreshToken": "eyJ...",
  "expiresIn": 3600
}
```
**Response 401:** Wrong credentials or unconfirmed account

---

## Protected Endpoints — Account Management

These endpoints use the **Access Token** (not ID Token).

### `GET /api/secure/profile` 🔐
Get authenticated user's profile.

**Response 200:**
```json
{ "email": "user@example.com", "screenName": "JohnDoe", "authenticated": true }
```

---

### `PATCH /api/secure/update-screen-name` 🔐
Update the authenticated user's display name.

**Request:** `{ "screenName": "NewName123" }`  
**Response 200:** `{ "message": "Screen name updated", "screenName": "NewName123" }`

---

### `DELETE /api/secure/delete-account` 🔐
Permanently delete the account and all associated data (goals → institutions → transactions → Cognito user). **Irreversible.**

**Response 204:** No content (success)

---

## Institutions (ID Token required)

### `POST /api/institutions` 🔐
Create a new financial institution.

**Request:** `{ "institutionName": "Chase Checking", "startingBalance": 5000.00 }`  
**Response 201:** [`InstitutionResponse`](03-data-models.md#institutionresponse)

---

### `GET /api/institutions` 🔐
Get all institutions for the authenticated user (paginated).

**Query params:**
- `limit` — max results per page (1–100, default 50)
- `lastEvaluatedKey` — pagination token from previous response

**Response 200:**
```json
{
  "institutions": [ ...InstitutionResponse... ],
  "nextToken": "eyJ..." 
}
```
`nextToken` is `null` when no more pages remain.

---

### `PATCH /api/institutions/{institutionId}` 🔐
Edit an institution's name or starting balance. When `startingBalance` changes, `currentBalance` adjusts by the same delta.

**Request (all fields optional):** `{ "institutionName": "Chase Savings", "startingBalance": 6000.00 }`  
**Response 200:** [`InstitutionResponse`](03-data-models.md#institutionresponse)  
**Response 404:** Institution not found or doesn't belong to user

---

### `DELETE /api/institutions/{institutionId}` 🔐
Delete an institution and all its transactions. Also removes it from any linked goals (recalculates goal completion).

**Response 204:** No content  
**Response 404:** Institution not found

---

## Transactions (ID Token required)

### `POST /api/institutions/{institutionId}/transactions` 🔐
Create a deposit or withdrawal on an institution.

**Request:**
```json
{
  "type": "DEPOSIT",
  "amount": 1000.50,
  "tags": ["salary", "monthly"],
  "description": "January salary deposit",
  "transactionDate": 1735041600
}
```
- `type`: `"DEPOSIT"` or `"WITHDRAWAL"` (required)
- `amount`: must be > 0 and ≤ 1,000,000,000 (required)
- `tags`, `description`, `transactionDate`: optional; `transactionDate` defaults to current time

**Response 201:** [`TransactionResponse`](03-data-models.md#transactionresponse)  
**Response 404:** Institution not found

---

### `GET /api/institutions/{institutionId}/transactions` 🔐
Get all transactions for an institution, sorted newest first.

**Response 200:** Array of [`TransactionResponse`](03-data-models.md#transactionresponse)

---

### `PUT /api/institutions/{institutionId}/transactions/{transactionId}` 🔐
Replace a transaction. Recalculates institution `currentBalance` and any linked goal completion.

**Request:** Same fields as create (all optional except they replace the existing values)  
**Response 200:** [`TransactionResponse`](03-data-models.md#transactionresponse)

---

### `DELETE /api/institutions/{institutionId}/transactions/{transactionId}` 🔐
Delete a transaction. Recalculates institution `currentBalance` and any linked goal completion.

**Response 204:** No content

---

## Goals (ID Token required)

### `POST /api/goals` 🔐
Create a financial goal linked to one or more institutions.

**Request:**
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
- `linkedInstitutions`: map of `institutionId → allocationPercent` (0–100)
- Validates that each institution exists, belongs to the user, and has remaining allocation capacity

**Response 201:** [`GoalResponse`](03-data-models.md#goalresponse)

---

### `GET /api/goals` 🔐
Get all goals for the authenticated user.

**Response 200:** `{ "goals": [ ...GoalResponse... ] }`

---

### `PATCH /api/goals/{goalId}` 🔐
Edit a goal. Any combination of fields can be updated. Recalculates `isCompleted` based on new state.

**Request (all optional):**
```json
{
  "name": "Updated Name",
  "description": "Updated description",
  "targetAmount": 15000.00,
  "linkedInstitutions": { "institution-uuid": 60 }
}
```
**Response 200:** [`GoalResponse`](03-data-models.md#goalresponse)

---

### `DELETE /api/goals/{goalId}` 🔐
Delete a goal. Removes the goal from all linked institutions' `linkedGoals` lists and reduces their `allocatedPercent` by the amount allocated to this goal.

**Response 204:** No content

---

### `POST /api/goals/{goalId}/complete` 🔐
Manually complete a goal by supplying specific transaction IDs whose amounts sum to exactly the goal's `targetAmount`. 

Clears `linkedInstitutions`, sets `isActive` to `false`, records `completedAt` timestamp, and stores the provided transaction IDs in `linkedTransactions`.

**Request:**
```json
{ "transactionIds": ["uuid-1", "uuid-2"] }
```
**Response 200:** [`GoalResponse`](03-data-models.md#goalresponse) with `isActive: false` and `completedAt` set  
**Response 400:** Transaction amounts do not sum to `targetAmount`  
**Response 404:** Goal or one/more transactions not found

---

## Analytics (ID Token required)

See [docs/04-analytics.md](04-analytics.md) for full details on request/response shapes and Lambda architecture.

### `POST /api/analytics/generate` 🔐
Invoke analytics Lambda to compute metrics for a given type and date range.

**Types:** `cash_flow`, `categories`, `goals`, `institutions`, `network`, `health`

---

### `POST /api/analytics/report` 🔐
Generate a styled HTML report, upload to S3, return a presigned URL (valid 30 days).

**Report types:** `cash_flow`, `category`, `goal`, `health_score`, `network`, `comprehensive`

---

### `GET /api/analytics/health-score` 🔐
Shortcut for `generateAnalytics` with `analyticsType: health`. Returns a structured score object.

**Query params:** `startDate`, `endDate` (both optional, default to last 30 days)
