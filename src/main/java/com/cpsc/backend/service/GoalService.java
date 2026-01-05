package com.cpsc.backend.service;

import com.cpsc.backend.entity.Goal;
import com.cpsc.backend.entity.Institution;
import com.cpsc.backend.exception.InstitutionNotFoundException;
import com.cpsc.backend.exception.InvalidInstitutionDataException;
import com.cpsc.backend.model.CreateGoalRequest;
import com.cpsc.backend.model.EditGoalRequest;
import com.cpsc.backend.model.GoalResponse;
import com.cpsc.backend.repository.GoalRepository;
import com.cpsc.backend.repository.InstitutionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;

import java.time.Instant;
import java.util.ArrayList;
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
            
            // Create the goal first to get the goalId
            Goal goal = new Goal();
            goal.setUserId(userId);
            goal.setGoalId(UUID.randomUUID().toString());
            goal.setName(request.getName().trim());
            goal.setDescription(request.getDescription() != null ? request.getDescription().trim() : null);
            goal.setTargetAmount(request.getTargetAmount());
            goal.setLinkedInstitutions(new HashMap<>(linkedInstitutions));
            goal.setCreatedAt(Instant.now().getEpochSecond());
            
            // Calculate if goal is completed based on allocated amounts
            double totalAllocatedAmount = 0.0;
            for (Map.Entry<String, Integer> entry : linkedInstitutions.entrySet()) {
                String institutionId = entry.getKey();
                Integer allocationPercent = entry.getValue();
                Institution institution = institutionMap.get(institutionId);
                
                double institutionCurrentBalance = institution.getCurrentBalance() != null ? institution.getCurrentBalance() : 0.0;
                double allocatedAmount = (institutionCurrentBalance * allocationPercent) / 100.0;
                totalAllocatedAmount += allocatedAmount;
            }
            goal.setIsCompleted(totalAllocatedAmount >= request.getTargetAmount());
            
            // Update all affected institutions with new allocations and add goal to linkedGoals
            for (Map.Entry<String, Integer> entry : linkedInstitutions.entrySet()) {
                String institutionId = entry.getKey();
                Integer allocationPercent = entry.getValue();
                
                Institution institution = institutionMap.get(institutionId);
                Integer currentAllocation = institution.getAllocatedPercent() != null ? institution.getAllocatedPercent() : 0;
                institution.setAllocatedPercent(currentAllocation + allocationPercent);
                
                // Add this goal to the institution's linkedGoals list
                List<String> linkedGoalsList = institution.getLinkedGoals();
                if (linkedGoalsList == null) {
                    linkedGoalsList = new ArrayList<>();
                }
                linkedGoalsList.add(goal.getGoalId());
                institution.setLinkedGoals(linkedGoalsList);
                
                institutionRepository.save(institution);
                
                logger.debug("Updated institution {} allocation from {} to {}, added goal {}", 
                    institutionId, currentAllocation, institution.getAllocatedPercent(), goal.getGoalId());
            }

            logger.info("Creating goal '{}' for user {} with {} linked institutions, target: {}, completed: {}", 
                goal.getName(), userId, linkedInstitutions.size(), goal.getTargetAmount(), goal.getIsCompleted());
            
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

    public void deleteGoal(String userId, String goalId) {
        if (userId == null || userId.trim().isEmpty()) {
            throw new IllegalArgumentException("User ID cannot be null or empty");
        }
        
        if (goalId == null || goalId.trim().isEmpty()) {
            throw new IllegalArgumentException("Goal ID cannot be null or empty");
        }
        
        try {
            logger.info("Deleting goal {} for user {}", goalId, userId);
            
            // First verify the goal exists and belongs to the user
            Goal goal = goalRepository.findByUserIdAndGoalId(userId, goalId);
            
            if (goal == null) {
                throw new InstitutionNotFoundException("Goal not found with ID: " + goalId);
            }
            
            // Update all linked institutions to remove allocations and goal references
            if (goal.getLinkedInstitutions() != null && !goal.getLinkedInstitutions().isEmpty()) {
                logger.info("Removing goal {} from {} linked institutions", 
                    goalId, goal.getLinkedInstitutions().size());
                
                for (Map.Entry<String, Integer> entry : goal.getLinkedInstitutions().entrySet()) {
                    String institutionId = entry.getKey();
                    Integer allocatedPercent = entry.getValue();
                    
                    try {
                        Institution institution = institutionRepository.findByUserIdAndInstitutionId(userId, institutionId);
                        
                        if (institution == null) {
                            logger.warn("Institution {} not found for goal {}, skipping", institutionId, goalId);
                            continue;
                        }
                        
                        // Reduce allocated percent
                        Integer currentAllocation = institution.getAllocatedPercent() != null 
                            ? institution.getAllocatedPercent() : 0;
                        institution.setAllocatedPercent(currentAllocation - allocatedPercent);
                        
                        // Remove goal from linkedGoals list
                        if (institution.getLinkedGoals() != null) {
                            institution.getLinkedGoals().remove(goalId);
                        }
                        
                        institutionRepository.save(institution);
                        
                        logger.debug("Removed allocation {}% and goal reference from institution {}", 
                            allocatedPercent, institutionId);
                        
                    } catch (Exception e) {
                        logger.error("Error updating institution {} while deleting goal {}: {}", 
                            institutionId, goalId, e.getMessage(), e);
                        // Continue with other institutions
                    }
                }
            }
            
            // Delete the goal
            goalRepository.delete(userId, goalId);
            
            logger.info("Successfully deleted goal {} and updated all linked institutions for user {}", 
                goalId, userId);
            
        } catch (InstitutionNotFoundException e) {
            throw e; // Re-throw not found exceptions
        } catch (DynamoDbException e) {
            logger.error("DynamoDB error while deleting goal {} for user {}: {}", 
                goalId, userId, e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error while deleting goal {} for user {}: {}", 
                goalId, userId, e.getMessage(), e);
            throw new RuntimeException("Failed to delete goal", e);
        }
    }

    public GoalResponse editGoal(String userId, String goalId, EditGoalRequest request) {
        if (userId == null || userId.trim().isEmpty()) {
            throw new IllegalArgumentException("User ID cannot be null or empty");
        }
        
        if (goalId == null || goalId.trim().isEmpty()) {
            throw new IllegalArgumentException("Goal ID cannot be null or empty");
        }
        
        try {
            logger.info("Editing goal {} for user {}", goalId, userId);
            
            // First verify the goal exists and belongs to the user
            Goal goal = goalRepository.findByUserIdAndGoalId(userId, goalId);
            
            if (goal == null) {
                throw new InstitutionNotFoundException("Goal not found with ID: " + goalId);
            }
            
            boolean updated = false;
            
            // Update name if provided
            if (request.getName() != null) {
                String newName = request.getName().trim();
                
                if (newName.isEmpty()) {
                    throw new InvalidInstitutionDataException("Goal name cannot be empty");
                }
                
                if (newName.length() > MAX_GOAL_NAME_LENGTH) {
                    throw new InvalidInstitutionDataException(
                        "Goal name cannot exceed " + MAX_GOAL_NAME_LENGTH + " characters");
                }
                
                logger.debug("Updating goal name from '{}' to '{}'", goal.getName(), newName);
                goal.setName(newName);
                updated = true;
            }
            
            // Update description if provided
            if (request.getDescription() != null) {
                String newDescription = request.getDescription().trim();
                
                if (newDescription.length() > MAX_GOAL_DESCRIPTION_LENGTH) {
                    throw new InvalidInstitutionDataException(
                        "Goal description cannot exceed " + MAX_GOAL_DESCRIPTION_LENGTH + " characters");
                }
                
                logger.debug("Updating goal description");
                goal.setDescription(newDescription);
                updated = true;
            }
            
            // Update target amount if provided
            if (request.getTargetAmount() != null) {
                if (request.getTargetAmount() <= 0) {
                    throw new InvalidInstitutionDataException("Target amount must be greater than 0");
                }
                
                logger.debug("Updating target amount from {} to {}", 
                    goal.getTargetAmount(), request.getTargetAmount());
                goal.setTargetAmount(request.getTargetAmount());
                updated = true;
            }
            
            // Update linked institutions if provided
            if (request.getLinkedInstitutions() != null) {
                Map<String, Integer> newLinkedInstitutions = request.getLinkedInstitutions();
                
                // Validate percentages
                for (Map.Entry<String, Integer> entry : newLinkedInstitutions.entrySet()) {
                    Integer percent = entry.getValue();
                    if (percent < 0 || percent > 100) {
                        throw new InvalidInstitutionDataException(
                            "Allocation percentage must be between 0 and 100");
                    }
                }
                
                // Validate new institutions and calculate available allocations
                Map<String, Institution> newInstitutionMap = new HashMap<>();
                for (String institutionId : newLinkedInstitutions.keySet()) {
                    Institution institution = institutionRepository.findByUserIdAndInstitutionId(userId, institutionId);
                    
                    if (institution == null) {
                        throw new InstitutionNotFoundException("Institution not found with ID: " + institutionId);
                    }
                    
                    newInstitutionMap.put(institutionId, institution);
                }
                
                // Get old linked institutions
                Map<String, Integer> oldLinkedInstitutions = goal.getLinkedInstitutions() != null 
                    ? goal.getLinkedInstitutions() 
                    : new HashMap<>();
                
                // Remove allocations from old institutions that are no longer linked
                for (Map.Entry<String, Integer> entry : oldLinkedInstitutions.entrySet()) {
                    String institutionId = entry.getKey();
                    Integer oldPercent = entry.getValue();
                    
                    if (!newLinkedInstitutions.containsKey(institutionId)) {
                        // This institution is no longer linked, remove allocation and goal reference
                        Institution institution = institutionRepository.findByUserIdAndInstitutionId(userId, institutionId);
                        if (institution != null) {
                            Integer currentAllocation = institution.getAllocatedPercent() != null 
                                ? institution.getAllocatedPercent() : 0;
                            institution.setAllocatedPercent(currentAllocation - oldPercent);
                            
                            // Remove goal from linkedGoals list
                            if (institution.getLinkedGoals() != null) {
                                institution.getLinkedGoals().remove(goalId);
                            }
                            
                            institutionRepository.save(institution);
                            logger.debug("Removed allocation {} from institution {}", oldPercent, institutionId);
                        }
                    }
                }
                
                // Add/update allocations for new or modified institutions
                for (Map.Entry<String, Integer> entry : newLinkedInstitutions.entrySet()) {
                    String institutionId = entry.getKey();
                    Integer newPercent = entry.getValue();
                    Integer oldPercent = oldLinkedInstitutions.getOrDefault(institutionId, 0);
                    
                    if (!newPercent.equals(oldPercent)) {
                        Institution institution = newInstitutionMap.get(institutionId);
                        Integer currentAllocation = institution.getAllocatedPercent() != null 
                            ? institution.getAllocatedPercent() : 0;
                        
                        // Calculate new allocation (remove old, add new)
                        int netChange = newPercent - oldPercent;
                        int finalAllocation = currentAllocation + netChange;
                        
                        if (finalAllocation < 0 || finalAllocation > 100) {
                            throw new InvalidInstitutionDataException(
                                "Institution " + institutionId + " would have allocation " + finalAllocation + 
                                "% which exceeds the allowed range (0-100%)");
                        }
                        
                        institution.setAllocatedPercent(finalAllocation);
                        
                        // Add goal to linkedGoals if not already present
                        if (institution.getLinkedGoals() == null) {
                            institution.setLinkedGoals(new ArrayList<>());
                        }
                        if (!institution.getLinkedGoals().contains(goalId)) {
                            institution.getLinkedGoals().add(goalId);
                        }
                        
                        institutionRepository.save(institution);
                        logger.debug("Updated institution {} allocation from {} to {}", 
                            institutionId, currentAllocation, finalAllocation);
                    }
                }
                
                // Update goal's linked institutions
                goal.setLinkedInstitutions(new HashMap<>(newLinkedInstitutions));
                updated = true;
                
                // Recalculate completion status
                double totalAllocatedAmount = 0.0;
                for (Map.Entry<String, Integer> entry : newLinkedInstitutions.entrySet()) {
                    String institutionId = entry.getKey();
                    Integer allocationPercent = entry.getValue();
                    Institution institution = newInstitutionMap.get(institutionId);
                    
                    double institutionCurrentBalance = institution.getCurrentBalance() != null 
                        ? institution.getCurrentBalance() : 0.0;
                    double allocatedAmount = (institutionCurrentBalance * allocationPercent) / 100.0;
                    totalAllocatedAmount += allocatedAmount;
                }
                
                Double targetAmount = goal.getTargetAmount();
                goal.setIsCompleted(totalAllocatedAmount >= targetAmount);
                logger.debug("Recalculated completion status: {} (allocated: {}, target: {})", 
                    goal.getIsCompleted(), totalAllocatedAmount, targetAmount);
            }
            
            if (!updated) {
                logger.warn("Edit request for goal {} had no changes", goalId);
            } else {
                goalRepository.save(goal);
                logger.info("Successfully edited goal {} for user {}", goalId, userId);
            }
            
            return mapToResponse(goal);
            
        } catch (InvalidInstitutionDataException | InstitutionNotFoundException e) {
            throw e; // Re-throw validation and not found exceptions
        } catch (DynamoDbException e) {
            logger.error("DynamoDB error while editing goal {} for user {}: {}", 
                goalId, userId, e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error while editing goal {} for user {}: {}", 
                goalId, userId, e.getMessage(), e);
            throw new RuntimeException("Failed to edit goal", e);
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
        
        // Validate target amount
        if (request.getTargetAmount() == null || request.getTargetAmount() <= 0) {
            throw new InvalidInstitutionDataException("Target amount must be greater than 0");
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
            response.setTargetAmount(goal.getTargetAmount());
            response.setIsCompleted(goal.getIsCompleted() != null ? goal.getIsCompleted() : false);
            response.setUserId(UUID.fromString(goal.getUserId()));
            response.setCreatedAt(goal.getCreatedAt());
            return response;
        } catch (IllegalArgumentException e) {
            logger.error("Invalid UUID format in goal data: goalId={}, userId={}", 
                goal.getGoalId(), goal.getUserId(), e);
            throw new RuntimeException("Invalid goal data format", e);
        }
    }
    
    /**
     * Recalculate and update completion status for all goals linked to an institution
     * Called when an institution's current balance changes
     */
    public void updateGoalCompletionForInstitution(String userId, String institutionId, Institution institution) {
        if (institution.getLinkedGoals() == null || institution.getLinkedGoals().isEmpty()) {
            return; // No goals linked to this institution
        }
        
        logger.debug("Updating goal completion status for {} goals linked to institution {}", 
            institution.getLinkedGoals().size(), institutionId);
        
        for (String goalId : institution.getLinkedGoals()) {
            try {
                Goal goal = goalRepository.findByUserIdAndGoalId(userId, goalId);
                if (goal == null) {
                    logger.warn("Goal {} not found for user {}, skipping completion update", goalId, userId);
                    continue;
                }
                
                // Recalculate total allocated amount across all linked institutions
                double totalAllocatedAmount = 0.0;
                for (Map.Entry<String, Integer> entry : goal.getLinkedInstitutions().entrySet()) {
                    String instId = entry.getKey();
                    Integer allocationPercent = entry.getValue();
                    
                    Institution inst;
                    if (instId.equals(institutionId)) {
                        // Use the updated institution passed in
                        inst = institution;
                    } else {
                        // Fetch other institutions
                        inst = institutionRepository.findByUserIdAndInstitutionId(userId, instId);
                        if (inst == null) {
                            logger.warn("Institution {} not found for goal {}, skipping", instId, goalId);
                            continue;
                        }
                    }
                    
                    double institutionCurrentBalance = inst.getCurrentBalance() != null ? inst.getCurrentBalance() : 0.0;
                    double allocatedAmount = (institutionCurrentBalance * allocationPercent) / 100.0;
                    totalAllocatedAmount += allocatedAmount;
                }
                
                // Update completion status
                boolean wasCompleted = goal.getIsCompleted() != null && goal.getIsCompleted();
                boolean isNowCompleted = totalAllocatedAmount >= goal.getTargetAmount();
                
                if (wasCompleted != isNowCompleted) {
                    goal.setIsCompleted(isNowCompleted);
                    goalRepository.save(goal);
                    logger.info("Updated goal {} completion status from {} to {} (allocated: {}, target: {})", 
                        goalId, wasCompleted, isNowCompleted, totalAllocatedAmount, goal.getTargetAmount());
                }
                
            } catch (Exception e) {
                logger.error("Error updating completion status for goal {}: {}", goalId, e.getMessage(), e);
                // Continue with other goals
            }
        }
    }

    /**
     * Remove an institution from all linked goals
     * Called when an institution is deleted
     */
    public void removeInstitutionFromGoals(String userId, String institutionId, Institution institution) {
        if (institution.getLinkedGoals() == null || institution.getLinkedGoals().isEmpty()) {
            return; // No goals linked to this institution
        }
        
        logger.info("Removing institution {} from {} linked goals", 
            institutionId, institution.getLinkedGoals().size());
        
        // Create a copy of the list to avoid concurrent modification
        List<String> linkedGoals = new ArrayList<>(institution.getLinkedGoals());
        
        for (String goalId : linkedGoals) {
            try {
                Goal goal = goalRepository.findByUserIdAndGoalId(userId, goalId);
                if (goal == null) {
                    logger.warn("Goal {} not found for user {}, skipping", goalId, userId);
                    continue;
                }
                
                // Get current linked institutions
                Map<String, Integer> linkedInstitutions = goal.getLinkedInstitutions();
                if (linkedInstitutions == null || !linkedInstitutions.containsKey(institutionId)) {
                    logger.warn("Institution {} not found in goal {}, skipping", institutionId, goalId);
                    continue;
                }
                
                // Remove the institution from the goal's linked institutions
                Integer removedPercent = linkedInstitutions.remove(institutionId);
                
                // Update the goal with the new linked institutions map
                goal.setLinkedInstitutions(linkedInstitutions);
                
                // Recalculate completion status if there are still linked institutions
                if (!linkedInstitutions.isEmpty()) {
                    double totalAllocatedAmount = 0.0;
                    for (Map.Entry<String, Integer> entry : linkedInstitutions.entrySet()) {
                        String instId = entry.getKey();
                        Integer allocationPercent = entry.getValue();
                        
                        Institution inst = institutionRepository.findByUserIdAndInstitutionId(userId, instId);
                        if (inst == null) {
                            logger.warn("Institution {} not found for goal {}, skipping", instId, goalId);
                            continue;
                        }
                        
                        double institutionCurrentBalance = inst.getCurrentBalance() != null 
                            ? inst.getCurrentBalance() : 0.0;
                        double allocatedAmount = (institutionCurrentBalance * allocationPercent) / 100.0;
                        totalAllocatedAmount += allocatedAmount;
                    }
                    
                    goal.setIsCompleted(totalAllocatedAmount >= goal.getTargetAmount());
                } else {
                    // No more linked institutions, goal cannot be completed
                    goal.setIsCompleted(false);
                }
                
                goalRepository.save(goal);
                
                logger.info("Removed institution {} ({}%) from goal {}, updated completion status to {}", 
                    institutionId, removedPercent, goalId, goal.getIsCompleted());
                
            } catch (Exception e) {
                logger.error("Error removing institution {} from goal {}: {}", 
                    institutionId, goalId, e.getMessage(), e);
                // Continue with other goals
            }
        }
    }
}
