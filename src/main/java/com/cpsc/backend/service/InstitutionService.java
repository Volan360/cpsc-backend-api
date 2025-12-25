package com.cpsc.backend.service;

import com.cpsc.backend.entity.Institution;
import com.cpsc.backend.exception.InvalidInstitutionDataException;
import com.cpsc.backend.model.CreateInstitutionRequest;
import com.cpsc.backend.model.GetInstitutions200Response;
import com.cpsc.backend.model.InstitutionResponse;
import com.cpsc.backend.repository.InstitutionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class InstitutionService {

    private static final Logger logger = LoggerFactory.getLogger(InstitutionService.class);
    private static final int MAX_INSTITUTION_NAME_LENGTH = 100;
    private static final double MAX_STARTING_BALANCE = 1_000_000_000.0; // 1 billion
    private static final int DEFAULT_PAGE_SIZE = 50;
    
    private final InstitutionRepository institutionRepository;
    private final ObjectMapper objectMapper;

    public InstitutionService(InstitutionRepository institutionRepository) {
        this.institutionRepository = institutionRepository;
        this.objectMapper = new ObjectMapper();
    }

    public InstitutionResponse createInstitution(String userId, CreateInstitutionRequest request) {
        if (userId == null || userId.trim().isEmpty()) {
            throw new IllegalArgumentException("User ID cannot be null or empty");
        }
        
        // Validate input
        validateInstitutionRequest(request);
        
        try {
            Institution institution = new Institution();
            institution.setUserId(userId);
            institution.setInstitutionId(UUID.randomUUID().toString());
            institution.setInstitutionName(request.getInstitutionName().trim());
            institution.setStartingBalance(request.getStartingBalance().doubleValue());
            institution.setCreatedAt(Instant.now());

            logger.info("Creating institution '{}' for user {} with starting balance {}", 
                institution.getInstitutionName(), userId, institution.getStartingBalance());
            
            institutionRepository.save(institution);
            
            logger.info("Successfully created institution {} for user {}", 
                institution.getInstitutionId(), userId);

            return mapToResponse(institution);
            
        } catch (DynamoDbException e) {
            logger.error("DynamoDB error while creating institution for user {}: {}", 
                userId, e.getMessage(), e);
            throw e; // Re-throw to be handled by GlobalExceptionHandler
        } catch (Exception e) {
            logger.error("Unexpected error while creating institution for user {}: {}", 
                userId, e.getMessage(), e);
            throw new RuntimeException("Failed to create institution", e);
        }
    }

    public List<InstitutionResponse> getUserInstitutions(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            throw new IllegalArgumentException("User ID cannot be null or empty");
        }
        
        try {
            logger.debug("Fetching institutions for user {}", userId);
            
            List<Institution> institutions = institutionRepository.findAllByUserId(userId);
            
            logger.info("Found {} institutions for user {}", institutions.size(), userId);
            
            return institutions.stream()
                    .map(this::mapToResponse)
                    .collect(Collectors.toList());
                    
        } catch (DynamoDbException e) {
            logger.error("DynamoDB error while fetching institutions for user {}: {}", 
                userId, e.getMessage(), e);
            throw e; // Re-throw to be handled by GlobalExceptionHandler
        } catch (Exception e) {
            logger.error("Unexpected error while fetching institutions for user {}: {}", 
                userId, e.getMessage(), e);
            throw new RuntimeException("Failed to fetch institutions", e);
        }
    }
    
    /**
     * Get paginated institutions for a user
     */
    public GetInstitutions200Response getUserInstitutionsPaginated(String userId, Integer limit, String lastEvaluatedKeyToken) {
        if (userId == null || userId.trim().isEmpty()) {
            throw new IllegalArgumentException("User ID cannot be null or empty");
        }
        
        int pageSize = (limit != null && limit > 0) ? Math.min(limit, 100) : DEFAULT_PAGE_SIZE;
        
        try {
            logger.debug("Fetching paginated institutions for user {} with limit={}", userId, pageSize);
            
            Map<String, AttributeValue> lastEvaluatedKey = decodeToken(lastEvaluatedKeyToken);
            
            InstitutionRepository.PaginatedResult<Institution> result = 
                institutionRepository.findAllByUserIdPaginated(userId, pageSize, lastEvaluatedKey);
            
            List<InstitutionResponse> institutions = result.getItems().stream()
                    .map(this::mapToResponse)
                    .collect(Collectors.toList());
            
            String nextToken = result.hasMore() ? encodeToken(result.getLastEvaluatedKey()) : null;
            
            logger.info("Found {} institutions for user {}, hasMore={}", 
                institutions.size(), userId, result.hasMore());
            
            GetInstitutions200Response response = new GetInstitutions200Response();
            response.setInstitutions(institutions);
            response.setNextToken(nextToken);
            
            return response;
                    
        } catch (DynamoDbException e) {
            logger.error("DynamoDB error while fetching paginated institutions for user {}: {}", 
                userId, e.getMessage(), e);
            throw e; // Re-throw to be handled by GlobalExceptionHandler
        } catch (Exception e) {
            logger.error("Unexpected error while fetching paginated institutions for user {}: {}", 
                userId, e.getMessage(), e);
            throw new RuntimeException("Failed to fetch institutions", e);
        }
    }
    
    private void validateInstitutionRequest(CreateInstitutionRequest request) {
        if (request.getInstitutionName() == null || request.getInstitutionName().trim().isEmpty()) {
            throw new InvalidInstitutionDataException("Institution name cannot be empty");
        }
        
        if (request.getInstitutionName().trim().length() > MAX_INSTITUTION_NAME_LENGTH) {
            throw new InvalidInstitutionDataException(
                "Institution name cannot exceed " + MAX_INSTITUTION_NAME_LENGTH + " characters");
        }
        
        if (request.getStartingBalance() == null) {
            throw new InvalidInstitutionDataException("Starting balance cannot be null");
        }
        
        double balance = request.getStartingBalance().doubleValue();
        
        if (balance < 0) {
            throw new InvalidInstitutionDataException("Starting balance cannot be negative");
        }
        
        if (balance > MAX_STARTING_BALANCE) {
            throw new InvalidInstitutionDataException(
                "Starting balance cannot exceed " + MAX_STARTING_BALANCE);
        }
        
        if (Double.isNaN(balance) || Double.isInfinite(balance)) {
            throw new InvalidInstitutionDataException("Starting balance must be a valid number");
        }
    }

    private InstitutionResponse mapToResponse(Institution institution) {
        try {
            InstitutionResponse response = new InstitutionResponse();
            response.setInstitutionId(UUID.fromString(institution.getInstitutionId()));
            response.setInstitutionName(institution.getInstitutionName());
            response.setStartingBalance(institution.getStartingBalance());
            response.setUserId(UUID.fromString(institution.getUserId()));
            response.setCreatedAt(OffsetDateTime.ofInstant(institution.getCreatedAt(), ZoneOffset.UTC));
            return response;
        } catch (IllegalArgumentException e) {
            logger.error("Invalid UUID format in institution data: institutionId={}, userId={}", 
                institution.getInstitutionId(), institution.getUserId(), e);
            throw new RuntimeException("Invalid institution data format", e);
        }
    }
    
    /**
     * Encode DynamoDB lastEvaluatedKey to a Base64 token
     */
    private String encodeToken(Map<String, AttributeValue> lastEvaluatedKey) {
        if (lastEvaluatedKey == null || lastEvaluatedKey.isEmpty()) {
            return null;
        }
        
        try {
            // Convert AttributeValue map to simple map for JSON serialization
            Map<String, Object> simpleMap = new HashMap<>();
            lastEvaluatedKey.forEach((key, value) -> {
                if (value.s() != null) {
                    simpleMap.put(key, value.s());
                } else if (value.n() != null) {
                    simpleMap.put(key, value.n());
                }
            });
            
            String json = objectMapper.writeValueAsString(simpleMap);
            return Base64.getUrlEncoder().encodeToString(json.getBytes());
        } catch (JsonProcessingException e) {
            logger.error("Failed to encode pagination token", e);
            throw new RuntimeException("Failed to encode pagination token", e);
        }
    }
    
    /**
     * Decode Base64 token back to DynamoDB lastEvaluatedKey
     */
    private Map<String, AttributeValue> decodeToken(String token) {
        if (token == null || token.trim().isEmpty()) {
            return null;
        }
        
        try {
            byte[] decodedBytes = Base64.getUrlDecoder().decode(token);
            String json = new String(decodedBytes);
            
            @SuppressWarnings("unchecked")
            Map<String, Object> simpleMap = objectMapper.readValue(json, Map.class);
            
            Map<String, AttributeValue> lastEvaluatedKey = new HashMap<>();
            simpleMap.forEach((key, value) -> {
                if (value instanceof String) {
                    lastEvaluatedKey.put(key, AttributeValue.builder().s((String) value).build());
                } else if (value instanceof Number) {
                    lastEvaluatedKey.put(key, AttributeValue.builder().n(value.toString()).build());
                }
            });
            
            return lastEvaluatedKey;
        } catch (Exception e) {
            logger.warn("Failed to decode pagination token: {}", e.getMessage());
            throw new InvalidInstitutionDataException("Invalid pagination token");
        }
    }
}
