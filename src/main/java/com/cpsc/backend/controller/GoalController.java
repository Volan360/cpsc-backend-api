package com.cpsc.backend.controller;

import com.cpsc.backend.api.GoalsApi;
import com.cpsc.backend.model.CreateGoalRequest;
import com.cpsc.backend.model.GetGoals200Response;
import com.cpsc.backend.model.GoalResponse;
import com.cpsc.backend.service.GoalService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class GoalController implements GoalsApi {

    private static final Logger logger = LoggerFactory.getLogger(GoalController.class);
    
    private final GoalService goalService;

    public GoalController(GoalService goalService) {
        this.goalService = goalService;
    }

    @Override
    public ResponseEntity<GoalResponse> createGoal(CreateGoalRequest createGoalRequest) {
        String userId = getAuthenticatedUserId();
        
        logger.info("Request to create goal from user {}", userId);
        
        GoalResponse response = goalService.createGoal(userId, createGoalRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Override
    public ResponseEntity<GetGoals200Response> getGoals() {
        String userId = getAuthenticatedUserId();
        
        logger.debug("Request to get goals for user {}", userId);
        
        List<GoalResponse> goals = goalService.getUserGoals(userId);
        
        GetGoals200Response response = new GetGoals200Response();
        response.setGoals(goals);
        
        return ResponseEntity.ok(response);
    }

    private String getAuthenticatedUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication == null) {
            logger.error("No authentication found in SecurityContext");
            throw new IllegalStateException("User not authenticated");
        }
        
        String userId = authentication.getName();
        
        if (userId == null || userId.trim().isEmpty()) {
            logger.error("Authentication present but user ID is null or empty");
            throw new IllegalStateException("Invalid authentication state");
        }
        
        return userId;
    }
}
