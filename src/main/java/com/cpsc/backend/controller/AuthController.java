package com.cpsc.backend.controller;

import com.cpsc.backend.api.AuthenticationApi;
import com.cpsc.backend.model.ConfirmSignUpRequest;
import com.cpsc.backend.model.ConfirmSignUpResponse;
import com.cpsc.backend.model.ErrorResponse;
import com.cpsc.backend.model.LoginRequest;
import com.cpsc.backend.model.LoginResponse;
import com.cpsc.backend.model.ResendCodeRequest;
import com.cpsc.backend.model.ResendCodeResponse;
import com.cpsc.backend.model.SignUpRequest;
import com.cpsc.backend.model.SignUpResponse;
import com.cpsc.backend.service.CognitoService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class AuthController implements AuthenticationApi {

    private final CognitoService cognitoService;

    public AuthController(CognitoService cognitoService) {
        this.cognitoService = cognitoService;
    }

    @Override
    public ResponseEntity<SignUpResponse> signUp(SignUpRequest request) {
        try {
            Map<String, String> result = cognitoService.signUp(
                request.getEmail(),
                request.getPassword()
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
    public ResponseEntity<LoginResponse> login(LoginRequest request) {
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
            
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            ErrorResponse error = new ErrorResponse();
            error.setError(e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null);
        }
    }
}
