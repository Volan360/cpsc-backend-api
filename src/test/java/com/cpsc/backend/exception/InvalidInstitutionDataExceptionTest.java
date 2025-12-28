package com.cpsc.backend.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InvalidInstitutionDataExceptionTest {

    @Test
    void constructor_WithMessage_CreatesException() {
        String message = "Invalid institution data";
        
        InvalidInstitutionDataException exception = new InvalidInstitutionDataException(message);
        
        assertThat(exception).isNotNull();
        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception).isInstanceOf(RuntimeException.class);
    }

    @Test
    void constructor_WithNullMessage_CreatesException() {
        InvalidInstitutionDataException exception = new InvalidInstitutionDataException(null);
        
        assertThat(exception).isNotNull();
        assertThat(exception.getMessage()).isNull();
    }

    @Test
    void constructor_WithEmptyMessage_CreatesException() {
        String message = "";
        
        InvalidInstitutionDataException exception = new InvalidInstitutionDataException(message);
        
        assertThat(exception).isNotNull();
        assertThat(exception.getMessage()).isEqualTo(message);
    }

    @Test
    void throw_ExceptionCanBeCaught() {
        assertThatThrownBy(() -> {
            throw new InvalidInstitutionDataException("Test error");
        })
        .isInstanceOf(InvalidInstitutionDataException.class)
        .hasMessage("Test error");
    }

    @Test
    void throw_ExceptionCanBeCaughtAsRuntimeException() {
        assertThatThrownBy(() -> {
            throw new InvalidInstitutionDataException("Test error");
        })
        .isInstanceOf(RuntimeException.class)
        .hasMessage("Test error");
    }

    @Test
    void getMessage_ReturnsCorrectMessage() {
        String expectedMessage = "Institution name cannot be empty";
        InvalidInstitutionDataException exception = new InvalidInstitutionDataException(expectedMessage);
        
        assertThat(exception.getMessage()).isEqualTo(expectedMessage);
    }

    @Test
    void multipleExceptions_HaveIndependentMessages() {
        InvalidInstitutionDataException exception1 = new InvalidInstitutionDataException("Error 1");
        InvalidInstitutionDataException exception2 = new InvalidInstitutionDataException("Error 2");
        
        assertThat(exception1.getMessage()).isEqualTo("Error 1");
        assertThat(exception2.getMessage()).isEqualTo("Error 2");
    }
}
