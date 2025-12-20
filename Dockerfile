# Multi-stage Dockerfile for CPSC Backend API
# Stage 1: Build the application
FROM gradle:8.14-jdk24 AS builder

# Set working directory
WORKDIR /app

# Copy Gradle wrapper and build files
COPY gradlew .
COPY gradlew.bat .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .

# Fix line endings and make gradlew executable (Windows CRLF to Unix LF)
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew

# Copy source code
COPY src src

# Build the application (skip tests for faster builds)
RUN ./gradlew clean build -x test --no-daemon

# Stage 2: Create the runtime image
FROM eclipse-temurin:24-jre-alpine

# Set working directory
WORKDIR /app

# Create a non-root user for security
RUN addgroup -g 1001 appuser && \
    adduser -D -u 1001 -G appuser appuser

# Copy the JAR file from builder stage
COPY --from=builder /app/build/libs/*.jar app.jar

# Change ownership to non-root user
RUN chown -R appuser:appuser /app

# Switch to non-root user
USER appuser

# Expose the application port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/api/hello || exit 1

# Set JVM options for containerized environments
ENV JAVA_OPTS="-Xmx512m -Xms256m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
