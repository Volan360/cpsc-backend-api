package com.cpsc.backend.controller;

import com.cpsc.backend.api.AuthenticationApi;
import com.cpsc.backend.model.ConfirmForgotPasswordRequest;
import com.cpsc.backend.model.ConfirmForgotPasswordResponse;
import com.cpsc.backend.model.ConfirmSignUpRequest;
import com.cpsc.backend.model.ConfirmSignUpResponse;
import com.cpsc.backend.model.ErrorResponse;
import com.cpsc.backend.model.ForgotPasswordRequest;
import com.cpsc.backend.model.ForgotPasswordResponse;
import com.cpsc.backend.model.GetProfile200Response;
import com.cpsc.backend.model.LoginRequest;
import com.cpsc.backend.model.LoginResponse;
import com.cpsc.backend.model.ResendCodeRequest;
import com.cpsc.backend.model.ResendCodeResponse;
import com.cpsc.backend.model.SignUpRequest;
import com.cpsc.backend.model.SignUpResponse;
import com.cpsc.backend.service.CognitoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class AuthController implements AuthenticationApi {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);
    private final CognitoService cognitoService;

    public AuthController(CognitoService cognitoService) {
        this.cognitoService = cognitoService;
    }

    @Override
    public ResponseEntity<SignUpResponse> signUp(SignUpRequest request) {
        try {
            Map<String, String> result = cognitoService.signUp(
                request.getEmail(),
                request.getPassword(),
                request.getScreenName()
            );
            
            SignUpResponse response = new SignUpResponse();
            response.setMessage(result.get("message"));
            response.setUserSub(result.get("userSub"));
            response.setConfirmed(result.get("confirmed"));
            
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (RuntimeException e) {
            ErrorResponse error = new ErrorResponse();
            error.setError(e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        }
    }

    @Override
    public ResponseEntity<ConfirmSignUpResponse> confirmSignUp(ConfirmSignUpRequest request) {
        try {
            Map<String, String> result = cognitoService.confirmSignUp(
                request.getEmail(),
                request.getConfirmationCode()
            );
            
            ConfirmSignUpResponse response = new ConfirmSignUpResponse();
            response.setMessage(result.get("message"));
            
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            ErrorResponse error = new ErrorResponse();
            error.setError(e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        }
    }

    @Override
    public ResponseEntity<ResendCodeResponse> resendConfirmationCode(ResendCodeRequest request) {
        try {
            Map<String, String> result = cognitoService.resendConfirmationCode(
                request.getEmail()
            );
            
            ResendCodeResponse response = new ResendCodeResponse();
            response.setMessage(result.get("message"));
            
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            ErrorResponse error = new ErrorResponse();
            error.setError(e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        }
    }

    @Override
    public ResponseEntity<ForgotPasswordResponse> forgotPassword(ForgotPasswordRequest request) {
        logger.info("Forgot password request received for email: {}", request.getEmail());
        try {
            Map<String, String> result = cognitoService.forgotPassword(request.getEmail());
            
            ForgotPasswordResponse response = new ForgotPasswordResponse();
            response.setMessage(result.get("message"));
            response.setDeliveryMedium(result.get("deliveryMedium"));
            response.setDestination(result.get("destination"));
            
            logger.info("Password reset code sent for email: {}", request.getEmail());
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            logger.error("Forgot password failed for email: {}, error: {}", request.getEmail(), e.getMessage());
            ErrorResponse error = new ErrorResponse();
            error.setError(e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        }
    }

    @Override
    public ResponseEntity<ConfirmForgotPasswordResponse> confirmForgotPassword(ConfirmForgotPasswordRequest request) {
        logger.info("Confirm forgot password request received for email: {}", request.getEmail());
        try {
            Map<String, String> result = cognitoService.confirmForgotPassword(
                request.getEmail(),
                request.getConfirmationCode(),
                request.getNewPassword()
            );
            
            ConfirmForgotPasswordResponse response = new ConfirmForgotPasswordResponse();
            response.setMessage(result.get("message"));
            
            logger.info("Password reset successful for email: {}", request.getEmail());
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            logger.error("Confirm forgot password failed for email: {}, error: {}", request.getEmail(), e.getMessage());
            ErrorResponse error = new ErrorResponse();
            error.setError(e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        }
    }

    @Override
    public ResponseEntity<LoginResponse> login(LoginRequest request) {
        logger.info("Login request received for email: {}", request.getEmail());
        try {
            Map<String, String> result = cognitoService.login(
                request.getEmail(),
                request.getPassword()
            );
            
            LoginResponse response = new LoginResponse();
            response.setAccessToken(result.get("accessToken"));
            response.setIdToken(result.get("idToken"));
            response.setRefreshToken(result.get("refreshToken"));
            response.setExpiresIn(result.get("expiresIn"));
            response.setTokenType(result.get("tokenType"));
            response.setScreenName(result.get("screenName"));
            response.setEmail(result.get("email"));
            
            logger.info("Login successful for email: {}", request.getEmail());
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            logger.error("Login failed for email: {}, error: {}", request.getEmail(), e.getMessage());
            ErrorResponse error = new ErrorResponse();
            error.setError(e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null);
        }
    }

    @Override
    public ResponseEntity<GetProfile200Response> getProfile() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        // The access token is stored as credentials in the authentication
        String accessToken = (String) authentication.getCredentials();
        
        // Use Cognito GetUser API to fetch user attributes (including preferred_username)
        Map<String, String> profile = cognitoService.getUserProfile(accessToken);
        
        GetProfile200Response response = new GetProfile200Response();
        response.setMessage("Welcome to your profile!");
        response.setEmail(profile.get("email"));
        response.setScreenName(profile.get("preferred_username"));
        response.setAuthenticated(true);
        
        return ResponseEntity.ok(response);
    }
}
