package com.cpsc.backend.controller;

import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.cpsc.backend.api.TestApi;
import com.cpsc.backend.model.GetProfile200Response;
import com.cpsc.backend.model.Hello200Response;
import com.cpsc.backend.security.JwtValidator;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestController implements TestApi {

    private final JwtValidator jwtValidator;

    public TestController(JwtValidator jwtValidator) {
        this.jwtValidator = jwtValidator;
    }

    @Override
    public ResponseEntity<Hello200Response> hello() {
        Hello200Response response = new Hello200Response();
        response.setMessage("Hello from CPSC Backend API!");
        response.setStatus("success");
        return ResponseEntity.ok(response); // Auto-deployed via CI/CD
    }

    @Override
    public ResponseEntity<GetProfile200Response> getProfile() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        // The JWT token is stored as credentials in the authentication
        String token = (String) authentication.getCredentials();
        DecodedJWT decodedJWT = JWT.decode(token);
        
        String email = jwtValidator.getEmail(decodedJWT);
        String screenName = jwtValidator.getScreenName(decodedJWT);
        
        GetProfile200Response response = new GetProfile200Response();
        response.setMessage("Welcome to your profile!");
        response.setEmail(email);
        response.setScreenName(screenName);
        response.setAuthenticated(true);
        
        return ResponseEntity.ok(response); // Auto-deployed via CI/CD
    }
}
