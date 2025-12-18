# CPSC Backend API

A Spring Boot REST API built with Gradle.

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

## Testing the API

Once the application is running (default port: 8080), you can test the endpoint:

```bash
curl http://localhost:8080/api/hello
```

Or open in your browser:
```
http://localhost:8080/api/hello
```

Expected response:
```json
{
  "message": "Hello from CPSC Backend API!",
  "status": "success"
}
```

## Building the Application

```bash
.\gradlew.bat build
```

## Project Structure

```
cpsc-backend-api/
├── src/
│   └── main/
│       ├── java/
│       │   └── com/cpsc/backend/
│       │       ├── CpscBackendApiApplication.java
│       │       └── controller/
│       │           └── TestController.java
│       └── resources/
│           └── application.properties
├── build.gradle
└── settings.gradle
```
