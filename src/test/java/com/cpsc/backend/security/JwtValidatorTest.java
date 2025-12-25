package com.cpsc.backend.security;

import com.cpsc.backend.config.CognitoConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtValidatorTest {

    @Mock
    private CognitoConfig cognitoConfig;

    private JwtValidator jwtValidator;

    @BeforeEach
    void setUp() {
        when(cognitoConfig.getUserPoolId()).thenReturn("us-east-1_test");
        when(cognitoConfig.getRegion()).thenReturn("us-east-1");
        jwtValidator = new JwtValidator(cognitoConfig);
    }

    @Test
    void constructor_InitializesSuccessfully() {
        // Assert
        assertThat(jwtValidator).isNotNull();
    }
}
