package com.cpsc.backend.service;

import com.cpsc.backend.entity.Goal;
import com.cpsc.backend.entity.Institution;
import com.cpsc.backend.exception.InstitutionNotFoundException;
import com.cpsc.backend.exception.InvalidInstitutionDataException;
import com.cpsc.backend.model.CreateGoalRequest;
import com.cpsc.backend.model.GoalResponse;
import com.cpsc.backend.repository.GoalRepository;
import com.cpsc.backend.repository.InstitutionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class GoalService {

    private static final Logger logger = LoggerFactory.getLogger(GoalService.class);
    private static final int MAX_GOAL_NAME_LENGTH = 100;
    private static final int MAX_GOAL_DESCRIPTION_LENGTH = 500;
    
    private final GoalRepository goalRepository;
    private final InstitutionRepository institutionRepository;

    public GoalService(GoalRepository goalRepository, InstitutionRepository institutionRepository) {
        this.goalRepository = goalRepository;
        this.institutionRepository = institutionRepository;
    }

    public GoalResponse createGoal(String userId, CreateGoalRequest request) {
        if (userId == null || userId.trim().isEmpty()) {
            throw new IllegalArgumentException("User ID cannot be null or empty");
        }
        
        // Validate input
        validateGoalRequest(request);
        
        try {
            // Validate that all institutions belong to the user and have sufficient allocation
            Map<String, Integer> linkedInstitutions = request.getLinkedInstitutions();
            Map<String, Institution> institutionMap = validateAndGetInstitutions(userId, linkedInstitutions);
            
            // Update all affected institutions with new allocations
            for (Map.Entry<String, Integer> entry : linkedInstitutions.entrySet()) {
                String institutionId = entry.getKey();
                Integer allocationPercent = entry.getValue();
                
                Institution institution = institutionMap.get(institutionId);
                Integer currentAllocation = institution.getAllocatedPercent() != null ? institution.getAllocatedPercent() : 0;
                institution.setAllocatedPercent(currentAllocation + allocationPercent);
                
                institutionRepository.save(institution);
                
                logger.debug("Updated institution {} allocation from {} to {}", 
                    institutionId, currentAllocation, institution.getAllocatedPercent());
            }
            
            // Create the goal
            Goal goal = new Goal();
            goal.setUserId(userId);
            goal.setGoalId(UUID.randomUUID().toString());
            goal.setName(request.getName().trim());
            goal.setDescription(request.getDescription() != null ? request.getDescription().trim() : null);
            goal.setLinkedInstitutions(new HashMap<>(linkedInstitutions));
            goal.setCreatedAt(Instant.now().getEpochSecond());

            logger.info("Creating goal '{}' for user {} with {} linked institutions", 
                goal.getName(), userId, linkedInstitutions.size());
            
            goalRepository.save(goal);
            
            logger.info("Successfully created goal {} for user {}", goal.getGoalId(), userId);

            return mapToResponse(goal);
            
        } catch (InstitutionNotFoundException | InvalidInstitutionDataException e) {
            // Business logic exceptions - re-throw directly
            throw e;
        } catch (DynamoDbException e) {
            logger.error("DynamoDB error while creating goal for user {}: {}", 
                userId, e.getMessage(), e);
            throw e; // Re-throw to be handled by GlobalExceptionHandler
        } catch (Exception e) {
            logger.error("Unexpected error while creating goal for user {}: {}", 
                userId, e.getMessage(), e);
            throw new RuntimeException("Failed to create goal", e);
        }
    }

    public List<GoalResponse> getUserGoals(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            throw new IllegalArgumentException("User ID cannot be null or empty");
        }
        
        try {
            logger.debug("Fetching goals for user {}", userId);
            
            List<Goal> goals = goalRepository.findAllByUserId(userId);
            
            logger.info("Found {} goals for user {}", goals.size(), userId);
            
            return goals.stream()
                    .map(this::mapToResponse)
                    .collect(Collectors.toList());
                    
        } catch (DynamoDbException e) {
            logger.error("DynamoDB error while fetching goals for user {}: {}", 
                userId, e.getMessage(), e);
            throw e; // Re-throw to be handled by GlobalExceptionHandler
        } catch (Exception e) {
            logger.error("Unexpected error while fetching goals for user {}: {}", 
                userId, e.getMessage(), e);
            throw new RuntimeException("Failed to fetch goals", e);
        }
    }

    private void validateGoalRequest(CreateGoalRequest request) {
        // Validate name
        if (request.getName() == null || request.getName().trim().isEmpty()) {
            throw new InvalidInstitutionDataException("Goal name cannot be empty");
        }
        
        if (request.getName().length() > MAX_GOAL_NAME_LENGTH) {
            throw new InvalidInstitutionDataException(
                "Goal name cannot exceed " + MAX_GOAL_NAME_LENGTH + " characters");
        }
        
        // Validate description if provided
        if (request.getDescription() != null && request.getDescription().length() > MAX_GOAL_DESCRIPTION_LENGTH) {
            throw new InvalidInstitutionDataException(
                "Goal description cannot exceed " + MAX_GOAL_DESCRIPTION_LENGTH + " characters");
        }
        
        // Validate linked institutions
        if (request.getLinkedInstitutions() == null || request.getLinkedInstitutions().isEmpty()) {
            throw new InvalidInstitutionDataException("At least one linked institution is required");
        }
        
        // Validate percentages
        for (Map.Entry<String, Integer> entry : request.getLinkedInstitutions().entrySet()) {
            Integer percent = entry.getValue();
            if (percent == null || percent < 0 || percent > 100) {
                throw new InvalidInstitutionDataException(
                    "Allocation percentage must be between 0 and 100 for institution " + entry.getKey());
            }
        }
    }

    private Map<String, Institution> validateAndGetInstitutions(String userId, Map<String, Integer> linkedInstitutions) {
        Map<String, Institution> institutionMap = new HashMap<>();
        
        for (Map.Entry<String, Integer> entry : linkedInstitutions.entrySet()) {
            String institutionId = entry.getKey();
            Integer requestedAllocation = entry.getValue();
            
            // Fetch the institution
            Institution institution = institutionRepository.findByUserIdAndInstitutionId(userId, institutionId);
            
            if (institution == null) {
                throw new com.cpsc.backend.exception.InstitutionNotFoundException(
                    "Institution not found or does not belong to user: " + institutionId);
            }
            
            // Check if the institution has enough remaining allocation
            Integer currentAllocation = institution.getAllocatedPercent() != null ? institution.getAllocatedPercent() : 0;
            int newTotalAllocation = currentAllocation + requestedAllocation;
            
            if (newTotalAllocation > 100) {
                throw new InvalidInstitutionDataException(
                    String.format("Institution '%s' has insufficient allocation. Current: %d%%, Requested: %d%%, Total would be: %d%% (max 100%%)",
                        institution.getInstitutionName(), currentAllocation, requestedAllocation, newTotalAllocation));
            }
            
            institutionMap.put(institutionId, institution);
            
            logger.debug("Validated institution {}: current allocation {}%, requested {}%, new total {}%", 
                institutionId, currentAllocation, requestedAllocation, newTotalAllocation);
        }
        
        return institutionMap;
    }

    private GoalResponse mapToResponse(Goal goal) {
        try {
            GoalResponse response = new GoalResponse();
            response.setGoalId(UUID.fromString(goal.getGoalId()));
            response.setName(goal.getName());
            response.setDescription(goal.getDescription());
            response.setLinkedInstitutions(goal.getLinkedInstitutions());
            response.setUserId(UUID.fromString(goal.getUserId()));
            response.setCreatedAt(goal.getCreatedAt());
            return response;
        } catch (IllegalArgumentException e) {
            logger.error("Invalid UUID format in goal data: goalId={}, userId={}", 
                goal.getGoalId(), goal.getUserId(), e);
            throw new RuntimeException("Invalid goal data format", e);
        }
    }
}
