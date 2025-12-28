package com.cpsc.backend.exception;

import com.cpsc.backend.model.ErrorResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

    @Test
    void handleValidationErrors_WithFieldError_ReturnsBadRequest() {
        // Arrange
        MethodArgumentNotValidException exception = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        FieldError fieldError = new FieldError("institution", "name", "must not be blank");
        
        when(exception.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError));

        // Act
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleValidationErrors(exception);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getError()).isEqualTo("name: must not be blank");
    }

    @Test
    void handleValidationErrors_WithMultipleFieldErrors_ReturnsFirstError() {
        // Arrange
        MethodArgumentNotValidException exception = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        FieldError firstError = new FieldError("institution", "name", "must not be blank");
        FieldError secondError = new FieldError("institution", "type", "must not be null");
        
        when(exception.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(firstError, secondError));

        // Act
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleValidationErrors(exception);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getError()).isEqualTo("name: must not be blank");
    }

    @Test
    void handleValidationErrors_WithNoFieldErrors_ReturnsDefaultMessage() {
        // Arrange
        MethodArgumentNotValidException exception = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        
        when(exception.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(Collections.emptyList());

        // Act
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleValidationErrors(exception);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getError()).isEqualTo("Validation failed");
    }

    @Test
    void handleIllegalArgument_ReturnsBadRequest() {
        // Arrange
        IllegalArgumentException exception = new IllegalArgumentException("Invalid argument provided");

        // Act
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleIllegalArgument(exception);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getError()).isEqualTo("Invalid argument provided");
    }

    @Test
    void handleAccessDenied_ReturnsForbidden() {
        // Arrange
        AccessDeniedException exception = new AccessDeniedException("User not authorized");

        // Act
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleAccessDenied(exception);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getError()).isEqualTo("Access denied");
    }
}
