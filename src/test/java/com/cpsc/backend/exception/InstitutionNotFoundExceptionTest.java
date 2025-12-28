package com.cpsc.backend.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InstitutionNotFoundExceptionTest {

    @Test
    void constructor_WithMessage_CreatesException() {
        String message = "Institution not found";
        
        InstitutionNotFoundException exception = new InstitutionNotFoundException(message);
        
        assertThat(exception).isNotNull();
        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception).isInstanceOf(RuntimeException.class);
    }

    @Test
    void constructor_WithNullMessage_CreatesException() {
        InstitutionNotFoundException exception = new InstitutionNotFoundException(null);
        
        assertThat(exception).isNotNull();
        assertThat(exception.getMessage()).isNull();
    }

    @Test
    void constructor_WithEmptyMessage_CreatesException() {
        String message = "";
        
        InstitutionNotFoundException exception = new InstitutionNotFoundException(message);
        
        assertThat(exception).isNotNull();
        assertThat(exception.getMessage()).isEqualTo(message);
    }

    @Test
    void throw_ExceptionCanBeCaught() {
        assertThatThrownBy(() -> {
            throw new InstitutionNotFoundException("Institution with ID inst-123 not found");
        })
        .isInstanceOf(InstitutionNotFoundException.class)
        .hasMessage("Institution with ID inst-123 not found");
    }

    @Test
    void throw_ExceptionCanBeCaughtAsRuntimeException() {
        assertThatThrownBy(() -> {
            throw new InstitutionNotFoundException("Not found");
        })
        .isInstanceOf(RuntimeException.class)
        .hasMessage("Not found");
    }

    @Test
    void getMessage_ReturnsCorrectMessage() {
        String expectedMessage = "Institution not found for user user-123";
        InstitutionNotFoundException exception = new InstitutionNotFoundException(expectedMessage);
        
        assertThat(exception.getMessage()).isEqualTo(expectedMessage);
    }

    @Test
    void multipleExceptions_HaveIndependentMessages() {
        InstitutionNotFoundException exception1 = new InstitutionNotFoundException("Not found: inst-1");
        InstitutionNotFoundException exception2 = new InstitutionNotFoundException("Not found: inst-2");
        
        assertThat(exception1.getMessage()).isEqualTo("Not found: inst-1");
        assertThat(exception2.getMessage()).isEqualTo("Not found: inst-2");
    }

    @Test
    void exceptionType_IsDifferentFromInvalidData() {
        InstitutionNotFoundException notFoundEx = new InstitutionNotFoundException("Not found");
        InvalidInstitutionDataException invalidDataEx = new InvalidInstitutionDataException("Invalid");
        
        assertThat(notFoundEx).isNotInstanceOf(InvalidInstitutionDataException.class);
        assertThat(invalidDataEx).isNotInstanceOf(InstitutionNotFoundException.class);
    }
}
