package com.cpsc.backend.controller;

import com.cpsc.backend.model.CreateGoalRequest;
import com.cpsc.backend.model.GetGoals200Response;
import com.cpsc.backend.model.GoalResponse;
import com.cpsc.backend.service.GoalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GoalControllerTest {

    @Mock
    private GoalService goalService;

    @Mock
    private Authentication authentication;

    @Mock
    private SecurityContext securityContext;

    @InjectMocks
    private GoalController goalController;

    private CreateGoalRequest createRequest;
    private GoalResponse goalResponse;
    private static final String USER_ID = "550e8400-e29b-41d4-a716-446655440001";

    @BeforeEach
    void setUp() {
        createRequest = new CreateGoalRequest();
        createRequest.setName("Emergency Fund");
        createRequest.setDescription("Save 6 months of expenses");
        
        Map<String, Integer> linkedInstitutions = new HashMap<>();
        linkedInstitutions.put("inst-1", 50);
        linkedInstitutions.put("inst-2", 30);
        createRequest.setLinkedInstitutions(linkedInstitutions);

        goalResponse = new GoalResponse();
        goalResponse.setGoalId(UUID.randomUUID());
        goalResponse.setName("Emergency Fund");
        goalResponse.setDescription("Save 6 months of expenses");
        goalResponse.setLinkedInstitutions(linkedInstitutions);
        goalResponse.setUserId(UUID.fromString(USER_ID));
        goalResponse.setCreatedAt(System.currentTimeMillis() / 1000L);
    }

    @Test
    void createGoal_Success() {
        try (MockedStatic<SecurityContextHolder> securityContextHolder = mockStatic(SecurityContextHolder.class)) {
            securityContextHolder.when(SecurityContextHolder::getContext).thenReturn(securityContext);
            when(securityContext.getAuthentication()).thenReturn(authentication);
            when(authentication.getName()).thenReturn(USER_ID);
            when(goalService.createGoal(eq(USER_ID), any(CreateGoalRequest.class)))
                    .thenReturn(goalResponse);

            ResponseEntity<GoalResponse> response = goalController.createGoal(createRequest);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getName()).isEqualTo("Emergency Fund");
            assertThat(response.getBody().getDescription()).isEqualTo("Save 6 months of expenses");
            assertThat(response.getBody().getLinkedInstitutions()).hasSize(2);

            verify(goalService).createGoal(eq(USER_ID), any(CreateGoalRequest.class));
        }
    }

    @Test
    void createGoal_ExtractsUserIdFromSecurityContext() {
        try (MockedStatic<SecurityContextHolder> securityContextHolder = mockStatic(SecurityContextHolder.class)) {
            securityContextHolder.when(SecurityContextHolder::getContext).thenReturn(securityContext);
            when(securityContext.getAuthentication()).thenReturn(authentication);
            when(authentication.getName()).thenReturn(USER_ID);
            when(goalService.createGoal(eq(USER_ID), any(CreateGoalRequest.class)))
                    .thenReturn(goalResponse);

            goalController.createGoal(createRequest);

            verify(authentication).getName();
            verify(goalService).createGoal(eq(USER_ID), any(CreateGoalRequest.class));
        }
    }

    @Test
    void getGoals_Success() {
        GoalResponse goal1 = new GoalResponse();
        goal1.setGoalId(UUID.randomUUID());
        goal1.setName("Goal 1");
        
        GoalResponse goal2 = new GoalResponse();
        goal2.setGoalId(UUID.randomUUID());
        goal2.setName("Goal 2");
        
        List<GoalResponse> goals = List.of(goal1, goal2);

        try (MockedStatic<SecurityContextHolder> securityContextHolder = mockStatic(SecurityContextHolder.class)) {
            securityContextHolder.when(SecurityContextHolder::getContext).thenReturn(securityContext);
            when(securityContext.getAuthentication()).thenReturn(authentication);
            when(authentication.getName()).thenReturn(USER_ID);
            when(goalService.getUserGoals(USER_ID)).thenReturn(goals);

            ResponseEntity<GetGoals200Response> response = goalController.getGoals();

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getGoals()).hasSize(2);

            verify(goalService).getUserGoals(USER_ID);
        }
    }

    @Test
    void getGoals_ExtractsUserIdFromSecurityContext() {
        try (MockedStatic<SecurityContextHolder> securityContextHolder = mockStatic(SecurityContextHolder.class)) {
            securityContextHolder.when(SecurityContextHolder::getContext).thenReturn(securityContext);
            when(securityContext.getAuthentication()).thenReturn(authentication);
            when(authentication.getName()).thenReturn(USER_ID);
            when(goalService.getUserGoals(USER_ID)).thenReturn(List.of());

            goalController.getGoals();

            verify(authentication).getName();
            verify(goalService).getUserGoals(USER_ID);
        }
    }
}
