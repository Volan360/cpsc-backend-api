package com.cpsc.backend.repository;

import com.cpsc.backend.entity.Goal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;

import java.util.List;
import java.util.stream.Collectors;

@Repository
public class GoalRepository {

    private static final Logger logger = LoggerFactory.getLogger(GoalRepository.class);
    
    private final DynamoDbTable<Goal> goalTable;

    public GoalRepository(DynamoDbEnhancedClient enhancedClient,
                         @Value("${dynamodb.goals.table.name}") String tableName) {
        if (enhancedClient == null) {
            throw new IllegalArgumentException("DynamoDbEnhancedClient cannot be null");
        }
        if (tableName == null || tableName.trim().isEmpty()) {
            throw new IllegalArgumentException("Table name cannot be null or empty");
        }
        
        this.goalTable = enhancedClient.table(tableName, TableSchema.fromBean(Goal.class));
        logger.info("GoalRepository initialized with table: {}", tableName);
    }

    public void save(Goal goal) {
        if (goal == null) {
            throw new IllegalArgumentException("Goal cannot be null");
        }
        
        validateGoal(goal);
        
        logger.debug("Saving goal: userId={}, goalId={}", 
            goal.getUserId(), goal.getGoalId());
        
        goalTable.putItem(goal);
    }

    public Goal findByUserIdAndGoalId(String userId, String goalId) {
        if (userId == null || userId.trim().isEmpty()) {
            throw new IllegalArgumentException("User ID cannot be null or empty");
        }
        if (goalId == null || goalId.trim().isEmpty()) {
            throw new IllegalArgumentException("Goal ID cannot be null or empty");
        }
        
        logger.debug("Finding goal: userId={}, goalId={}", userId, goalId);
        
        Key key = Key.builder()
                .partitionValue(userId)
                .sortValue(goalId)
                .build();
        return goalTable.getItem(key);
    }

    public List<Goal> findAllByUserId(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            throw new IllegalArgumentException("User ID cannot be null or empty");
        }
        
        logger.debug("Finding all goals for userId={}", userId);
        
        QueryConditional queryConditional = QueryConditional
                .keyEqualTo(Key.builder().partitionValue(userId).build());
        
        List<Goal> goals = goalTable.query(queryConditional)
                .items()
                .stream()
                .collect(Collectors.toList());
        
        logger.debug("Found {} goals for userId={}", goals.size(), userId);
        
        return goals;
    }

    public void delete(Goal goal) {
        if (goal == null) {
            throw new IllegalArgumentException("Goal cannot be null");
        }
        
        logger.debug("Deleting goal: userId={}, goalId={}", 
            goal.getUserId(), goal.getGoalId());
        
        goalTable.deleteItem(goal);
    }

    public void delete(String userId, String goalId) {
        if (userId == null || userId.trim().isEmpty()) {
            throw new IllegalArgumentException("User ID cannot be null or empty");
        }
        if (goalId == null || goalId.trim().isEmpty()) {
            throw new IllegalArgumentException("Goal ID cannot be null or empty");
        }
        
        logger.debug("Deleting goal: userId={}, goalId={}", userId, goalId);
        
        Key key = Key.builder()
            .partitionValue(userId)
            .sortValue(goalId)
            .build();
        
        goalTable.deleteItem(key);
    }

    private void validateGoal(Goal goal) {
        if (goal.getUserId() == null || goal.getUserId().trim().isEmpty()) {
            throw new IllegalArgumentException("User ID cannot be null or empty");
        }
        if (goal.getGoalId() == null || goal.getGoalId().trim().isEmpty()) {
            throw new IllegalArgumentException("Goal ID cannot be null or empty");
        }
        if (goal.getName() == null || goal.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Goal name cannot be null or empty");
        }
    }
}
