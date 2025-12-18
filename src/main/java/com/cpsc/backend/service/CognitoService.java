package com.cpsc.backend.service;

import com.cpsc.backend.config.SecretsManagerConfig;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Service
public class CognitoService {

    private final CognitoIdentityProviderClient cognitoClient;
    private final SecretsManagerConfig secretsManagerConfig;
    private final String userPoolId;
    private final String clientId;
    private final String clientSecret;

    public CognitoService(CognitoIdentityProviderClient cognitoClient, 
                         SecretsManagerConfig secretsManagerConfig) {
        this.cognitoClient = cognitoClient;
        this.secretsManagerConfig = secretsManagerConfig;
        this.userPoolId = secretsManagerConfig.getCognitoUserPoolId();
        this.clientId = secretsManagerConfig.getCognitoClientId();
        this.clientSecret = secretsManagerConfig.getCognitoClientSecret();
    }

    /**
     * Sign up a new user in AWS Cognito
     * Uses email as the username
     */
    public Map<String, String> signUp(String email, String password) {
        try {
            String secretHash = calculateSecretHash(email);

            SignUpRequest signUpRequest = SignUpRequest.builder()
                    .clientId(clientId)
                    .secretHash(secretHash)
                    .username(email)  // Use email as username
                    .password(password)
                    .userAttributes(
                            AttributeType.builder()
                                    .name("email")
                                    .value(email)
                                    .build()
                    )
                    .build();

            SignUpResponse signUpResponse = cognitoClient.signUp(signUpRequest);

            Map<String, String> response = new HashMap<>();
            response.put("message", "User registered successfully");
            response.put("userSub", signUpResponse.userSub());
            response.put("confirmed", String.valueOf(signUpResponse.userConfirmed()));
            return response;

        } catch (UsernameExistsException e) {
            throw new RuntimeException("Username already exists");
        } catch (InvalidPasswordException e) {
            throw new RuntimeException("Invalid password. Password must meet the requirements.");
        } catch (CognitoIdentityProviderException e) {
            throw new RuntimeException("Error during sign up: " + e.getMessage());
        }
    }

    /**
     * Confirm user signup with verification code
     */
    public Map<String, String> confirmSignUp(String email, String confirmationCode) {
        try {
            String secretHash = calculateSecretHash(email);

            ConfirmSignUpRequest confirmRequest = ConfirmSignUpRequest.builder()
                    .clientId(clientId)
                    .secretHash(secretHash)
                    .username(email)
                    .confirmationCode(confirmationCode)
                    .build();

            cognitoClient.confirmSignUp(confirmRequest);

            Map<String, String> response = new HashMap<>();
            response.put("message", "User confirmed successfully");
            return response;

        } catch (CodeMismatchException e) {
            throw new RuntimeException("Invalid verification code");
        } catch (ExpiredCodeException e) {
            throw new RuntimeException("Verification code has expired");
        } catch (CognitoIdentityProviderException e) {
            throw new RuntimeException("Error during confirmation: " + e.getMessage());
        }
    }

    /**
     * Resend confirmation code to user's email
     */
    public Map<String, String> resendConfirmationCode(String email) {
        try {
            String secretHash = calculateSecretHash(email);

            ResendConfirmationCodeRequest resendRequest = ResendConfirmationCodeRequest.builder()
                    .clientId(clientId)
                    .secretHash(secretHash)
                    .username(email)
                    .build();

            cognitoClient.resendConfirmationCode(resendRequest);

            Map<String, String> response = new HashMap<>();
            response.put("message", "Verification code resent successfully");
            return response;

        } catch (CognitoIdentityProviderException e) {
            throw new RuntimeException("Error resending code: " + e.getMessage());
        }
    }

    /**
     * Authenticate a user and return JWT tokens
     * Uses email as the username
     */
    public Map<String, String> login(String email, String password) {
        try {
            String secretHash = calculateSecretHash(email);

            Map<String, String> authParams = new HashMap<>();
            authParams.put("USERNAME", email);  // Use email as username
            authParams.put("PASSWORD", password);
            authParams.put("SECRET_HASH", secretHash);

            InitiateAuthRequest authRequest = InitiateAuthRequest.builder()
                    .clientId(clientId)
                    .authFlow(AuthFlowType.USER_PASSWORD_AUTH)
                    .authParameters(authParams)
                    .build();

            InitiateAuthResponse authResponse = cognitoClient.initiateAuth(authRequest);

            AuthenticationResultType authResult = authResponse.authenticationResult();

            Map<String, String> response = new HashMap<>();
            response.put("accessToken", authResult.accessToken());
            response.put("idToken", authResult.idToken());
            response.put("refreshToken", authResult.refreshToken());
            response.put("expiresIn", String.valueOf(authResult.expiresIn()));
            response.put("tokenType", authResult.tokenType());
            return response;

        } catch (NotAuthorizedException e) {
            throw new RuntimeException("Incorrect username or password");
        } catch (UserNotConfirmedException e) {
            throw new RuntimeException("User is not confirmed. Please verify your email.");
        } catch (CognitoIdentityProviderException e) {
            throw new RuntimeException("Error during login: " + e.getMessage());
        }
    }

    /**
     * Calculate the secret hash required for authentication
     */
    private String calculateSecretHash(String username) {
        try {
            final String HMAC_SHA256_ALGORITHM = "HmacSHA256";

            SecretKeySpec signingKey = new SecretKeySpec(
                    clientSecret.getBytes(StandardCharsets.UTF_8),
                    HMAC_SHA256_ALGORITHM
            );

            Mac mac = Mac.getInstance(HMAC_SHA256_ALGORITHM);
            mac.init(signingKey);
            mac.update(username.getBytes(StandardCharsets.UTF_8));
            byte[] rawHmac = mac.doFinal(clientId.getBytes(StandardCharsets.UTF_8));

            return Base64.getEncoder().encodeToString(rawHmac);

        } catch (Exception e) {
            throw new RuntimeException("Error calculating secret hash: " + e.getMessage());
        }
    }
}
