package com.cpsc.backend.exception;

import com.cpsc.backend.model.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(InstitutionNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleInstitutionNotFound(InstitutionNotFoundException ex) {
        logger.warn("Institution not found: {}", ex.getMessage());
        ErrorResponse error = new ErrorResponse();
        error.setError(ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    @ExceptionHandler(InvalidInstitutionDataException.class)
    public ResponseEntity<ErrorResponse> handleInvalidInstitutionData(InvalidInstitutionDataException ex) {
        logger.warn("Invalid institution data: {}", ex.getMessage());
        ErrorResponse error = new ErrorResponse();
        error.setError(ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationErrors(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
            .map(error -> error.getField() + ": " + error.getDefaultMessage())
            .findFirst()
            .orElse("Validation failed");
        
        logger.warn("Validation error: {}", message);
        ErrorResponse error = new ErrorResponse();
        error.setError(message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(DynamoDbException.class)
    public ResponseEntity<ErrorResponse> handleDynamoDbException(DynamoDbException ex) {
        logger.error("DynamoDB error: {}", ex.getMessage(), ex);
        ErrorResponse error = new ErrorResponse();
        error.setError("Database error occurred. Please try again later.");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        logger.warn("Invalid argument: {}", ex.getMessage());
        ErrorResponse error = new ErrorResponse();
        error.setError(ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        logger.warn("Access denied: {}", ex.getMessage());
        ErrorResponse error = new ErrorResponse();
        error.setError("Access denied");
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        logger.error("Unexpected error: {}", ex.getMessage(), ex);
        ErrorResponse error = new ErrorResponse();
        error.setError("An unexpected error occurred. Please try again later.");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}
