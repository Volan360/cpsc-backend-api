# Analytics

Analytics endpoints delegate all computation and report generation to AWS Lambda functions in the `cpsc-analytics-scripts` repository. The ECS backend acts as a proxy — it builds the Lambda event, invokes the function, and maps the response back to OpenAPI models.

For local development, start the local Lambda server before calling these endpoints. See [docs/01-local-development.md](01-local-development.md#local-lambda-server-analytics).

---

## Architecture

```
POST /api/analytics/*
  └─ AnalyticsController
       ├─ Extracts userId from JWT (SecurityContext)
       ├─ Builds Lambda event:
       │     { requestContext: { authorizer: { claims: { sub: userId } } },
       │       body: "<JSON request body>" }
       ├─ LambdaInvokerService.invoke(functionName, event)
       │    ├─ Local:  HTTP POST http://localhost:9001/2015-03-31/functions/{name}/invocations
       │    └─ AWS:    AWS SDK Lambda.invoke() — IAM role, no credentials in code
       └─ Parses Lambda proxy response → maps to OpenAPI model
```

**Lambda functions (in `cpsc-analytics-scripts`):**

| Spring Boot env var | AWS function name | Handler |
|----|----|----|
| `LAMBDA_ANALYTICS_FUNCTION` | `cpsc-analytics-generate-{env}` | `src.lambda_handlers.analytics_handler.lambda_handler` |
| `LAMBDA_REPORT_FUNCTION` | `cpsc-analytics-report-{env}` | `src.lambda_handlers.report_handler.lambda_handler` |

**Local Lambda server:** `cpsc-analytics-scripts/local_lambda_server.py` — pure Python stdlib HTTP server on port 9001 that emulates the Lambda invocation API (`POST /2015-03-31/functions/{name}/invocations`).

---

## `POST /api/analytics/generate`

Invokes `cpsc-analytics-generate-{env}` to compute metrics for the requested type and date range.

### Request

```json
{
  "analyticsType": "cash_flow",
  "dateRange": { "start": "2025-01-01", "end": "2025-12-31" },
  "options": { "groupBy": "month" }
}
```

| Field | Type | Required? | Description |
|-------|------|-----------|-------------|
| `analyticsType` | string | Yes | See types table below |
| `dateRange` | object | See types table | `{ "start": "YYYY-MM-DD", "end": "YYYY-MM-DD" }` |
| `options` | object | No | Optional settings (see below) |

### Analytics Types

| `analyticsType` | `dateRange` | `options` | What it computes |
|---|---|---|---|
| `cash_flow` | Required | `groupBy`: `day`/`week`/`month` (default `month`) | Running totals of deposits vs withdrawals in each time bucket |
| `categories` | Required | — | Spending breakdown by transaction tags: totals, percentages, per-tag transaction lists |
| `goals` | **Not required** (snapshot) | — | Current progress, completion status, at-risk goals, priority ranking, allocation details for all goals |
| `institutions` | Required | — | Per-institution balance trends and transaction volumes |
| `network` | **Not required** (all-time) | — | Relationships between institutions and goals with allocation percentages |
| `health` | Required | `includeRecommendations`: boolean (default `true`) | Composite health score (0–100) across 5 dimensions |

### Response

```json
{
  "analyticsType": "cash_flow",
  "userId": "cognito-sub-here",
  "generatedAt": "2026-02-19T10:30:00Z",
  "dateRange": { "start": "2025-01-01", "end": "2025-12-31" },
  "data": { ... }
}
```

`dateRange` is omitted from the response for `goals` and `network` (snapshot/all-time types).  
`data` structure varies by `analyticsType` — see the Lambda source for field-level schema.

---

## `POST /api/analytics/report`

Invokes `cpsc-analytics-report-{env}` to generate a styled HTML report, uploads it to `cpsc-analytics-{env}` S3 bucket, and returns a presigned URL valid for 30 days.

### Request

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

### Report Types

| `reportType` | `dateRange` | What's included |
|---|---|---|
| `cash_flow` | Required | Monthly/weekly income vs expense chart |
| `category` | Required | Tag-based spending breakdown with percentages |
| `goal` | **Not required** (snapshot) | Progress bars, completion status, and projections for all goals |
| `health_score` | Required | Score gauges, component breakdown, and recommendations |
| `network` | **Not required** (all-time) | Institution-goal relationship diagram with allocation percentages |
| `comprehensive` | Required | All of the above in a single document |

### Response

```json
{
  "reportType": "cash_flow",
  "userId": "cognito-sub-here",
  "generatedAt": "2026-02-19T10:30:00Z",
  "dateRange": { "start": "2025-01-01", "end": "2025-12-31" },
  "reportUrl": "https://s3.amazonaws.com/cpsc-analytics-devl/reports/user/2025/01/01/cash_flow_report_100000.html?X-Amz-...",
  "s3Key": "reports/user/2025/01/01/cash_flow_report_100000.html",
  "bucket": "cpsc-analytics-devl"
}
```

The presigned URL is valid for **30 days**. After expiry, the report still exists in S3 but requires a new presigned URL to access.

---

## `GET /api/analytics/health-score`

A convenience endpoint. Internally calls `POST /api/analytics/generate` with `analyticsType: health` and maps the structured score fields from `data` directly into a `HealthScoreResponse`.

### Query Parameters

| Param | Default | Description |
|-------|---------|-------------|
| `startDate` | 30 days ago | Start of analysis period (`YYYY-MM-DD`) |
| `endDate` | today | End of analysis period (`YYYY-MM-DD`) |

**Example:** `GET /api/analytics/health-score?startDate=2025-01-01&endDate=2025-12-31`

### Response

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

### Health Score Ratings

| Rating | Score |
|--------|-------|
| Excellent | 90–100 |
| Good | 75–89 |
| Fair | 60–74 |
| Poor | 45–59 |
| Needs Improvement | 0–44 |

---

## Lambda Runtime Dependencies

The Lambda deployment package (built from `requirements-lambda.txt`):

| Package | Purpose |
|---------|---------|
| numpy | Required by plotly.express at import time |
| plotly | Chart generation |
| networkx | Network/graph analysis |
| jinja2 | HTML report templating |

`boto3` is excluded — provided by the Lambda Python 3.12 runtime.

---

## Postman Collection

The collection includes requests for all analytics types:
- `Generate Analytics - Cash Flow` / `Cash Flow (Weekly)`
- `Generate Analytics - Categories`
- `Generate Analytics - Goals`
- `Generate Analytics - Institutions`
- `Generate Analytics - Network`
- `Generate Analytics - Health`
- `Get Health Score (Last 30 Days)` / `(Custom Range)`
- `Generate Report - Cash Flow / Category / Goal / Health Score / Network / Comprehensive`

All use `Bearer {{idToken}}` automatically set by the Login request.
