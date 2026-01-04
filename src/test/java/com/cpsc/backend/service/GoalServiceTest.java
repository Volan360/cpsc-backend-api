package com.cpsc.backend.service;

import com.cpsc.backend.entity.Goal;
import com.cpsc.backend.entity.Institution;
import com.cpsc.backend.exception.InvalidInstitutionDataException;
import com.cpsc.backend.model.CreateGoalRequest;
import com.cpsc.backend.model.GoalResponse;
import com.cpsc.backend.repository.GoalRepository;
import com.cpsc.backend.repository.InstitutionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GoalServiceTest {

    @Mock
    private GoalRepository goalRepository;

    @Mock
    private InstitutionRepository institutionRepository;

    private GoalService goalService;

    private CreateGoalRequest validRequest;

    @BeforeEach
    void setUp() {
        goalService = new GoalService(goalRepository, institutionRepository);
        
        validRequest = new CreateGoalRequest();
        validRequest.setName("Emergency Fund");
        validRequest.setDescription("Save 6 months of expenses");
        Map<String, Integer> linkedInstitutions = new HashMap<>();
        linkedInstitutions.put("550e8400-e29b-41d4-a716-446655440000", 50);
        validRequest.setLinkedInstitutions(linkedInstitutions);
    }

    @Test
    void createGoal_Success() {
        String userId = "3c925d70-6d8d-4e59-9d2c-2d86a5f0bf28";
        Institution institution = createInstitution("550e8400-e29b-41d4-a716-446655440000", 0);
        
        when(institutionRepository.findByUserIdAndInstitutionId(userId, "550e8400-e29b-41d4-a716-446655440000")).thenReturn(institution);
        doNothing().when(institutionRepository).save(any(Institution.class));
        doNothing().when(goalRepository).save(any(Goal.class));

        GoalResponse response = goalService.createGoal(userId, validRequest);

        assertThat(response).isNotNull();
        assertThat(response.getGoalId()).isNotNull();
        assertThat(response.getName()).isEqualTo("Emergency Fund");
        assertThat(response.getDescription()).isEqualTo("Save 6 months of expenses");
        assertThat(response.getLinkedInstitutions()).containsEntry("550e8400-e29b-41d4-a716-446655440000", 50);
        
        verify(institutionRepository).save(any(Institution.class));
        verify(goalRepository).save(any(Goal.class));
    }

    @Test
    void createGoal_UpdatesInstitutionAllocation() {
        String userId = "3c925d70-6d8d-4e59-9d2c-2d86a5f0bf28";
        Institution institution = createInstitution("550e8400-e29b-41d4-a716-446655440000", 25);
        
        when(institutionRepository.findByUserIdAndInstitutionId(userId, "550e8400-e29b-41d4-a716-446655440000")).thenReturn(institution);
        doNothing().when(institutionRepository).save(any(Institution.class));
        doNothing().when(goalRepository).save(any(Goal.class));

        goalService.createGoal(userId, validRequest);

        ArgumentCaptor<Institution> institutionCaptor = ArgumentCaptor.forClass(Institution.class);
        verify(institutionRepository).save(institutionCaptor.capture());
        
        Institution savedInstitution = institutionCaptor.getValue();
        assertThat(savedInstitution.getAllocatedPercent()).isEqualTo(75); // 25 + 50
    }

    @Test
    void createGoal_MultipleInstitutions_UpdatesAll() {
        String userId = "3c925d70-6d8d-4e59-9d2c-2d86a5f0bf28";
        Map<String, Integer> linkedInstitutions = new HashMap<>();
        linkedInstitutions.put("550e8400-e29b-41d4-a716-446655440001", 30);
        linkedInstitutions.put("550e8400-e29b-41d4-a716-446655440002", 40);
        validRequest.setLinkedInstitutions(linkedInstitutions);
        
        Institution institution1 = createInstitution("550e8400-e29b-41d4-a716-446655440001", 10);
        Institution institution2 = createInstitution("550e8400-e29b-41d4-a716-446655440002", 20);
        
        when(institutionRepository.findByUserIdAndInstitutionId(userId, "550e8400-e29b-41d4-a716-446655440001")).thenReturn(institution1);
        when(institutionRepository.findByUserIdAndInstitutionId(userId, "550e8400-e29b-41d4-a716-446655440002")).thenReturn(institution2);
        doNothing().when(institutionRepository).save(any(Institution.class));
        doNothing().when(goalRepository).save(any(Goal.class));

        goalService.createGoal(userId, validRequest);

        verify(institutionRepository, times(2)).save(any(Institution.class));
        verify(goalRepository).save(any(Goal.class));
    }

    @Test
    void createGoal_NullUserId_ThrowsException() {
        assertThatThrownBy(() -> goalService.createGoal(null, validRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("User ID cannot be null or empty");
    }

    @Test
    void createGoal_EmptyUserId_ThrowsException() {
        assertThatThrownBy(() -> goalService.createGoal("", validRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("User ID cannot be null or empty");
    }

    @Test
    void createGoal_NullName_ThrowsException() {
        validRequest.setName(null);

        assertThatThrownBy(() -> goalService.createGoal("3c925d70-6d8d-4e59-9d2c-2d86a5f0bf28", validRequest))
                .isInstanceOf(InvalidInstitutionDataException.class)
                .hasMessage("Goal name cannot be empty");
    }

    @Test
    void createGoal_EmptyName_ThrowsException() {
        validRequest.setName("");

        assertThatThrownBy(() -> goalService.createGoal("3c925d70-6d8d-4e59-9d2c-2d86a5f0bf28", validRequest))
                .isInstanceOf(InvalidInstitutionDataException.class)
                .hasMessage("Goal name cannot be empty");
    }

    @Test
    void createGoal_NameTooLong_ThrowsException() {
        validRequest.setName("a".repeat(101));

        assertThatThrownBy(() -> goalService.createGoal("3c925d70-6d8d-4e59-9d2c-2d86a5f0bf28", validRequest))
                .isInstanceOf(InvalidInstitutionDataException.class)
                .hasMessageContaining("Goal name cannot exceed 100 characters");
    }

    @Test
    void createGoal_DescriptionTooLong_ThrowsException() {
        validRequest.setDescription("a".repeat(501));

        assertThatThrownBy(() -> goalService.createGoal("3c925d70-6d8d-4e59-9d2c-2d86a5f0bf28", validRequest))
                .isInstanceOf(InvalidInstitutionDataException.class)
                .hasMessageContaining("Goal description cannot exceed 500 characters");
    }

    @Test
    void createGoal_NoLinkedInstitutions_ThrowsException() {
        validRequest.setLinkedInstitutions(null);

        assertThatThrownBy(() -> goalService.createGoal("3c925d70-6d8d-4e59-9d2c-2d86a5f0bf28", validRequest))
                .isInstanceOf(InvalidInstitutionDataException.class)
                .hasMessage("At least one linked institution is required");
    }

    @Test
    void createGoal_EmptyLinkedInstitutions_ThrowsException() {
        validRequest.setLinkedInstitutions(new HashMap<>());

        assertThatThrownBy(() -> goalService.createGoal("3c925d70-6d8d-4e59-9d2c-2d86a5f0bf28", validRequest))
                .isInstanceOf(InvalidInstitutionDataException.class)
                .hasMessage("At least one linked institution is required");
    }

    @Test
    void createGoal_InvalidPercentage_Negative_ThrowsException() {
        Map<String, Integer> linkedInstitutions = new HashMap<>();
        linkedInstitutions.put("550e8400-e29b-41d4-a716-446655440000", -10);
        validRequest.setLinkedInstitutions(linkedInstitutions);

        assertThatThrownBy(() -> goalService.createGoal("3c925d70-6d8d-4e59-9d2c-2d86a5f0bf28", validRequest))
                .isInstanceOf(InvalidInstitutionDataException.class)
                .hasMessageContaining("Allocation percentage must be between 0 and 100");
    }

    @Test
    void createGoal_InvalidPercentage_ExceedsMax_ThrowsException() {
        Map<String, Integer> linkedInstitutions = new HashMap<>();
        linkedInstitutions.put("550e8400-e29b-41d4-a716-446655440000", 101);
        validRequest.setLinkedInstitutions(linkedInstitutions);

        assertThatThrownBy(() -> goalService.createGoal("3c925d70-6d8d-4e59-9d2c-2d86a5f0bf28", validRequest))
                .isInstanceOf(InvalidInstitutionDataException.class)
                .hasMessageContaining("Allocation percentage must be between 0 and 100");
    }

    @Test
    void createGoal_InstitutionNotFound_ThrowsException() {
        String userId = "3c925d70-6d8d-4e59-9d2c-2d86a5f0bf28";
        
        when(institutionRepository.findByUserIdAndInstitutionId(userId, "550e8400-e29b-41d4-a716-446655440000")).thenReturn(null);

        assertThatThrownBy(() -> goalService.createGoal(userId, validRequest))
                .isInstanceOf(com.cpsc.backend.exception.InstitutionNotFoundException.class)
                .hasMessageContaining("Institution not found or does not belong to user");
    }

    @Test
    void createGoal_InstitutionBelongsToOtherUser_ThrowsException() {
        String userId = "3c925d70-6d8d-4e59-9d2c-2d86a5f0bf28";
        
        // Repository returns null when institution doesn't belong to user
        when(institutionRepository.findByUserIdAndInstitutionId(userId, "550e8400-e29b-41d4-a716-446655440000")).thenReturn(null);

        assertThatThrownBy(() -> goalService.createGoal(userId, validRequest))
                .isInstanceOf(com.cpsc.backend.exception.InstitutionNotFoundException.class)
                .hasMessageContaining("Institution not found or does not belong to user");
    }

    @Test
    void createGoal_InsufficientAllocation_ThrowsException() {
        String userId = "3c925d70-6d8d-4e59-9d2c-2d86a5f0bf28";
        Institution institution = createInstitution("550e8400-e29b-41d4-a716-446655440000", 60); // Already has 60%
        
        // Request tries to allocate 50% more (total would be 110%)
        when(institutionRepository.findByUserIdAndInstitutionId(userId, "550e8400-e29b-41d4-a716-446655440000")).thenReturn(institution);

        assertThatThrownBy(() -> goalService.createGoal(userId, validRequest))
                .isInstanceOf(InvalidInstitutionDataException.class)
                .hasMessageContaining("has insufficient allocation")
                .hasMessageContaining("Current: 60%")
                .hasMessageContaining("Requested: 50%")
                .hasMessageContaining("Total would be: 110%");
    }

    @Test
    void createGoal_ExactlyMaxAllocation_Success() {
        String userId = "3c925d70-6d8d-4e59-9d2c-2d86a5f0bf28";
        Institution institution = createInstitution("550e8400-e29b-41d4-a716-446655440000", 50);
        
        when(institutionRepository.findByUserIdAndInstitutionId(userId, "550e8400-e29b-41d4-a716-446655440000")).thenReturn(institution);
        doNothing().when(institutionRepository).save(any(Institution.class));
        doNothing().when(goalRepository).save(any(Goal.class));

        GoalResponse response = goalService.createGoal(userId, validRequest);

        assertThat(response).isNotNull();
        
        ArgumentCaptor<Institution> captor = ArgumentCaptor.forClass(Institution.class);
        verify(institutionRepository).save(captor.capture());
        assertThat(captor.getValue().getAllocatedPercent()).isEqualTo(100); // 50 + 50
    }

    @Test
    void getUserGoals_Success() {
        String userId = "3c925d70-6d8d-4e59-9d2c-2d86a5f0bf28";
        Goal goal1 = createGoal("770e8400-e29b-41d4-a716-446655440001", "Goal 1");
        Goal goal2 = createGoal("770e8400-e29b-41d4-a716-446655440002", "Goal 2");
        
        when(goalRepository.findAllByUserId(userId)).thenReturn(List.of(goal1, goal2));

        List<GoalResponse> results = goalService.getUserGoals(userId);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).getName()).isEqualTo("Goal 1");
        assertThat(results.get(1).getName()).isEqualTo("Goal 2");
    }

    @Test
    void getUserGoals_NullUserId_ThrowsException() {
        assertThatThrownBy(() -> goalService.getUserGoals(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("User ID cannot be null or empty");
    }

    @Test
    void getUserGoals_EmptyUserId_ThrowsException() {
        assertThatThrownBy(() -> goalService.getUserGoals(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("User ID cannot be null or empty");
    }

    private Institution createInstitution(String institutionId, Integer allocatedPercent) {
        Institution institution = new Institution();
        institution.setUserId("3c925d70-6d8d-4e59-9d2c-2d86a5f0bf28");
        institution.setInstitutionId(institutionId);
        institution.setInstitutionName("Test Bank");
        institution.setStartingBalance(1000.0);
        institution.setCurrentBalance(1000.0);
        institution.setAllocatedPercent(allocatedPercent);
        institution.setCreatedAt(System.currentTimeMillis() / 1000L);
        return institution;
    }

    private Goal createGoal(String goalId, String name) {
        Goal goal = new Goal();
        goal.setUserId("3c925d70-6d8d-4e59-9d2c-2d86a5f0bf28");
        goal.setGoalId(goalId);
        goal.setName(name);
        goal.setDescription("Test description");
        
        Map<String, Integer> linkedInstitutions = new HashMap<>();
        linkedInstitutions.put("550e8400-e29b-41d4-a716-446655440001", 50);
        goal.setLinkedInstitutions(linkedInstitutions);
        
        goal.setCreatedAt(System.currentTimeMillis() / 1000L);
        return goal;
    }
}
