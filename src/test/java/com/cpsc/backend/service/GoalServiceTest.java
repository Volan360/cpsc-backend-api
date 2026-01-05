package com.cpsc.backend.service;

import com.cpsc.backend.entity.Goal;
import com.cpsc.backend.entity.Institution;
import com.cpsc.backend.exception.InvalidInstitutionDataException;
import com.cpsc.backend.model.CreateGoalRequest;
import com.cpsc.backend.model.EditGoalRequest;
import com.cpsc.backend.model.GoalResponse;
import com.cpsc.backend.repository.GoalRepository;
import com.cpsc.backend.repository.InstitutionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
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
        validRequest.setTargetAmount(10000.00);
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

    @Test
    void editGoal_RemoveInstitution_UpdatesInstitutionAllocationAndLinkedGoals() {
        // Arrange
        String userId = "3c925d70-6d8d-4e59-9d2c-2d86a5f0bf28";
        String goalId = "770e8400-e29b-41d4-a716-446655440000";
        
        // Create existing goal with two institutions
        Goal existingGoal = new Goal();
        existingGoal.setUserId(userId);
        existingGoal.setGoalId(goalId);
        existingGoal.setName("Original Goal");
        existingGoal.setTargetAmount(10000.0);
        Map<String, Integer> oldLinkedInstitutions = new HashMap<>();
        oldLinkedInstitutions.put("550e8400-e29b-41d4-a716-446655440000", 50);
        oldLinkedInstitutions.put("660e8400-e29b-41d4-a716-446655440000", 30);
        existingGoal.setLinkedInstitutions(oldLinkedInstitutions);
        
        // Create institutions
        Institution inst1 = createInstitution("550e8400-e29b-41d4-a716-446655440000", 50);
        inst1.setLinkedGoals(new ArrayList<>(List.of(goalId)));
        
        Institution inst2 = createInstitution("660e8400-e29b-41d4-a716-446655440000", 30);
        inst2.setLinkedGoals(new ArrayList<>(List.of(goalId)));
        inst2.setCurrentBalance(2000.0);
        
        // Edit request removes inst-2
        EditGoalRequest editRequest = new EditGoalRequest();
        Map<String, Integer> newLinkedInstitutions = new HashMap<>();
        newLinkedInstitutions.put("550e8400-e29b-41d4-a716-446655440000", 50);
        editRequest.setLinkedInstitutions(newLinkedInstitutions);
        
        when(goalRepository.findByUserIdAndGoalId(userId, goalId)).thenReturn(existingGoal);
        when(institutionRepository.findByUserIdAndInstitutionId(userId, "550e8400-e29b-41d4-a716-446655440000")).thenReturn(inst1);
        when(institutionRepository.findByUserIdAndInstitutionId(userId, "660e8400-e29b-41d4-a716-446655440000")).thenReturn(inst2);
        
        // Act
        goalService.editGoal(userId, goalId, editRequest);
        
        // Assert - inst-2 should have allocation reduced and goal removed
        ArgumentCaptor<Institution> institutionCaptor = ArgumentCaptor.forClass(Institution.class);
        verify(institutionRepository, atLeastOnce()).save(institutionCaptor.capture());
        
        List<Institution> savedInstitutions = institutionCaptor.getAllValues();
        Institution savedInst2 = savedInstitutions.stream()
            .filter(i -> i.getInstitutionId().equals("660e8400-e29b-41d4-a716-446655440000"))
            .findFirst()
            .orElse(null);
        
        assertThat(savedInst2).isNotNull();
        assertThat(savedInst2.getAllocatedPercent()).isEqualTo(0); // 30 - 30 = 0
        assertThat(savedInst2.getLinkedGoals()).doesNotContain(goalId);
    }

    @Test
    void editGoal_AddInstitution_UpdatesInstitutionAllocationAndLinkedGoals() {
        // Arrange
        String userId = "3c925d70-6d8d-4e59-9d2c-2d86a5f0bf28";
        String goalId = "770e8400-e29b-41d4-a716-446655440000";
        
        // Create existing goal with one institution
        Goal existingGoal = new Goal();
        existingGoal.setUserId(userId);
        existingGoal.setGoalId(goalId);
        existingGoal.setName("Original Goal");
        existingGoal.setTargetAmount(10000.0);
        Map<String, Integer> oldLinkedInstitutions = new HashMap<>();
        oldLinkedInstitutions.put("550e8400-e29b-41d4-a716-446655440000", 50);
        existingGoal.setLinkedInstitutions(oldLinkedInstitutions);
        
        // Create institutions
        Institution inst1 = createInstitution("550e8400-e29b-41d4-a716-446655440000", 50);
        inst1.setLinkedGoals(new ArrayList<>(List.of(goalId)));
        
        Institution inst2 = createInstitution("660e8400-e29b-41d4-a716-446655440000", 20); // Already has 20% from another goal
        inst2.setLinkedGoals(new ArrayList<>(List.of("880e8400-e29b-41d4-a716-446655440000")));
        inst2.setCurrentBalance(2000.0);
        
        // Edit request adds inst-2
        EditGoalRequest editRequest = new EditGoalRequest();
        Map<String, Integer> newLinkedInstitutions = new HashMap<>();
        newLinkedInstitutions.put("550e8400-e29b-41d4-a716-446655440000", 50);
        newLinkedInstitutions.put("660e8400-e29b-41d4-a716-446655440000", 30);
        editRequest.setLinkedInstitutions(newLinkedInstitutions);
        
        when(goalRepository.findByUserIdAndGoalId(userId, goalId)).thenReturn(existingGoal);
        when(institutionRepository.findByUserIdAndInstitutionId(userId, "550e8400-e29b-41d4-a716-446655440000")).thenReturn(inst1);
        when(institutionRepository.findByUserIdAndInstitutionId(userId, "660e8400-e29b-41d4-a716-446655440000")).thenReturn(inst2);
        
        // Act
        goalService.editGoal(userId, goalId, editRequest);
        
        // Assert - inst-2 should have allocation increased and goal added
        ArgumentCaptor<Institution> institutionCaptor = ArgumentCaptor.forClass(Institution.class);
        verify(institutionRepository, atLeastOnce()).save(institutionCaptor.capture());
        
        List<Institution> savedInstitutions = institutionCaptor.getAllValues();
        Institution savedInst2 = savedInstitutions.stream()
            .filter(i -> i.getInstitutionId().equals("660e8400-e29b-41d4-a716-446655440000"))
            .findFirst()
            .orElse(null);
        
        assertThat(savedInst2).isNotNull();
        assertThat(savedInst2.getAllocatedPercent()).isEqualTo(50); // 20 + 30 = 50
        assertThat(savedInst2.getLinkedGoals()).contains(goalId);
    }

    @Test
    void editGoal_ChangeInstitutionPercentage_UpdatesInstitutionAllocation() {
        // Arrange
        String userId = "3c925d70-6d8d-4e59-9d2c-2d86a5f0bf28";
        String goalId = "770e8400-e29b-41d4-a716-446655440000";
        
        // Create existing goal
        Goal existingGoal = new Goal();
        existingGoal.setUserId(userId);
        existingGoal.setGoalId(goalId);
        existingGoal.setName("Original Goal");
        existingGoal.setTargetAmount(10000.0);
        Map<String, Integer> oldLinkedInstitutions = new HashMap<>();
        oldLinkedInstitutions.put("550e8400-e29b-41d4-a716-446655440000", 50);
        existingGoal.setLinkedInstitutions(oldLinkedInstitutions);
        
        // Create institution
        Institution inst1 = createInstitution("550e8400-e29b-41d4-a716-446655440000", 60); // 50 from this goal + 10 from another goal
        inst1.setLinkedGoals(new ArrayList<>(List.of(goalId, "880e8400-e29b-41d4-a716-446655440000")));
        
        // Edit request changes inst-1 from 50% to 75%
        EditGoalRequest editRequest = new EditGoalRequest();
        Map<String, Integer> newLinkedInstitutions = new HashMap<>();
        newLinkedInstitutions.put("550e8400-e29b-41d4-a716-446655440000", 75);
        editRequest.setLinkedInstitutions(newLinkedInstitutions);
        
        when(goalRepository.findByUserIdAndGoalId(userId, goalId)).thenReturn(existingGoal);
        when(institutionRepository.findByUserIdAndInstitutionId(userId, "550e8400-e29b-41d4-a716-446655440000")).thenReturn(inst1);
        
        // Act
        goalService.editGoal(userId, goalId, editRequest);
        
        // Assert - inst-1 should have allocation increased by 25 (75 - 50)
        ArgumentCaptor<Institution> institutionCaptor = ArgumentCaptor.forClass(Institution.class);
        verify(institutionRepository, atLeastOnce()).save(institutionCaptor.capture());
        
        List<Institution> savedInstitutions = institutionCaptor.getAllValues();
        Institution savedInst1 = savedInstitutions.stream()
            .filter(i -> i.getInstitutionId().equals("550e8400-e29b-41d4-a716-446655440000"))
            .findFirst()
            .orElse(null);
        
        assertThat(savedInst1).isNotNull();
        assertThat(savedInst1.getAllocatedPercent()).isEqualTo(85); // 60 + 25 = 85
        assertThat(savedInst1.getLinkedGoals()).contains(goalId);
    }

    @Test
    void editGoal_ReplaceAllInstitutions_UpdatesBothOldAndNewInstitutions() {
        // Arrange
        String userId = "3c925d70-6d8d-4e59-9d2c-2d86a5f0bf28";
        String goalId = "770e8400-e29b-41d4-a716-446655440000";
        
        // Create existing goal with inst-1
        Goal existingGoal = new Goal();
        existingGoal.setUserId(userId);
        existingGoal.setGoalId(goalId);
        existingGoal.setName("Original Goal");
        existingGoal.setTargetAmount(10000.0);
        Map<String, Integer> oldLinkedInstitutions = new HashMap<>();
        oldLinkedInstitutions.put("550e8400-e29b-41d4-a716-446655440000", 50);
        existingGoal.setLinkedInstitutions(oldLinkedInstitutions);
        
        // Create institutions
        Institution inst1 = createInstitution("550e8400-e29b-41d4-a716-446655440000", 50);
        inst1.setLinkedGoals(new ArrayList<>(List.of(goalId)));
        
        Institution inst2 = createInstitution("660e8400-e29b-41d4-a716-446655440000", 0);
        inst2.setLinkedGoals(new ArrayList<>());
        inst2.setCurrentBalance(2000.0);
        
        // Edit request completely replaces inst-1 with inst-2
        EditGoalRequest editRequest = new EditGoalRequest();
        Map<String, Integer> newLinkedInstitutions = new HashMap<>();
        newLinkedInstitutions.put("660e8400-e29b-41d4-a716-446655440000", 60);
        editRequest.setLinkedInstitutions(newLinkedInstitutions);
        
        when(goalRepository.findByUserIdAndGoalId(userId, goalId)).thenReturn(existingGoal);
        when(institutionRepository.findByUserIdAndInstitutionId(userId, "550e8400-e29b-41d4-a716-446655440000")).thenReturn(inst1);
        when(institutionRepository.findByUserIdAndInstitutionId(userId, "660e8400-e29b-41d4-a716-446655440000")).thenReturn(inst2);
        
        // Act
        goalService.editGoal(userId, goalId, editRequest);
        
        // Assert - both institutions should be updated
        ArgumentCaptor<Institution> institutionCaptor = ArgumentCaptor.forClass(Institution.class);
        verify(institutionRepository, atLeastOnce()).save(institutionCaptor.capture());
        
        List<Institution> savedInstitutions = institutionCaptor.getAllValues();
        
        // inst-1 should have allocation removed and goal removed
        Institution savedInst1 = savedInstitutions.stream()
            .filter(i -> i.getInstitutionId().equals("550e8400-e29b-41d4-a716-446655440000"))
            .findFirst()
            .orElse(null);
        assertThat(savedInst1).isNotNull();
        assertThat(savedInst1.getAllocatedPercent()).isEqualTo(0);
        assertThat(savedInst1.getLinkedGoals()).doesNotContain(goalId);
        
        // inst-2 should have allocation added and goal added
        Institution savedInst2 = savedInstitutions.stream()
            .filter(i -> i.getInstitutionId().equals("660e8400-e29b-41d4-a716-446655440000"))
            .findFirst()
            .orElse(null);
        assertThat(savedInst2).isNotNull();
        assertThat(savedInst2.getAllocatedPercent()).isEqualTo(60);
        assertThat(savedInst2.getLinkedGoals()).contains(goalId);
    }

    @Test
    void deleteGoal_UpdatesAllLinkedInstitutions() {
        // Arrange
        String userId = "3c925d70-6d8d-4e59-9d2c-2d86a5f0bf28";
        String goalId = "770e8400-e29b-41d4-a716-446655440000";
        
        // Create goal with two institutions
        Goal goal = new Goal();
        goal.setUserId(userId);
        goal.setGoalId(goalId);
        goal.setName("Test Goal");
        goal.setTargetAmount(10000.0);
        Map<String, Integer> linkedInstitutions = new HashMap<>();
        linkedInstitutions.put("550e8400-e29b-41d4-a716-446655440000", 50);
        linkedInstitutions.put("660e8400-e29b-41d4-a716-446655440000", 30);
        goal.setLinkedInstitutions(linkedInstitutions);
        
        // Create institutions
        Institution inst1 = createInstitution("550e8400-e29b-41d4-a716-446655440000", 50);
        inst1.setLinkedGoals(new ArrayList<>(List.of(goalId)));
        
        Institution inst2 = createInstitution("660e8400-e29b-41d4-a716-446655440000", 30);
        inst2.setLinkedGoals(new ArrayList<>(List.of(goalId)));
        
        when(goalRepository.findByUserIdAndGoalId(userId, goalId)).thenReturn(goal);
        when(institutionRepository.findByUserIdAndInstitutionId(userId, "550e8400-e29b-41d4-a716-446655440000")).thenReturn(inst1);
        when(institutionRepository.findByUserIdAndInstitutionId(userId, "660e8400-e29b-41d4-a716-446655440000")).thenReturn(inst2);
        
        // Act
        goalService.deleteGoal(userId, goalId);
        
        // Assert - both institutions should have allocations reduced and goal removed
        ArgumentCaptor<Institution> institutionCaptor = ArgumentCaptor.forClass(Institution.class);
        verify(institutionRepository, times(2)).save(institutionCaptor.capture());
        
        List<Institution> savedInstitutions = institutionCaptor.getAllValues();
        
        // inst1 should have allocation reduced to 0 and goal removed
        Institution savedInst1 = savedInstitutions.stream()
            .filter(i -> i.getInstitutionId().equals("550e8400-e29b-41d4-a716-446655440000"))
            .findFirst()
            .orElse(null);
        assertThat(savedInst1).isNotNull();
        assertThat(savedInst1.getAllocatedPercent()).isEqualTo(0); // 50 - 50 = 0
        assertThat(savedInst1.getLinkedGoals()).doesNotContain(goalId);
        
        // inst2 should have allocation reduced to 0 and goal removed
        Institution savedInst2 = savedInstitutions.stream()
            .filter(i -> i.getInstitutionId().equals("660e8400-e29b-41d4-a716-446655440000"))
            .findFirst()
            .orElse(null);
        assertThat(savedInst2).isNotNull();
        assertThat(savedInst2.getAllocatedPercent()).isEqualTo(0); // 30 - 30 = 0
        assertThat(savedInst2.getLinkedGoals()).doesNotContain(goalId);
        
        // Verify goal was deleted
        verify(goalRepository).delete(userId, goalId);
    }

    @Test
    void deleteGoal_WithMultipleGoals_OnlyReducesDeletedGoalAllocation() {
        // Arrange
        String userId = "3c925d70-6d8d-4e59-9d2c-2d86a5f0bf28";
        String goalId = "770e8400-e29b-41d4-a716-446655440000";
        String otherGoalId = "880e8400-e29b-41d4-a716-446655440000";
        
        // Create goal being deleted
        Goal goal = new Goal();
        goal.setUserId(userId);
        goal.setGoalId(goalId);
        goal.setName("Test Goal");
        goal.setTargetAmount(10000.0);
        Map<String, Integer> linkedInstitutions = new HashMap<>();
        linkedInstitutions.put("550e8400-e29b-41d4-a716-446655440000", 40);
        goal.setLinkedInstitutions(linkedInstitutions);
        
        // Create institution with 70% allocated (40% to this goal, 30% to another)
        Institution inst1 = createInstitution("550e8400-e29b-41d4-a716-446655440000", 70);
        inst1.setLinkedGoals(new ArrayList<>(List.of(goalId, otherGoalId)));
        
        when(goalRepository.findByUserIdAndGoalId(userId, goalId)).thenReturn(goal);
        when(institutionRepository.findByUserIdAndInstitutionId(userId, "550e8400-e29b-41d4-a716-446655440000")).thenReturn(inst1);
        
        // Act
        goalService.deleteGoal(userId, goalId);
        
        // Assert - institution should have allocation reduced by 40 (70 - 40 = 30)
        ArgumentCaptor<Institution> institutionCaptor = ArgumentCaptor.forClass(Institution.class);
        verify(institutionRepository).save(institutionCaptor.capture());
        
        Institution savedInst = institutionCaptor.getValue();
        assertThat(savedInst.getAllocatedPercent()).isEqualTo(30); // 70 - 40 = 30
        assertThat(savedInst.getLinkedGoals()).doesNotContain(goalId);
        assertThat(savedInst.getLinkedGoals()).contains(otherGoalId); // Other goal still linked
        
        // Verify goal was deleted
        verify(goalRepository).delete(userId, goalId);
    }
}
