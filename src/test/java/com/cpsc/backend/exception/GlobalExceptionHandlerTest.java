package com.cpsc.backend.exception;

import com.cpsc.backend.model.ErrorResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler exceptionHandler;

    @BeforeEach
    void setUp() {
        exceptionHandler = new GlobalExceptionHandler();
    }

    @Test
    void handleInstitutionNotFound_ReturnsNotFound() {
        // Arrange
        InstitutionNotFoundException exception = new InstitutionNotFoundException("Institution not found");

        // Act
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleInstitutionNotFound(exception);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getError()).isEqualTo("Institution not found");
    }

    @Test
    void handleInvalidInstitutionData_ReturnsBadRequest() {
        // Arrange
        InvalidInstitutionDataException exception = new InvalidInstitutionDataException("Invalid data");

        // Act
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleInvalidInstitutionData(exception);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getError()).isEqualTo("Invalid data");
    }

    @Test
    void handleDynamoDbException_ReturnsInternalServerError() {
        // Arrange
        DynamoDbException exception = (DynamoDbException) DynamoDbException.builder().message("DynamoDB error").build();

        // Act
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleDynamoDbException(exception);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getError()).contains("Database error");
    }

    @Test
    void handleGenericException_ReturnsInternalServerError() {
        // Arrange
        Exception exception = new Exception("Unexpected error");

        // Act
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleGenericException(exception);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getError()).contains("An unexpected error occurred");
    }

    @Test
    void handleRuntimeException_ReturnsInternalServerError() {
        // Arrange
        RuntimeException exception = new RuntimeException("Runtime error");

        // Act
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleGenericException(exception);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
    }
}
