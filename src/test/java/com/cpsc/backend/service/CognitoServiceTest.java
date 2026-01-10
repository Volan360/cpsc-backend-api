package com.cpsc.backend.service;

import com.cpsc.backend.config.SecretsManagerConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.*;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
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
        Map<String, String> result = cognitoService.signUp("test@example.com", "Test@1234", "TestUser123");

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
        assertThatThrownBy(() -> cognitoService.signUp("test@example.com", "Test@1234", "TestUser123"))
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
        assertThatThrownBy(() -> cognitoService.signUp("test@example.com", "weak", "TestUser123"))
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
        // Arrange - create a valid JWT structure (header.payload.signature)
        // Generate the token dynamically to avoid GitGuardian false positives
        String mockIdToken = buildMockJwt(
            "{\"alg\":\"HS256\",\"typ\":\"JWT\"}",
            "{\"email\":\"test@example.com\",\"preferred_username\":\"TestUser123\"}"
        );
        
        AuthenticationResultType authResult = AuthenticationResultType.builder()
                .accessToken("access-token")
                .idToken(mockIdToken)
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
        assertThat(result.get("idToken")).isEqualTo(mockIdToken);
        assertThat(result.get("refreshToken")).isEqualTo("refresh-token");
        assertThat(result.get("expiresIn")).isEqualTo("3600");
        assertThat(result.get("tokenType")).isEqualTo("Bearer");
        assertThat(result.get("screenName")).isEqualTo("TestUser123");
        assertThat(result.get("email")).isEqualTo("test@example.com");

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

    @Test
    void getUserProfile_Success() {
        // Arrange
        String accessToken = "valid-access-token";
        GetUserResponse getUserResponse = GetUserResponse.builder()
                .username("04b8b408-3011-70f9-5a38-7a897cf03438")
                .userAttributes(
                        AttributeType.builder().name("email").value("test@example.com").build(),
                        AttributeType.builder().name("preferred_username").value("TestUser123").build(),
                        AttributeType.builder().name("sub").value("04b8b408-3011-70f9-5a38-7a897cf03438").build()
                )
                .build();

        when(cognitoClient.getUser(any(GetUserRequest.class))).thenReturn(getUserResponse);

        // Act
        Map<String, String> result = cognitoService.getUserProfile(accessToken);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.get("username")).isEqualTo("04b8b408-3011-70f9-5a38-7a897cf03438");
        assertThat(result.get("email")).isEqualTo("test@example.com");
        assertThat(result.get("preferred_username")).isEqualTo("TestUser123");
        assertThat(result.get("sub")).isEqualTo("04b8b408-3011-70f9-5a38-7a897cf03438");

        verify(cognitoClient).getUser(any(GetUserRequest.class));
    }

    @Test
    void getUserProfile_InvalidToken_ThrowsException() {
        // Arrange
        String invalidToken = "invalid-token";
        when(cognitoClient.getUser(any(GetUserRequest.class)))
                .thenThrow(CognitoIdentityProviderException.builder().message("Invalid token").build());

        // Act & Assert
        assertThatThrownBy(() -> cognitoService.getUserProfile(invalidToken))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Error getting user profile");

        verify(cognitoClient).getUser(any(GetUserRequest.class));
    }

    @Test
    void forgotPassword_Success() {
        // Arrange
        CodeDeliveryDetailsType deliveryDetails = CodeDeliveryDetailsType.builder()
                .deliveryMedium(DeliveryMediumType.EMAIL)
                .destination("t***@e***.com")
                .build();

        ForgotPasswordResponse mockResponse = ForgotPasswordResponse.builder()
                .codeDeliveryDetails(deliveryDetails)
                .build();

        when(cognitoClient.forgotPassword(any(ForgotPasswordRequest.class))).thenReturn(mockResponse);

        // Act
        Map<String, String> result = cognitoService.forgotPassword("test@example.com");

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.get("message")).isEqualTo("Password reset code sent to your email");
        assertThat(result.get("deliveryMedium")).isEqualTo("EMAIL");
        assertThat(result.get("destination")).isEqualTo("t***@e***.com");

        verify(cognitoClient).forgotPassword(any(ForgotPasswordRequest.class));
    }

    @Test
    void forgotPassword_UserNotFound_ThrowsException() {
        // Arrange
        when(cognitoClient.forgotPassword(any(ForgotPasswordRequest.class)))
                .thenThrow(UserNotFoundException.builder().message("User not found").build());

        // Act & Assert
        assertThatThrownBy(() -> cognitoService.forgotPassword("nonexistent@example.com"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("User not found");

        verify(cognitoClient).forgotPassword(any(ForgotPasswordRequest.class));
    }

    @Test
    void forgotPassword_InvalidParameter_ThrowsException() {
        // Arrange
        when(cognitoClient.forgotPassword(any(ForgotPasswordRequest.class)))
                .thenThrow(InvalidParameterException.builder().message("Invalid parameter").build());

        // Act & Assert
        assertThatThrownBy(() -> cognitoService.forgotPassword("test@example.com"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Cannot reset password for this user. Please contact support.");

        verify(cognitoClient).forgotPassword(any(ForgotPasswordRequest.class));
    }

    @Test
    void forgotPassword_LimitExceeded_ThrowsException() {
        // Arrange
        when(cognitoClient.forgotPassword(any(ForgotPasswordRequest.class)))
                .thenThrow(LimitExceededException.builder().message("Too many requests").build());

        // Act & Assert
        assertThatThrownBy(() -> cognitoService.forgotPassword("test@example.com"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Too many requests. Please try again later.");

        verify(cognitoClient).forgotPassword(any(ForgotPasswordRequest.class));
    }

    @Test
    void confirmForgotPassword_Success() {
        // Arrange
        ConfirmForgotPasswordResponse mockResponse = ConfirmForgotPasswordResponse.builder().build();
        when(cognitoClient.confirmForgotPassword(any(ConfirmForgotPasswordRequest.class)))
                .thenReturn(mockResponse);

        // Act
        Map<String, String> result = cognitoService.confirmForgotPassword(
                "test@example.com", "123456", "NewTest@1234");

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.get("message")).isEqualTo("Password reset successfully. You can now login with your new password.");

        verify(cognitoClient).confirmForgotPassword(any(ConfirmForgotPasswordRequest.class));
    }

    @Test
    void confirmForgotPassword_CodeMismatch_ThrowsException() {
        // Arrange
        when(cognitoClient.confirmForgotPassword(any(ConfirmForgotPasswordRequest.class)))
                .thenThrow(CodeMismatchException.builder().message("Wrong code").build());

        // Act & Assert
        assertThatThrownBy(() -> cognitoService.confirmForgotPassword(
                "test@example.com", "wrong", "NewTest@1234"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Invalid verification code");

        verify(cognitoClient).confirmForgotPassword(any(ConfirmForgotPasswordRequest.class));
    }

    @Test
    void confirmForgotPassword_ExpiredCode_ThrowsException() {
        // Arrange
        when(cognitoClient.confirmForgotPassword(any(ConfirmForgotPasswordRequest.class)))
                .thenThrow(ExpiredCodeException.builder().message("Code expired").build());

        // Act & Assert
        assertThatThrownBy(() -> cognitoService.confirmForgotPassword(
                "test@example.com", "123456", "NewTest@1234"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Verification code has expired. Please request a new one.");

        verify(cognitoClient).confirmForgotPassword(any(ConfirmForgotPasswordRequest.class));
    }

    @Test
    void confirmForgotPassword_InvalidPassword_ThrowsException() {
        // Arrange
        when(cognitoClient.confirmForgotPassword(any(ConfirmForgotPasswordRequest.class)))
                .thenThrow(InvalidPasswordException.builder().message("Weak password").build());

        // Act & Assert
        assertThatThrownBy(() -> cognitoService.confirmForgotPassword(
                "test@example.com", "123456", "weak"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Invalid password. Password must meet the requirements.");

        verify(cognitoClient).confirmForgotPassword(any(ConfirmForgotPasswordRequest.class));
    }

    @Test
    void confirmForgotPassword_UserNotFound_ThrowsException() {
        // Arrange
        when(cognitoClient.confirmForgotPassword(any(ConfirmForgotPasswordRequest.class)))
                .thenThrow(UserNotFoundException.builder().message("User not found").build());

        // Act & Assert
        assertThatThrownBy(() -> cognitoService.confirmForgotPassword(
                "test@example.com", "123456", "NewTest@1234"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("User not found");

        verify(cognitoClient).confirmForgotPassword(any(ConfirmForgotPasswordRequest.class));
    }

    @Test
    void confirmForgotPassword_LimitExceeded_ThrowsException() {
        // Arrange
        when(cognitoClient.confirmForgotPassword(any(ConfirmForgotPasswordRequest.class)))
                .thenThrow(LimitExceededException.builder().message("Too many attempts").build());

        // Act & Assert
        assertThatThrownBy(() -> cognitoService.confirmForgotPassword(
                "test@example.com", "123456", "NewTest@1234"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Too many attempts. Please try again later.");

        verify(cognitoClient).confirmForgotPassword(any(ConfirmForgotPasswordRequest.class));
    }

    /**
     * Helper method to build a mock JWT token dynamically.
     * This avoids hardcoding tokens that trigger secret detection tools.
     */
    private String buildMockJwt(String header, String payload) {
        String encodedHeader = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(header.getBytes(StandardCharsets.UTF_8));
        String encodedPayload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return encodedHeader + "." + encodedPayload + ".mock_signature";
    }
}
