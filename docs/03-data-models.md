# Data Models

All schemas are defined in `src/main/resources/openapi.yaml` and generated into `build/generated/src/main/java/com/cpsc/backend/model/`.

---

## InstitutionResponse

Returned by `POST /api/institutions`, `GET /api/institutions`, and `PATCH /api/institutions/{id}`.

| Field | Type | Description |
|-------|------|-------------|
| `institutionId` | UUID | Unique identifier |
| `institutionName` | string | Name of the institution |
| `startingBalance` | double | Initial balance at creation |
| `currentBalance` | double | Current balance — `startingBalance` + all DEPOSITs − all WITHDRAWALs |
| `allocatedPercent` | integer (0–100) | Percentage of institution balance allocated to goals (default 0) |
| `linkedGoals` | UUID[] | Goal IDs that reference this institution |
| `userId` | UUID | Cognito `sub` of the owning user |
| `createdAt` | long | UNIX timestamp (seconds) of creation |

**Balance calculation:** When `startingBalance` is changed via `PATCH`, `currentBalance` is adjusted by the same delta (preserving the balance history from transactions).

---

## TransactionResponse

Returned by `POST`, `GET`, `PUT` on transaction endpoints.

| Field | Type | Description |
|-------|------|-------------|
| `transactionId` | UUID | Unique identifier |
| `institutionId` | UUID | Owning institution |
| `type` | enum | `DEPOSIT` or `WITHDRAWAL` |
| `amount` | double | Amount > 0 and ≤ 1,000,000,000 |
| `tags` | string[] | Optional categorization tags |
| `description` | string (max 500) | Optional notes |
| `transactionDate` | long | UNIX timestamp of when transaction occurred |
| `createdAt` | long | UNIX timestamp of when record was saved |

**Validation rules:**
- `type` is required
- `amount` must be > 0, ≤ 1,000,000,000, not NaN, not Infinite
- `tags`, `description`, `transactionDate` are all optional
- `transactionDate` defaults to current time if omitted

---

## GoalResponse

Returned by all goal endpoints.

| Field | Type | Description |
|-------|------|-------------|
| `goalId` | UUID | Unique identifier |
| `name` | string (max 100) | Goal name |
| `description` | string (max 500) | Optional description |
| `targetAmount` | double | Amount to save (> 0) |
| `linkedInstitutions` | `{UUID → integer}` | Map of institution IDs to their allocation percentages |
| `isCompleted` | boolean | Auto-calculated: whether allocated amounts ≥ `targetAmount` |
| `isActive` | boolean | `false` after calling `POST /api/goals/{goalId}/complete`. All new goals start as `true`. |
| `linkedTransactions` | UUID[] | Transaction IDs supplied when completing via the complete endpoint. Empty for auto-completed goals. |
| `completedAt` | long | UNIX timestamp set when `isActive` → `false` via the complete endpoint. `null` otherwise. |
| `userId` | UUID | Cognito `sub` of the owning user |
| `createdAt` | long | UNIX timestamp of creation |

### Goal Completion Logic

**Automatic (`isCompleted`)**  
Recalculated whenever a linked institution's balance changes (transaction created/updated/deleted, institution balance edited, or on goal edit):

```
isCompleted = Σ (institutionBalance × allocationPercent ÷ 100) ≥ targetAmount
```

Does not change `isActive` or `completedAt`.

**Manual (`POST /api/goals/{goalId}/complete`)**  
Validates that the supplied `transactionIds` sum to exactly `targetAmount`, then:
- `isActive` → `false`
- `completedAt` → current UNIX timestamp
- `linkedTransactions` → the supplied IDs
- `linkedInstitutions` → cleared

---

## Cascade Behavior

### Deleting an institution
1. All transactions for that institution are deleted
2. Institution is removed from all linked goals' `linkedInstitutions` maps
3. Linked institutions' `allocatedPercent` is reduced by the removed allocation
4. Affected goals' `isCompleted` is recalculated (set to `false` if no institutions remain)

### Deleting a goal
1. Each linked institution's `allocatedPercent` is reduced by the amount allocated to this goal
2. Goal ID is removed from each institution's `linkedGoals` list

### Deleting an account (`DELETE /api/secure/delete-account`)
1. User's goals are deleted (triggers cascade above)
2. User's institutions are deleted (triggers cascade above)  
3. Cognito user account is deleted
4. Order ensures referential consistency. **Irreversible.**

---

## Validation Rules Summary

| Resource | Field | Rule |
|----------|-------|------|
| Institution | `institutionName` | Required, 1–100 chars |
| Institution | `startingBalance` | Required on create |
| Institution | `allocatedPercent` | 0–100, managed by goal operations |
| Transaction | `type` | Required: `DEPOSIT` or `WITHDRAWAL` |
| Transaction | `amount` | Required, > 0, ≤ 1,000,000,000, not NaN/Infinite |
| Goal | `name` | Required on create, 1–100 chars |
| Goal | `description` | Optional, max 500 chars |
| Goal | `targetAmount` | Required on create, > 0 |
| Goal | `linkedInstitutions` | Required on create; each institution must exist and belong to the user |
| Goal allocation | per-institution | Current `allocatedPercent` + new allocation ≤ 100% |
| Auth | `screenName` | Required on signup, 3–50 chars |
| Auth | `password` | Min 8 chars, requires uppercase, lowercase, numbers |

---

## Error Response

All error responses use a consistent shape:

```json
{ "error": "Human-readable error message" }
```

Common HTTP status codes:
- `400` — Validation failure, business rule violation
- `401` — Missing or invalid JWT token
- `404` — Resource not found or doesn't belong to the authenticated user
- `500` — Unexpected server error (check CloudWatch logs)
