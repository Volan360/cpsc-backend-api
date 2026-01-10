package com.cpsc.backend.controller;

import com.cpsc.backend.model.*;
import com.cpsc.backend.service.CognitoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private CognitoService cognitoService;

    @Mock
    private Authentication authentication;

    @Mock
    private SecurityContext securityContext;

    private AuthController authController;

    private SignUpRequest signUpRequest;
    private LoginRequest loginRequest;
    private ConfirmSignUpRequest confirmRequest;
    private ResendCodeRequest resendRequest;
    private ForgotPasswordRequest forgotPasswordRequest;
    private ConfirmForgotPasswordRequest confirmForgotPasswordRequest;
    private UpdateScreenNameRequest updateScreenNameRequest;

    @BeforeEach
    void setUp() {
        authController = new AuthController(cognitoService);

        signUpRequest = new SignUpRequest();
        signUpRequest.setEmail("test@example.com");
        signUpRequest.setPassword("Test@1234");
        signUpRequest.setScreenName("TestUser123");

        loginRequest = new LoginRequest();
        loginRequest.setEmail("test@example.com");
        loginRequest.setPassword("Test@1234");

        confirmRequest = new ConfirmSignUpRequest();
        confirmRequest.setEmail("test@example.com");
        confirmRequest.setConfirmationCode("123456");

        resendRequest = new ResendCodeRequest();
        resendRequest.setEmail("test@example.com");

        forgotPasswordRequest = new ForgotPasswordRequest();
        forgotPasswordRequest.setEmail("test@example.com");

        confirmForgotPasswordRequest = new ConfirmForgotPasswordRequest();
        confirmForgotPasswordRequest.setEmail("test@example.com");
        confirmForgotPasswordRequest.setConfirmationCode("123456");
        confirmForgotPasswordRequest.setNewPassword("NewTest@1234");

        updateScreenNameRequest = new UpdateScreenNameRequest();
        updateScreenNameRequest.setScreenName("NewUsername123");
    }

    @Test
    void signUp_Success() {
        // Arrange
        Map<String, String> serviceResult = new HashMap<>();
        serviceResult.put("message", "User registered successfully");
        serviceResult.put("userSub", "test-user-sub");
        serviceResult.put("confirmed", "false");

        when(cognitoService.signUp(anyString(), anyString(), anyString())).thenReturn(serviceResult);

        // Act
        ResponseEntity<SignUpResponse> response = authController.signUp(signUpRequest);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("User registered successfully");
        assertThat(response.getBody().getUserSub()).isEqualTo("test-user-sub");
        assertThat(response.getBody().getConfirmed()).isEqualTo("false");

        verify(cognitoService).signUp("test@example.com", "Test@1234", "TestUser123");
    }

    @Test
    void signUp_Failure_RuntimeException() {
        // Arrange
        when(cognitoService.signUp(anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("Username already exists"));

        // Act
        ResponseEntity<SignUpResponse> response = authController.signUp(signUpRequest);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNull();

        verify(cognitoService).signUp("test@example.com", "Test@1234", "TestUser123");
    }

    @Test
    void confirmSignUp_Success() {
        // Arrange
        Map<String, String> serviceResult = new HashMap<>();
        serviceResult.put("message", "User confirmed successfully");

        when(cognitoService.confirmSignUp(anyString(), anyString())).thenReturn(serviceResult);

        // Act
        ResponseEntity<ConfirmSignUpResponse> response = authController.confirmSignUp(confirmRequest);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("User confirmed successfully");

        verify(cognitoService).confirmSignUp("test@example.com", "123456");
    }

    @Test
    void confirmSignUp_Failure_InvalidCode() {
        // Arrange
        when(cognitoService.confirmSignUp(anyString(), anyString()))
                .thenThrow(new RuntimeException("Invalid verification code"));

        // Act
        ResponseEntity<ConfirmSignUpResponse> response = authController.confirmSignUp(confirmRequest);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNull();

        verify(cognitoService).confirmSignUp("test@example.com", "123456");
    }

    @Test
    void resendConfirmationCode_Success() {
        // Arrange
        Map<String, String> serviceResult = new HashMap<>();
        serviceResult.put("message", "Verification code resent successfully");

        when(cognitoService.resendConfirmationCode(anyString())).thenReturn(serviceResult);

        // Act
        ResponseEntity<ResendCodeResponse> response = authController.resendConfirmationCode(resendRequest);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("Verification code resent successfully");

        verify(cognitoService).resendConfirmationCode("test@example.com");
    }

    @Test
    void resendConfirmationCode_Failure() {
        // Arrange
        when(cognitoService.resendConfirmationCode(anyString()))
                .thenThrow(new RuntimeException("Error resending code"));

        // Act
        ResponseEntity<ResendCodeResponse> response = authController.resendConfirmationCode(resendRequest);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNull();

        verify(cognitoService).resendConfirmationCode("test@example.com");
    }

    @Test
    void login_Success() {
        // Arrange
        Map<String, String> serviceResult = new HashMap<>();
        serviceResult.put("accessToken", "access-token");
        serviceResult.put("idToken", "id-token");
        serviceResult.put("refreshToken", "refresh-token");
        serviceResult.put("expiresIn", "3600");
        serviceResult.put("tokenType", "Bearer");
        serviceResult.put("screenName", "TestUser123");
        serviceResult.put("email", "test@example.com");

        when(cognitoService.login(anyString(), anyString())).thenReturn(serviceResult);

        // Act
        ResponseEntity<LoginResponse> response = authController.login(loginRequest);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getAccessToken()).isEqualTo("access-token");
        assertThat(response.getBody().getIdToken()).isEqualTo("id-token");
        assertThat(response.getBody().getRefreshToken()).isEqualTo("refresh-token");
        assertThat(response.getBody().getExpiresIn()).isEqualTo("3600");
        assertThat(response.getBody().getTokenType()).isEqualTo("Bearer");
        assertThat(response.getBody().getScreenName()).isEqualTo("TestUser123");
        assertThat(response.getBody().getEmail()).isEqualTo("test@example.com");

        verify(cognitoService).login("test@example.com", "Test@1234");
    }

    @Test
    void login_Failure_IncorrectCredentials() {
        // Arrange
        when(cognitoService.login(anyString(), anyString()))
                .thenThrow(new RuntimeException("Incorrect username or password"));

        // Act
        ResponseEntity<LoginResponse> response = authController.login(loginRequest);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNull();

        verify(cognitoService).login("test@example.com", "Test@1234");
    }

    @Test
    void login_Failure_UserNotConfirmed() {
        // Arrange
        when(cognitoService.login(anyString(), anyString()))
                .thenThrow(new RuntimeException("User is not confirmed. Please verify your email."));

        // Act
        ResponseEntity<LoginResponse> response = authController.login(loginRequest);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNull();

        verify(cognitoService).login("test@example.com", "Test@1234");
    }

    @Test
    void getProfile_Success() {
        try (MockedStatic<SecurityContextHolder> securityContextHolder = mockStatic(SecurityContextHolder.class)) {
            // Arrange
            String mockToken = "mock-access-token";
            Map<String, String> profileResult = new HashMap<>();
            profileResult.put("email", "test@example.com");
            profileResult.put("preferred_username", "TestUser123");
            
            securityContextHolder.when(SecurityContextHolder::getContext).thenReturn(securityContext);
            when(securityContext.getAuthentication()).thenReturn(authentication);
            when(authentication.getCredentials()).thenReturn(mockToken);
            when(cognitoService.getUserProfile(mockToken)).thenReturn(profileResult);

            // Act
            ResponseEntity<GetProfile200Response> response = authController.getProfile();

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getMessage()).isEqualTo("Welcome to your profile!");
            assertThat(response.getBody().getEmail()).isEqualTo("test@example.com");
            assertThat(response.getBody().getScreenName()).isEqualTo("TestUser123");
            assertThat(response.getBody().getAuthenticated()).isTrue();
            
            verify(cognitoService).getUserProfile(mockToken);
        }
    }

    @Test
    void forgotPassword_Success() {
        // Arrange
        Map<String, String> serviceResult = new HashMap<>();
        serviceResult.put("message", "Password reset code sent to your email");
        serviceResult.put("deliveryMedium", "EMAIL");
        serviceResult.put("destination", "t***@e***.com");

        when(cognitoService.forgotPassword(anyString())).thenReturn(serviceResult);

        // Act
        ResponseEntity<ForgotPasswordResponse> response = authController.forgotPassword(forgotPasswordRequest);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("Password reset code sent to your email");
        assertThat(response.getBody().getDeliveryMedium()).isEqualTo("EMAIL");
        assertThat(response.getBody().getDestination()).isEqualTo("t***@e***.com");

        verify(cognitoService).forgotPassword("test@example.com");
    }

    @Test
    void forgotPassword_Failure_UserNotFound() {
        // Arrange
        when(cognitoService.forgotPassword(anyString()))
                .thenThrow(new RuntimeException("User not found"));

        // Act
        ResponseEntity<ForgotPasswordResponse> response = authController.forgotPassword(forgotPasswordRequest);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNull();

        verify(cognitoService).forgotPassword("test@example.com");
    }

    @Test
    void forgotPassword_Failure_LimitExceeded() {
        // Arrange
        when(cognitoService.forgotPassword(anyString()))
                .thenThrow(new RuntimeException("Too many requests. Please try again later."));

        // Act
        ResponseEntity<ForgotPasswordResponse> response = authController.forgotPassword(forgotPasswordRequest);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNull();

        verify(cognitoService).forgotPassword("test@example.com");
    }

    @Test
    void confirmForgotPassword_Success() {
        // Arrange
        Map<String, String> serviceResult = new HashMap<>();
        serviceResult.put("message", "Password reset successfully. You can now login with your new password.");

        when(cognitoService.confirmForgotPassword(anyString(), anyString(), anyString())).thenReturn(serviceResult);

        // Act
        ResponseEntity<ConfirmForgotPasswordResponse> response = 
                authController.confirmForgotPassword(confirmForgotPasswordRequest);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage())
                .isEqualTo("Password reset successfully. You can now login with your new password.");

        verify(cognitoService).confirmForgotPassword("test@example.com", "123456", "NewTest@1234");
    }

    @Test
    void confirmForgotPassword_Failure_InvalidCode() {
        // Arrange
        when(cognitoService.confirmForgotPassword(anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("Invalid verification code"));

        // Act
        ResponseEntity<ConfirmForgotPasswordResponse> response = 
                authController.confirmForgotPassword(confirmForgotPasswordRequest);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNull();

        verify(cognitoService).confirmForgotPassword("test@example.com", "123456", "NewTest@1234");
    }

    @Test
    void confirmForgotPassword_Failure_ExpiredCode() {
        // Arrange
        when(cognitoService.confirmForgotPassword(anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("Verification code has expired. Please request a new one."));

        // Act
        ResponseEntity<ConfirmForgotPasswordResponse> response = 
                authController.confirmForgotPassword(confirmForgotPasswordRequest);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNull();

        verify(cognitoService).confirmForgotPassword("test@example.com", "123456", "NewTest@1234");
    }

    @Test
    void confirmForgotPassword_Failure_InvalidPassword() {
        // Arrange
        when(cognitoService.confirmForgotPassword(anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("Invalid password. Password must meet the requirements."));

        // Act
        ResponseEntity<ConfirmForgotPasswordResponse> response = 
                authController.confirmForgotPassword(confirmForgotPasswordRequest);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNull();

        verify(cognitoService).confirmForgotPassword("test@example.com", "123456", "NewTest@1234");
    }

    @Test
    void confirmForgotPassword_Failure_UserNotFound() {
        // Arrange
        when(cognitoService.confirmForgotPassword(anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("User not found"));

        // Act
        ResponseEntity<ConfirmForgotPasswordResponse> response = 
                authController.confirmForgotPassword(confirmForgotPasswordRequest);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNull();

        verify(cognitoService).confirmForgotPassword("test@example.com", "123456", "NewTest@1234");
    }

    @Test
    void updateScreenName_Success() {
        try (MockedStatic<SecurityContextHolder> securityContextHolder = mockStatic(SecurityContextHolder.class)) {
            // Arrange
            String mockToken = "mock-access-token";
            Map<String, String> serviceResult = new HashMap<>();
            serviceResult.put("message", "Screen name updated successfully");
            serviceResult.put("screenName", "NewUsername123");
            
            securityContextHolder.when(SecurityContextHolder::getContext).thenReturn(securityContext);
            when(securityContext.getAuthentication()).thenReturn(authentication);
            when(authentication.getCredentials()).thenReturn(mockToken);
            when(cognitoService.updateScreenName(mockToken, "NewUsername123")).thenReturn(serviceResult);

            // Act
            ResponseEntity<UpdateScreenNameResponse> response = authController.updateScreenName(updateScreenNameRequest);

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getMessage()).isEqualTo("Screen name updated successfully");
            assertThat(response.getBody().getScreenName()).isEqualTo("NewUsername123");
            
            verify(cognitoService).updateScreenName(mockToken, "NewUsername123");
        }
    }

    @Test
    void updateScreenName_Failure_InvalidFormat() {
        try (MockedStatic<SecurityContextHolder> securityContextHolder = mockStatic(SecurityContextHolder.class)) {
            // Arrange
            String mockToken = "mock-access-token";
            
            securityContextHolder.when(SecurityContextHolder::getContext).thenReturn(securityContext);
            when(securityContext.getAuthentication()).thenReturn(authentication);
            when(authentication.getCredentials()).thenReturn(mockToken);
            when(cognitoService.updateScreenName(mockToken, "NewUsername123"))
                    .thenThrow(new RuntimeException("Invalid screen name format"));

            // Act
            ResponseEntity<UpdateScreenNameResponse> response = authController.updateScreenName(updateScreenNameRequest);

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).isNull();
            
            verify(cognitoService).updateScreenName(mockToken, "NewUsername123");
        }
    }

    @Test
    void updateScreenName_Failure_ServiceError() {
        try (MockedStatic<SecurityContextHolder> securityContextHolder = mockStatic(SecurityContextHolder.class)) {
            // Arrange
            String mockToken = "mock-access-token";
            
            securityContextHolder.when(SecurityContextHolder::getContext).thenReturn(securityContext);
            when(securityContext.getAuthentication()).thenReturn(authentication);
            when(authentication.getCredentials()).thenReturn(mockToken);
            when(cognitoService.updateScreenName(mockToken, "NewUsername123"))
                    .thenThrow(new RuntimeException("Error updating screen name: Service error"));

            // Act
            ResponseEntity<UpdateScreenNameResponse> response = authController.updateScreenName(updateScreenNameRequest);

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).isNull();
            
            verify(cognitoService).updateScreenName(mockToken, "NewUsername123");
        }
    }
}
