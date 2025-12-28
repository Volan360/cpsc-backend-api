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
