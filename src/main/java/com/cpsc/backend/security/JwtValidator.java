package com.cpsc.backend.security;

import com.auth0.jwk.Jwk;
import com.auth0.jwk.JwkProvider;
import com.auth0.jwk.JwkProviderBuilder;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.JWTVerifier;
import com.cpsc.backend.config.CognitoConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URL;
import java.security.interfaces.RSAPublicKey;
import java.util.concurrent.TimeUnit;

@Component
public class JwtValidator {

    private static final Logger logger = LoggerFactory.getLogger(JwtValidator.class);
    
    private final JwkProvider jwkProvider;
    private final String userPoolId;
    private final String region;
    
    public JwtValidator(CognitoConfig cognitoConfig) {
        this.userPoolId = cognitoConfig.getUserPoolId();
        this.region = cognitoConfig.getRegion();
        
        try {
            // Cognito JWKS endpoint: https://cognito-idp.{region}.amazonaws.com/{userPoolId}/.well-known/jwks.json
            String jwksUrl = String.format("https://cognito-idp.%s.amazonaws.com/%s/.well-known/jwks.json", 
                region, userPoolId);
            
            this.jwkProvider = new JwkProviderBuilder(new URL(jwksUrl))
                .cached(10, 24, TimeUnit.HOURS) // Cache keys for 24 hours, max 10 keys
                .rateLimited(10, 1, TimeUnit.MINUTES) // Max 10 requests per minute
                .build();
                
            logger.info("JWT Validator initialized with JWKS URL: {}", jwksUrl);
        } catch (Exception e) {
            logger.error("Failed to initialize JWT Validator", e);
            throw new RuntimeException("Failed to initialize JWT Validator", e);
        }
    }
    
    /**
     * Validates and verifies the JWT token signature against Cognito's public keys
     * @param token The JWT token to validate
     * @return Validated DecodedJWT if valid, null if invalid
     */
    public DecodedJWT validateToken(String token) {
        try {
            // First decode to get the key ID (kid) from header
            DecodedJWT jwt = JWT.decode(token);
            
            // Get the public key from Cognito's JWKS endpoint using the kid
            Jwk jwk = jwkProvider.get(jwt.getKeyId());
            RSAPublicKey publicKey = (RSAPublicKey) jwk.getPublicKey();
            
            // Create verifier with the public key
            Algorithm algorithm = Algorithm.RSA256(publicKey, null);
            JWTVerifier verifier = JWT.require(algorithm)
                .withIssuer(String.format("https://cognito-idp.%s.amazonaws.com/%s", region, userPoolId))
                .build();
            
            // Verify the token signature and claims
            DecodedJWT verifiedJwt = verifier.verify(token);
            
            logger.debug("JWT token validated successfully for subject: {}", verifiedJwt.getSubject());
            return verifiedJwt;
            
        } catch (Exception e) {
            logger.warn("JWT token validation failed: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Extract the user ID (sub claim) from a validated token
     * @param decodedJWT The validated JWT token
     * @return The user ID (sub claim) or null if not present
     */
    public String getUserId(DecodedJWT decodedJWT) {
        if (decodedJWT == null) {
            return null;
        }
        
        String sub = decodedJWT.getSubject();
        if (sub == null || sub.isEmpty()) {
            logger.warn("JWT token missing 'sub' claim");
            return null;
        }
        
        return sub;
    }
}
