package com.cpsc.backend.exception;

public class InvalidInstitutionDataException extends RuntimeException {
    public InvalidInstitutionDataException(String message) {
        super(message);
    }
}
