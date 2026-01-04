package com.cpsc.backend.controller;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.cpsc.backend.model.*;
import com.cpsc.backend.security.JwtValidator;
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
    private JwtValidator jwtValidator;

    @Mock
    private Authentication authentication;

    @Mock
    private SecurityContext securityContext;

    @Mock
    private DecodedJWT decodedJWT;

    private AuthController authController;

    private SignUpRequest signUpRequest;
    private LoginRequest loginRequest;
    private ConfirmSignUpRequest confirmRequest;
    private ResendCodeRequest resendRequest;

    @BeforeEach
    void setUp() {
        authController = new AuthController(cognitoService, jwtValidator);

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
            // Arrange - use a valid JWT structure (header.payload.signature)
            String mockToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJlbWFpbCI6InRlc3RAZXhhbXBsZS5jb20iLCJwcmVmZXJyZWRfdXNlcm5hbWUiOiJUZXN0VXNlcjEyMyJ9.mock_signature";
            securityContextHolder.when(SecurityContextHolder::getContext).thenReturn(securityContext);
            when(securityContext.getAuthentication()).thenReturn(authentication);
            when(authentication.getCredentials()).thenReturn(mockToken);
            when(jwtValidator.getEmail(any(DecodedJWT.class))).thenReturn("test@example.com");
            when(jwtValidator.getScreenName(any(DecodedJWT.class))).thenReturn("TestUser123");

            // Act
            ResponseEntity<GetProfile200Response> response = authController.getProfile();

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getMessage()).isEqualTo("Welcome to your profile!");
            assertThat(response.getBody().getEmail()).isEqualTo("test@example.com");
            assertThat(response.getBody().getScreenName()).isEqualTo("TestUser123");
            assertThat(response.getBody().getAuthenticated()).isTrue();
        }
    }
}
