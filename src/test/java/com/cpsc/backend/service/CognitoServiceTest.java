package com.cpsc.backend.service;

import com.cpsc.backend.config.SecretsManagerConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.*;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CognitoServiceTest {

    @Mock
    private CognitoIdentityProviderClient cognitoClient;

    @Mock
    private SecretsManagerConfig secretsManagerConfig;

    private CognitoService cognitoService;

    @BeforeEach
    void setUp() {
        when(secretsManagerConfig.getCognitoUserPoolId()).thenReturn("us-east-1_test");
        when(secretsManagerConfig.getCognitoClientId()).thenReturn("test-client-id");
        when(secretsManagerConfig.getCognitoClientSecret()).thenReturn("test-secret");

        cognitoService = new CognitoService(cognitoClient, secretsManagerConfig);
    }

    @Test
    void signUp_Success() {
        // Arrange
        SignUpResponse mockResponse = SignUpResponse.builder()
                .userSub("test-user-sub")
                .userConfirmed(false)
                .build();

        when(cognitoClient.signUp(any(SignUpRequest.class))).thenReturn(mockResponse);

        // Act
        Map<String, String> result = cognitoService.signUp("test@example.com", "Test@1234");

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.get("message")).isEqualTo("User registered successfully");
        assertThat(result.get("userSub")).isEqualTo("test-user-sub");
        assertThat(result.get("confirmed")).isEqualTo("false");

        verify(cognitoClient).signUp(any(SignUpRequest.class));
    }

    @Test
    void signUp_UsernameExists_ThrowsException() {
        // Arrange
        when(cognitoClient.signUp(any(SignUpRequest.class)))
                .thenThrow(UsernameExistsException.builder().message("User exists").build());

        // Act & Assert
        assertThatThrownBy(() -> cognitoService.signUp("test@example.com", "Test@1234"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Username already exists");

        verify(cognitoClient).signUp(any(SignUpRequest.class));
    }

    @Test
    void signUp_InvalidPassword_ThrowsException() {
        // Arrange
        when(cognitoClient.signUp(any(SignUpRequest.class)))
                .thenThrow(InvalidPasswordException.builder().message("Weak password").build());

        // Act & Assert
        assertThatThrownBy(() -> cognitoService.signUp("test@example.com", "weak"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Invalid password. Password must meet the requirements.");

        verify(cognitoClient).signUp(any(SignUpRequest.class));
    }

    @Test
    void confirmSignUp_Success() {
        // Arrange
        ConfirmSignUpResponse mockResponse = ConfirmSignUpResponse.builder().build();
        when(cognitoClient.confirmSignUp(any(ConfirmSignUpRequest.class))).thenReturn(mockResponse);

        // Act
        Map<String, String> result = cognitoService.confirmSignUp("test@example.com", "123456");

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.get("message")).isEqualTo("User confirmed successfully");

        verify(cognitoClient).confirmSignUp(any(ConfirmSignUpRequest.class));
    }

    @Test
    void confirmSignUp_CodeMismatch_ThrowsException() {
        // Arrange
        when(cognitoClient.confirmSignUp(any(ConfirmSignUpRequest.class)))
                .thenThrow(CodeMismatchException.builder().message("Wrong code").build());

        // Act & Assert
        assertThatThrownBy(() -> cognitoService.confirmSignUp("test@example.com", "wrong"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Invalid verification code");

        verify(cognitoClient).confirmSignUp(any(ConfirmSignUpRequest.class));
    }

    @Test
    void confirmSignUp_ExpiredCode_ThrowsException() {
        // Arrange
        when(cognitoClient.confirmSignUp(any(ConfirmSignUpRequest.class)))
                .thenThrow(ExpiredCodeException.builder().message("Code expired").build());

        // Act & Assert
        assertThatThrownBy(() -> cognitoService.confirmSignUp("test@example.com", "123456"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Verification code has expired");

        verify(cognitoClient).confirmSignUp(any(ConfirmSignUpRequest.class));
    }

    @Test
    void resendConfirmationCode_Success() {
        // Arrange
        ResendConfirmationCodeResponse mockResponse = ResendConfirmationCodeResponse.builder().build();
        when(cognitoClient.resendConfirmationCode(any(ResendConfirmationCodeRequest.class)))
                .thenReturn(mockResponse);

        // Act
        Map<String, String> result = cognitoService.resendConfirmationCode("test@example.com");

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.get("message")).isEqualTo("Verification code resent successfully");

        verify(cognitoClient).resendConfirmationCode(any(ResendConfirmationCodeRequest.class));
    }

    @Test
    void login_Success() {
        // Arrange
        AuthenticationResultType authResult = AuthenticationResultType.builder()
                .accessToken("access-token")
                .idToken("id-token")
                .refreshToken("refresh-token")
                .expiresIn(3600)
                .tokenType("Bearer")
                .build();

        InitiateAuthResponse mockResponse = InitiateAuthResponse.builder()
                .authenticationResult(authResult)
                .build();

        when(cognitoClient.initiateAuth(any(InitiateAuthRequest.class))).thenReturn(mockResponse);

        // Act
        Map<String, String> result = cognitoService.login("test@example.com", "Test@1234");

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.get("accessToken")).isEqualTo("access-token");
        assertThat(result.get("idToken")).isEqualTo("id-token");
        assertThat(result.get("refreshToken")).isEqualTo("refresh-token");
        assertThat(result.get("expiresIn")).isEqualTo("3600");
        assertThat(result.get("tokenType")).isEqualTo("Bearer");

        verify(cognitoClient).initiateAuth(any(InitiateAuthRequest.class));
    }

    @Test
    void login_NotAuthorized_ThrowsException() {
        // Arrange
        when(cognitoClient.initiateAuth(any(InitiateAuthRequest.class)))
                .thenThrow(NotAuthorizedException.builder().message("Wrong password").build());

        // Act & Assert
        assertThatThrownBy(() -> cognitoService.login("test@example.com", "wrong"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Incorrect username or password");

        verify(cognitoClient).initiateAuth(any(InitiateAuthRequest.class));
    }

    @Test
    void login_UserNotConfirmed_ThrowsException() {
        // Arrange
        when(cognitoClient.initiateAuth(any(InitiateAuthRequest.class)))
                .thenThrow(UserNotConfirmedException.builder().message("Not confirmed").build());

        // Act & Assert
        assertThatThrownBy(() -> cognitoService.login("test@example.com", "Test@1234"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("User is not confirmed. Please verify your email.");

        verify(cognitoClient).initiateAuth(any(InitiateAuthRequest.class));
    }
}
