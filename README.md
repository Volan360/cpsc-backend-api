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
- **Postman Collection**: Pre-configured API testing collection


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
