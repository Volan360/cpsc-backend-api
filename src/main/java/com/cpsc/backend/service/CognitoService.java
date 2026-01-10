package com.cpsc.backend.service;

import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;
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
     * Uses email as the username, stores screen name as preferred_username
     */
    public Map<String, String> signUp(String email, String password, String screenName) {
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
                                    .build(),
                            AttributeType.builder()
                                    .name("preferred_username")
                                    .value(screenName)
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
     * Initiate forgot password flow - sends reset code to user's email
     */
    public Map<String, String> forgotPassword(String email) {
        try {
            String secretHash = calculateSecretHash(email);

            ForgotPasswordRequest forgotPasswordRequest = ForgotPasswordRequest.builder()
                    .clientId(clientId)
                    .secretHash(secretHash)
                    .username(email)
                    .build();

            ForgotPasswordResponse forgotPasswordResponse = cognitoClient.forgotPassword(forgotPasswordRequest);

            Map<String, String> response = new HashMap<>();
            response.put("message", "Password reset code sent to your email");
            
            if (forgotPasswordResponse.codeDeliveryDetails() != null) {
                response.put("deliveryMedium", forgotPasswordResponse.codeDeliveryDetails().deliveryMediumAsString());
                response.put("destination", forgotPasswordResponse.codeDeliveryDetails().destination());
            }
            
            return response;

        } catch (UserNotFoundException e) {
            throw new RuntimeException("User not found");
        } catch (InvalidParameterException e) {
            throw new RuntimeException("Cannot reset password for this user. Please contact support.");
        } catch (LimitExceededException e) {
            throw new RuntimeException("Too many requests. Please try again later.");
        } catch (CognitoIdentityProviderException e) {
            throw new RuntimeException("Error initiating password reset: " + e.getMessage());
        }
    }

    /**
     * Confirm forgot password with verification code and new password
     */
    public Map<String, String> confirmForgotPassword(String email, String confirmationCode, String newPassword) {
        try {
            String secretHash = calculateSecretHash(email);

            ConfirmForgotPasswordRequest confirmForgotPasswordRequest = ConfirmForgotPasswordRequest.builder()
                    .clientId(clientId)
                    .secretHash(secretHash)
                    .username(email)
                    .confirmationCode(confirmationCode)
                    .password(newPassword)
                    .build();

            cognitoClient.confirmForgotPassword(confirmForgotPasswordRequest);

            Map<String, String> response = new HashMap<>();
            response.put("message", "Password reset successfully. You can now login with your new password.");
            return response;

        } catch (CodeMismatchException e) {
            throw new RuntimeException("Invalid verification code");
        } catch (ExpiredCodeException e) {
            throw new RuntimeException("Verification code has expired. Please request a new one.");
        } catch (InvalidPasswordException e) {
            throw new RuntimeException("Invalid password. Password must meet the requirements.");
        } catch (UserNotFoundException e) {
            throw new RuntimeException("User not found");
        } catch (LimitExceededException e) {
            throw new RuntimeException("Too many attempts. Please try again later.");
        } catch (CognitoIdentityProviderException e) {
            throw new RuntimeException("Error resetting password: " + e.getMessage());
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

            // Decode the ID token to extract user attributes
            DecodedJWT decodedIdToken = JWT.decode(authResult.idToken());
            String screenName = decodedIdToken.getClaim("preferred_username").asString();
            String userEmail = decodedIdToken.getClaim("email").asString();

            Map<String, String> response = new HashMap<>();
            response.put("accessToken", authResult.accessToken());
            response.put("idToken", authResult.idToken());
            response.put("refreshToken", authResult.refreshToken());
            response.put("expiresIn", String.valueOf(authResult.expiresIn()));
            response.put("tokenType", authResult.tokenType());
            response.put("screenName", screenName);
            response.put("email", userEmail);
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
     * Get user profile attributes using the access token
     * Uses Cognito GetUser API which returns user attributes for the authenticated user
     */
    public Map<String, String> getUserProfile(String accessToken) {
        try {
            GetUserRequest request = GetUserRequest.builder()
                    .accessToken(accessToken)
                    .build();

            GetUserResponse response = cognitoClient.getUser(request);
            
            Map<String, String> profile = new HashMap<>();
            profile.put("username", response.username());
            
            // Extract attributes from response
            for (AttributeType attr : response.userAttributes()) {
                profile.put(attr.name(), attr.value());
            }
            
            return profile;
        } catch (CognitoIdentityProviderException e) {
            throw new RuntimeException("Error getting user profile: " + e.getMessage());
        }
    }

    /**
     * Update user's screen name (preferred_username attribute)
     * Uses the access token to identify the user
     */
    public Map<String, String> updateScreenName(String accessToken, String newScreenName) {
        try {
            AttributeType screenNameAttribute = AttributeType.builder()
                    .name("preferred_username")
                    .value(newScreenName)
                    .build();

            UpdateUserAttributesRequest request = UpdateUserAttributesRequest.builder()
                    .accessToken(accessToken)
                    .userAttributes(screenNameAttribute)
                    .build();

            cognitoClient.updateUserAttributes(request);

            Map<String, String> response = new HashMap<>();
            response.put("message", "Screen name updated successfully");
            response.put("screenName", newScreenName);
            return response;

        } catch (InvalidParameterException e) {
            throw new RuntimeException("Invalid screen name format");
        } catch (CognitoIdentityProviderException e) {
            throw new RuntimeException("Error updating screen name: " + e.getMessage());
        }
    }

    /**
     * Delete the authenticated user's Cognito account
     * Uses the access token to identify and delete the user
     */
    public void deleteUser(String accessToken) {
        try {
            DeleteUserRequest request = DeleteUserRequest.builder()
                    .accessToken(accessToken)
                    .build();

            cognitoClient.deleteUser(request);

        } catch (NotAuthorizedException e) {
            throw new RuntimeException("Not authorized to delete this user");
        } catch (CognitoIdentityProviderException e) {
            throw new RuntimeException("Error deleting user account: " + e.getMessage());
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
