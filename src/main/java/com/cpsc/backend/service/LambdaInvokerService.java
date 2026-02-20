package com.cpsc.backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.LambdaClientBuilder;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;
import software.amazon.awssdk.services.lambda.model.InvokeResponse;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Service for invoking AWS Lambda functions.
 *
 * <p>Supports local development mode via {@code lambda.endpoint-url} property.
 * When non-empty, that URL is used as the endpoint override (e.g. {@code http://localhost:9001}).
 * Leave the property empty (or unset) to use standard AWS Lambda invocation.</p>
 */
@Service
public class LambdaInvokerService {

    private static final Logger logger = LoggerFactory.getLogger(LambdaInvokerService.class);

    private final LambdaClient lambdaClient;
    private final ObjectMapper objectMapper;

    public LambdaInvokerService(
            @Value("${lambda.endpoint-url:}") String endpointUrl,
            @Value("${aws.secretsmanager.region:us-east-1}") String region) {

        LambdaClientBuilder builder = LambdaClient.builder()
                .region(Region.of(region));

        if (endpointUrl != null && !endpointUrl.isBlank()) {
            logger.info("Lambda client configured with local endpoint override: {}", endpointUrl);
            builder.endpointOverride(URI.create(endpointUrl));
        } else {
            logger.info("Lambda client configured for AWS (region: {})", region);
        }

        this.lambdaClient = builder.build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Invoke a Lambda function synchronously with a structured event payload.
     *
     * @param functionName the Lambda function name or ARN
     * @param event        the event map to serialize as JSON and send to the function
     * @return the parsed Lambda proxy response as a map
     * @throws LambdaInvocationException if the function returns a non-2xx statusCode or throws
     */
    public Map<String, Object> invoke(String functionName, Map<String, Object> event) {
        try {
            String eventJson = objectMapper.writeValueAsString(event);
            logger.debug("Invoking Lambda function '{}' with event: {}", functionName, eventJson);

            InvokeRequest request = InvokeRequest.builder()
                    .functionName(functionName)
                    .payload(SdkBytes.fromString(eventJson, StandardCharsets.UTF_8))
                    .build();

            InvokeResponse response = lambdaClient.invoke(request);

            // Check for Lambda-level errors (function threw an exception)
            String functionError = response.functionError();
            if (functionError != null && !functionError.isBlank()) {
                String errorBody = response.payload().asUtf8String();
                logger.error("Lambda function '{}' returned error '{}': {}", functionName, functionError, errorBody);
                throw new LambdaInvocationException("Lambda function error: " + functionError + " — " + errorBody, 500);
            }

            String responsePayload = response.payload().asUtf8String();
            logger.debug("Lambda function '{}' returned: {}", functionName, responsePayload);

            // Parse the Lambda proxy response envelope
            Map<String, Object> proxyResponse = objectMapper.readValue(
                    responsePayload, new TypeReference<Map<String, Object>>() {});

            Integer statusCode = (Integer) proxyResponse.getOrDefault("statusCode", 200);
            if (statusCode < 200 || statusCode >= 300) {
                Object body = proxyResponse.get("body");
                throw new LambdaInvocationException(
                        "Lambda returned HTTP " + statusCode + ": " + body, statusCode);
            }

            return proxyResponse;

        } catch (LambdaInvocationException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Failed to invoke Lambda function '{}'", functionName, e);
            throw new LambdaInvocationException("Failed to invoke Lambda: " + e.getMessage(), 500);
        }
    }

    /**
     * Parse the {@code body} field of a Lambda proxy response as a typed map.
     *
     * @param proxyResponse the full proxy response returned by {@link #invoke}
     * @return the parsed body as a {@code Map<String, Object>}
     */
    public Map<String, Object> parseBody(Map<String, Object> proxyResponse) {
        try {
            Object body = proxyResponse.get("body");
            if (body instanceof String) {
                return objectMapper.readValue((String) body, new TypeReference<Map<String, Object>>() {});
            } else if (body instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) body;
                return map;
            }
            throw new LambdaInvocationException("Unexpected body type in Lambda response: "
                    + (body == null ? "null" : body.getClass().getName()), 500);
        } catch (LambdaInvocationException e) {
            throw e;
        } catch (Exception e) {
            throw new LambdaInvocationException("Failed to parse Lambda response body: " + e.getMessage(), 500);
        }
    }

    // -------------------------------------------------------------------------
    // Exception
    // -------------------------------------------------------------------------

    /**
     * Runtime exception thrown when a Lambda invocation fails or returns an error.
     */
    public static class LambdaInvocationException extends RuntimeException {

        private final int httpStatus;

        public LambdaInvocationException(String message, int httpStatus) {
            super(message);
            this.httpStatus = httpStatus;
        }

        public int getHttpStatus() {
            return httpStatus;
        }
    }
}
