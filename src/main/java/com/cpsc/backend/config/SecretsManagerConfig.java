package com.cpsc.backend.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;

import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;

@Configuration
public class SecretsManagerConfig {

    @Value("${aws.secretsmanager.secret-name}")
    private String secretName;

    @Value("${aws.secretsmanager.region}")
    private String region;

    private final Map<String, String> secretsCache = new HashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct
    public void init() {
        loadSecrets();
    }

    private void loadSecrets() {
        try (SecretsManagerClient client = SecretsManagerClient.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build()) {

            GetSecretValueRequest request = GetSecretValueRequest.builder()
                    .secretId(secretName)
                    .build();

            GetSecretValueResponse response = client.getSecretValue(request);
            String secretString = response.secretString();

            // Parse JSON secret
            JsonNode secretJson = objectMapper.readTree(secretString);
            
            // Cache all secret values
            secretJson.fields().forEachRemaining(entry -> 
                secretsCache.put(entry.getKey(), entry.getValue().asText())
            );

        } catch (Exception e) {
            throw new RuntimeException("Failed to load secrets from AWS Secrets Manager: " + e.getMessage(), e);
        }
    }

    public String getSecret(String key) {
        String value = secretsCache.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Secret key '" + key + "' not found in Secrets Manager");
        }
        return value;
    }

    public String getCognitoUserPoolId() {
        return getSecret("COGNITO_USER_POOL_ID");
    }

    public String getCognitoClientId() {
        return getSecret("COGNITO_CLIENT_ID");
    }

    public String getCognitoClientSecret() {
        return getSecret("COGNITO_CLIENT_SECRET");
    }

    public String getCognitoRegion() {
        return getSecret("COGNITO_REGION");
    }
}
