package com.cpsc.backend.service;

import com.cpsc.backend.entity.Institution;
import com.cpsc.backend.entity.Transaction;
import com.cpsc.backend.exception.InvalidInstitutionDataException;
import com.cpsc.backend.model.CreateInstitutionRequest;
import com.cpsc.backend.model.GetInstitutions200Response;
import com.cpsc.backend.model.InstitutionResponse;
import com.cpsc.backend.repository.InstitutionRepository;
import com.cpsc.backend.repository.TransactionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;

import java.time.Instant;
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
    private final TransactionRepository transactionRepository;
    private final GoalService goalService;
    private final ObjectMapper objectMapper;

    public InstitutionService(InstitutionRepository institutionRepository, 
                             TransactionRepository transactionRepository,
                             GoalService goalService) {
        this.institutionRepository = institutionRepository;
        this.transactionRepository = transactionRepository;
        this.goalService = goalService;
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
            institution.setCurrentBalance(request.getStartingBalance().doubleValue());
            institution.setCreatedAt(Instant.now().getEpochSecond());
            institution.setAllocatedPercent(0);

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

    /**
     * Edit an institution for a user
     */
    public InstitutionResponse editInstitution(String userId, String institutionId, 
            com.cpsc.backend.model.EditInstitutionRequest request) {
        if (userId == null || userId.trim().isEmpty()) {
            throw new IllegalArgumentException("User ID cannot be null or empty");
        }
        
        if (institutionId == null || institutionId.trim().isEmpty()) {
            throw new IllegalArgumentException("Institution ID cannot be null or empty");
        }
        
        try {
            logger.info("Editing institution {} for user {}", institutionId, userId);
            
            // First verify the institution exists and belongs to the user
            Institution institution = institutionRepository.findByUserIdAndInstitutionId(userId, institutionId);
            
            if (institution == null) {
                throw new com.cpsc.backend.exception.InstitutionNotFoundException(
                    "Institution not found with ID: " + institutionId);
            }
            
            boolean updated = false;
            
            // Update institution name if provided
            if (request.getInstitutionName() != null) {
                String newName = request.getInstitutionName().trim();
                
                if (newName.isEmpty()) {
                    throw new InvalidInstitutionDataException("Institution name cannot be empty");
                }
                
                if (newName.length() > MAX_INSTITUTION_NAME_LENGTH) {
                    throw new InvalidInstitutionDataException(
                        "Institution name cannot exceed " + MAX_INSTITUTION_NAME_LENGTH + " characters");
                }
                
                logger.debug("Updating institution name from '{}' to '{}'", 
                    institution.getInstitutionName(), newName);
                institution.setInstitutionName(newName);
                updated = true;
            }
            
            // Update starting balance if provided
            if (request.getStartingBalance() != null) {
                double newStartingBalance = request.getStartingBalance();
                
                // Validate the new starting balance
                if (newStartingBalance < 0) {
                    throw new InvalidInstitutionDataException("Starting balance cannot be negative");
                }
                
                if (newStartingBalance > MAX_STARTING_BALANCE) {
                    throw new InvalidInstitutionDataException(
                        "Starting balance cannot exceed " + MAX_STARTING_BALANCE);
                }
                
                if (Double.isNaN(newStartingBalance) || Double.isInfinite(newStartingBalance)) {
                    throw new InvalidInstitutionDataException("Starting balance must be a valid number");
                }
                
                // Calculate the difference and adjust current balance
                double oldStartingBalance = institution.getStartingBalance();
                double difference = newStartingBalance - oldStartingBalance;
                
                logger.debug("Updating starting balance from {} to {} (difference: {})", 
                    oldStartingBalance, newStartingBalance, difference);
                
                institution.setStartingBalance(newStartingBalance);
                
                // Adjust current balance by the same difference
                Double currentBalance = institution.getCurrentBalance();
                if (currentBalance == null) {
                    currentBalance = oldStartingBalance;
                }
                double newCurrentBalance = currentBalance + difference;
                
                logger.debug("Adjusting current balance from {} to {}", 
                    currentBalance, newCurrentBalance);
                
                institution.setCurrentBalance(newCurrentBalance);
                updated = true;
            }
            
            // Update allocated percent if provided
            if (request.getAllocatedPercent() != null) {
                int newAllocatedPercent = request.getAllocatedPercent();
                
                if (newAllocatedPercent < 0 || newAllocatedPercent > 100) {
                    throw new InvalidInstitutionDataException("Allocated percent must be between 0 and 100");
                }
                
                logger.debug("Updating allocated percent from {} to {}", 
                    institution.getAllocatedPercent(), newAllocatedPercent);
                
                institution.setAllocatedPercent(newAllocatedPercent);
                updated = true;
            }
            
            if (!updated) {
                logger.warn("Edit request for institution {} had no changes", institutionId);
            } else {
                institutionRepository.save(institution);
                logger.info("Successfully edited institution {} for user {}", institutionId, userId);
                
                // Update goal completion status for all linked goals
                goalService.updateGoalCompletionForInstitution(userId, institutionId, institution);
            }
            
            return mapToResponse(institution);
            
        } catch (InvalidInstitutionDataException e) {
            throw e; // Re-throw validation exceptions
        } catch (com.cpsc.backend.exception.InstitutionNotFoundException e) {
            throw e; // Re-throw not found exceptions
        } catch (DynamoDbException e) {
            logger.error("DynamoDB error while editing institution {} for user {}: {}", 
                institutionId, userId, e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error while editing institution {} for user {}: {}", 
                institutionId, userId, e.getMessage(), e);
            throw new RuntimeException("Failed to edit institution", e);
        }
    }
    
    /**
     * Delete an institution for a user
     * This will cascade delete all transactions associated with the institution
     * and remove the institution from all linked goals
     */
    public void deleteInstitution(String userId, String institutionId) {
        if (userId == null || userId.trim().isEmpty()) {
            throw new IllegalArgumentException("User ID cannot be null or empty");
        }
        
        if (institutionId == null || institutionId.trim().isEmpty()) {
            throw new IllegalArgumentException("Institution ID cannot be null or empty");
        }
        
        try {
            logger.info("Deleting institution {} for user {}", institutionId, userId);
            
            // First verify the institution exists and belongs to the user
            Institution institution = institutionRepository.findByUserIdAndInstitutionId(userId, institutionId);
            
            if (institution == null) {
                throw new com.cpsc.backend.exception.InstitutionNotFoundException(
                    "Institution not found with ID: " + institutionId);
            }
            
            // Remove institution from all linked goals
            if (institution.getLinkedGoals() != null && !institution.getLinkedGoals().isEmpty()) {
                logger.info("Removing institution {} from {} linked goals", 
                    institutionId, institution.getLinkedGoals().size());
                goalService.removeInstitutionFromGoals(userId, institutionId, institution);
            }
            
            // Bulk delete all transactions associated with this institution
            transactionRepository.deleteAllByInstitutionId(institutionId);
            
            // Then delete the institution itself
            institutionRepository.delete(userId, institutionId);
            
            logger.info("Successfully deleted institution {} and all associated transactions for user {}", 
                institutionId, userId);
            
        } catch (DynamoDbException e) {
            logger.error("DynamoDB error while deleting institution {} for user {}: {}", 
                institutionId, userId, e.getMessage(), e);
            throw e; // Re-throw to be handled by GlobalExceptionHandler
        } catch (Exception e) {
            logger.error("Unexpected error while deleting institution {} for user {}: {}", 
                institutionId, userId, e.getMessage(), e);
            throw new RuntimeException("Failed to delete institution", e);
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
            response.setCurrentBalance(institution.getCurrentBalance());
            response.setUserId(UUID.fromString(institution.getUserId()));
            response.setCreatedAt(institution.getCreatedAt());
            response.setAllocatedPercent(institution.getAllocatedPercent() != null ? institution.getAllocatedPercent() : 0);
            
            // Map linkedGoals to list of UUIDs
            if (institution.getLinkedGoals() != null && !institution.getLinkedGoals().isEmpty()) {
                List<UUID> linkedGoalsUUIDs = institution.getLinkedGoals().stream()
                    .map(UUID::fromString)
                    .collect(Collectors.toList());
                response.setLinkedGoals(linkedGoalsUUIDs);
            }
            
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

    /**
     * Delete all institutions for a user (used during account deletion)
     * This will cascade delete all associated transactions
     * NOTE: All goals should be deleted BEFORE calling this method to maintain referential integrity
     */
    public void deleteAllUserInstitutions(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            throw new IllegalArgumentException("User ID cannot be null or empty");
        }
        
        try {
            logger.info("Deleting all institutions for user {}", userId);
            
            List<Institution> institutions = institutionRepository.findAllByUserId(userId);
            
            logger.info("Found {} institutions to delete for user {}", institutions.size(), userId);
            
            // Don't reuse deleteInstitution() because:
            // 1. All goals are already deleted (no need to update them)
            // 2. We can bulk delete more efficiently
            for (Institution institution : institutions) {
                // Bulk delete all transactions associated with this institution
                transactionRepository.deleteAllByInstitutionId(institution.getInstitutionId());
                
                // Delete the institution itself
                institutionRepository.delete(userId, institution.getInstitutionId());
            }
            
            logger.info("Successfully deleted all {} institutions and their transactions for user {}", 
                institutions.size(), userId);
            
        } catch (Exception e) {
            logger.error("Error while deleting all institutions for user {}: {}", 
                userId, e.getMessage(), e);
            throw new RuntimeException("Failed to delete all user institutions", e);
        }
    }
}
