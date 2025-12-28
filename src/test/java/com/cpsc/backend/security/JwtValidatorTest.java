package com.cpsc.backend.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.cpsc.backend.config.CognitoConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
        assertThat(jwtValidator).isNotNull();
    }

    @Test
    void constructor_NullCognitoConfig_ThrowsException() {
        assertThatThrownBy(() -> new JwtValidator(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void validateToken_NullToken_ReturnsNull() {
        DecodedJWT result = jwtValidator.validateToken(null);
        
        assertThat(result).isNull();
    }

    @Test
    void validateToken_EmptyToken_ReturnsNull() {
        DecodedJWT result = jwtValidator.validateToken("");
        
        assertThat(result).isNull();
    }

    @Test
    void validateToken_InvalidFormat_ReturnsNull() {
        DecodedJWT result = jwtValidator.validateToken("not.a.valid.jwt.token");
        
        assertThat(result).isNull();
    }

    @Test
    void validateToken_MalformedToken_ReturnsNull() {
        DecodedJWT result = jwtValidator.validateToken("malformed");
        
        assertThat(result).isNull();
    }

    @Test
    void validateToken_WhitespaceToken_ReturnsNull() {
        DecodedJWT result = jwtValidator.validateToken("   ");
        
        assertThat(result).isNull();
    }

    @Test
    void validateToken_TokenWithInvalidSignature_ReturnsNull() throws Exception {
        // Create a token with one key pair
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        KeyPair keyPair = keyGen.generateKeyPair();
        RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();
        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();

        Algorithm algorithm = Algorithm.RSA256(publicKey, privateKey);
        
        String token = JWT.create()
                .withIssuer("https://cognito-idp.us-east-1.amazonaws.com/us-east-1_test")
                .withSubject("user-123")
                .withKeyId("test-key-id")
                .withExpiresAt(Date.from(Instant.now().plusSeconds(3600)))
                .sign(algorithm);

        // The validator will fail to verify because it fetches keys from Cognito's JWKS
        // which won't match our locally generated key
        DecodedJWT result = jwtValidator.validateToken(token);
        
        assertThat(result).isNull();
    }

    @Test
    void validateToken_TokenWithWrongIssuer_ReturnsNull() throws Exception {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        KeyPair keyPair = keyGen.generateKeyPair();
        RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();
        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();

        Algorithm algorithm = Algorithm.RSA256(publicKey, privateKey);
        
        String token = JWT.create()
                .withIssuer("https://wrong-issuer.example.com")
                .withSubject("user-123")
                .withKeyId("test-key-id")
                .withExpiresAt(Date.from(Instant.now().plusSeconds(3600)))
                .sign(algorithm);

        DecodedJWT result = jwtValidator.validateToken(token);
        
        assertThat(result).isNull();
    }

    @Test
    void validateToken_TokenWithMissingKeyId_ReturnsNull() throws Exception {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        KeyPair keyPair = keyGen.generateKeyPair();
        RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();
        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();

        Algorithm algorithm = Algorithm.RSA256(publicKey, privateKey);
        
        // Create token without keyId (kid) in header
        String token = JWT.create()
                .withIssuer("https://cognito-idp.us-east-1.amazonaws.com/us-east-1_test")
                .withSubject("user-123")
                .withExpiresAt(Date.from(Instant.now().plusSeconds(3600)))
                .sign(algorithm);

        // Will fail when trying to get JWK with null kid
        DecodedJWT result = jwtValidator.validateToken(token);
        
        assertThat(result).isNull();
    }

    @Test
    void validateToken_TokenWithSpecialCharacters_ReturnsNull() {
        String tokenWithSpecialChars = "eyJ!@#$%^&*()+=<>?/\\|{}[]`~.eyJ!@#$%^&*()+=<>?/\\|{}[]`~.signature!@#$%^&*()";
        
        DecodedJWT result = jwtValidator.validateToken(tokenWithSpecialChars);
        
        assertThat(result).isNull();
    }

    @Test
    void validateToken_VeryLongToken_ReturnsNull() {
        // Create a very long invalid token
        String longToken = "a".repeat(10000);
        
        DecodedJWT result = jwtValidator.validateToken(longToken);
        
        assertThat(result).isNull();
    }

    @Test
    void validateToken_TokenWithOnlyTwoParts_ReturnsNull() {
        // JWT should have 3 parts separated by dots
        String incompletToken = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ1c2VyLTEyMyJ9";
        
        DecodedJWT result = jwtValidator.validateToken(incompletToken);
        
        assertThat(result).isNull();
    }

    @Test
    void validateToken_TokenWithInvalidBase64_ReturnsNull() {
        // Invalid base64 encoding
        String invalidBase64Token = "not-valid-base64.not-valid-base64.not-valid-base64";
        
        DecodedJWT result = jwtValidator.validateToken(invalidBase64Token);
        
        assertThat(result).isNull();
    }

    @Test
    void validateToken_ExpiredToken_ReturnsNull() throws Exception {
        // Create an expired token
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        KeyPair keyPair = keyGen.generateKeyPair();
        RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();
        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();

        Algorithm algorithm = Algorithm.RSA256(publicKey, privateKey);
        
        String expiredToken = JWT.create()
                .withIssuer("https://cognito-idp.us-east-1.amazonaws.com/us-east-1_test")
                .withSubject("user-123")
                .withExpiresAt(Date.from(Instant.now().minusSeconds(3600))) // Expired 1 hour ago
                .sign(algorithm);

        DecodedJWT result = jwtValidator.validateToken(expiredToken);
        
        // Will return null because signature verification will fail (different keys)
        assertThat(result).isNull();
    }

    @Test
    void getUserId_ValidToken_ReturnsUserId() throws Exception {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        KeyPair keyPair = keyGen.generateKeyPair();
        RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();
        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();

        Algorithm algorithm = Algorithm.RSA256(publicKey, privateKey);
        
        String token = JWT.create()
                .withIssuer("https://cognito-idp.us-east-1.amazonaws.com/us-east-1_test")
                .withSubject("user-123")
                .withExpiresAt(Date.from(Instant.now().plusSeconds(3600)))
                .sign(algorithm);

        DecodedJWT decodedJWT = JWT.decode(token);
        String userId = jwtValidator.getUserId(decodedJWT);
        
        assertThat(userId).isEqualTo("user-123");
    }

    @Test
    void getUserId_NullToken_ReturnsNull() {
        String userId = jwtValidator.getUserId(null);
        
        assertThat(userId).isNull();
    }

    @Test
    void getUserId_TokenWithoutSubject_ReturnsNull() throws Exception {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        KeyPair keyPair = keyGen.generateKeyPair();
        RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();
        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();

        Algorithm algorithm = Algorithm.RSA256(publicKey, privateKey);
        
        // Create token without subject
        String token = JWT.create()
                .withIssuer("https://cognito-idp.us-east-1.amazonaws.com/us-east-1_test")
                .withExpiresAt(Date.from(Instant.now().plusSeconds(3600)))
                .sign(algorithm);

        DecodedJWT decodedJWT = JWT.decode(token);
        String userId = jwtValidator.getUserId(decodedJWT);
        
        assertThat(userId).isNull();
    }

    @Test
    void getUserId_TokenWithEmptySubject_ReturnsNull() throws Exception {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        KeyPair keyPair = keyGen.generateKeyPair();
        RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();
        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();

        Algorithm algorithm = Algorithm.RSA256(publicKey, privateKey);
        
        // Create token with empty subject
        String token = JWT.create()
                .withIssuer("https://cognito-idp.us-east-1.amazonaws.com/us-east-1_test")
                .withSubject("")
                .withExpiresAt(Date.from(Instant.now().plusSeconds(3600)))
                .sign(algorithm);

        DecodedJWT decodedJWT = JWT.decode(token);
        String userId = jwtValidator.getUserId(decodedJWT);
        
        assertThat(userId).isNull();
    }
}
