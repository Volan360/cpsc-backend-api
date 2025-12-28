package com.cpsc.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;

@Configuration
public class CognitoConfig {

    private final SecretsManagerConfig secretsManagerConfig;

    public CognitoConfig(SecretsManagerConfig secretsManagerConfig) {
        this.secretsManagerConfig = secretsManagerConfig;
    }

    @Bean
    public CognitoIdentityProviderClient cognitoClient() {
        String region = secretsManagerConfig.getCognitoRegion();
        return CognitoIdentityProviderClient.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }
    
    public String getUserPoolId() {
        return secretsManagerConfig.getCognitoUserPoolId();
    }
    
    public String getRegion() {
        return secretsManagerConfig.getCognitoRegion();
    }
}
