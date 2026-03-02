# CPSC Backend API

REST API for the CPSC Cornerstone financial tracking application.

**Stack:** Spring Boot 3.4.4 · Java 24 · Gradle 8.14 · AWS Cognito · DynamoDB · Lambda · ECS Fargate

---

## Quick Start

```powershell
# Terminal 1 — start local Lambda analytics server
cd cpsc-analytics-scripts
.\run-local.ps1

# Terminal 2 — start the API
cd cpsc-backend-api
.\run-local.ps1
```

API: `http://localhost:8080`  
Swagger UI: `http://localhost:8080/swagger-ui.html`  
OpenAPI JSON: `http://localhost:8080/api-docs`

> See [docs/01-local-development.md](docs/01-local-development.md) for full setup including env vars and AWS credentials.

---

## Documentation

| File | Contents |
|------|----------|
| [docs/01-local-development.md](docs/01-local-development.md) | Prerequisites, env vars, local Lambda, Docker Compose, troubleshooting |
| [docs/02-api-reference.md](docs/02-api-reference.md) | All 20 endpoints — methods, auth, request/response examples |
| [docs/03-data-models.md](docs/03-data-models.md) | Request/response schemas, DynamoDB table structure, validation rules |
| [docs/04-analytics.md](docs/04-analytics.md) | Analytics endpoints, Lambda invocation flow, report types, health score |
| [docs/05-deployment.md](docs/05-deployment.md) | ECS Fargate environments, CodePipeline triggers, env vars per environment |
| [docs/06-development.md](docs/06-development.md) | OpenAPI-first workflow, build commands, testing, security architecture |

---

## API Summary

| Category | Endpoints |
|----------|-----------|
| Public | `GET /api/hello` |
| Authentication | Sign up, confirm, resend code, forgot password, confirm forgot password, login |
| Profile | Get profile, update screen name, delete account |
| Institutions | CRUD operations |
| Transactions | CRUD operations (scoped to an institution) |
| Goals | CRUD + `POST /api/goals/{id}/complete` |
| Analytics | Generate analytics, get health score, generate report |

Authentication uses AWS Cognito JWT tokens. Pass the ID Token in the `Authorization: Bearer <token>` header for protected routes.

---

## Testing with Postman

Import `CPSC_Backend_API.postman_collection.json` into Postman and set the `baseUrl` collection variable:

| Environment | URL |
|-------------|-----|
| Local | `http://localhost:8080` |
| devl | `https://devl.fullstackcashtrack.com` |
| acpt | `https://acpt.fullstackcashtrack.com` |
| prod | `https://prod.fullstackcashtrack.com` |

The Login request automatically saves tokens to environment variables for use in subsequent requests.

---

## Tests

```powershell
.\gradlew.bat test                 # run all 326 tests
.\gradlew.bat jacocoTestReport     # generate coverage report
```

Coverage report: `build/reports/jacoco/test/html/index.html`
