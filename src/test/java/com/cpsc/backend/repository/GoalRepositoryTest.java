package com.cpsc.backend.repository;

import com.cpsc.backend.entity.Goal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.PageIterable;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GoalRepositoryTest {

    @Mock
    private DynamoDbEnhancedClient enhancedClient;

    @Mock
    private DynamoDbTable<Goal> goalTable;

    @Mock
    private PageIterable<Goal> pageIterable;

    private GoalRepository repository;

    private static final String TABLE_NAME = "test-goals";
    private static final String USER_ID = "3c925d70-6d8d-4e59-9d2c-2d86a5f0bf28";
    private static final String GOAL_ID = "550e8400-e29b-41d4-a716-446655440000";

    @BeforeEach
    void setUp() {
        when(enhancedClient.table(eq(TABLE_NAME), any(TableSchema.class))).thenReturn(goalTable);
        repository = new GoalRepository(enhancedClient, TABLE_NAME);
    }

    @Test
    void constructor_NullClient_ThrowsException() {
        assertThatThrownBy(() -> new GoalRepository(null, TABLE_NAME))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("DynamoDbEnhancedClient cannot be null");
    }

    @Test
    void constructor_NullTableName_ThrowsException() {
        assertThatThrownBy(() -> new GoalRepository(enhancedClient, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Table name cannot be null or empty");
    }

    @Test
    void constructor_EmptyTableName_ThrowsException() {
        assertThatThrownBy(() -> new GoalRepository(enhancedClient, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Table name cannot be null or empty");
    }

    @Test
    void save_ValidGoal_SavesSuccessfully() {
        Goal goal = createTestGoal();
        doNothing().when(goalTable).putItem(goal);

        repository.save(goal);

        verify(goalTable).putItem(goal);
    }

    @Test
    void save_NullGoal_ThrowsException() {
        assertThatThrownBy(() -> repository.save(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Goal cannot be null");
    }

    @Test
    void save_NullUserId_ThrowsException() {
        Goal goal = createTestGoal();
        goal.setUserId(null);

        assertThatThrownBy(() -> repository.save(goal))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("User ID cannot be null or empty");
    }

    @Test
    void save_EmptyUserId_ThrowsException() {
        Goal goal = createTestGoal();
        goal.setUserId("");

        assertThatThrownBy(() -> repository.save(goal))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("User ID cannot be null or empty");
    }

    @Test
    void save_NullGoalId_ThrowsException() {
        Goal goal = createTestGoal();
        goal.setGoalId(null);

        assertThatThrownBy(() -> repository.save(goal))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Goal ID cannot be null or empty");
    }

    @Test
    void save_EmptyGoalId_ThrowsException() {
        Goal goal = createTestGoal();
        goal.setGoalId("");

        assertThatThrownBy(() -> repository.save(goal))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Goal ID cannot be null or empty");
    }

    @Test
    void save_NullName_ThrowsException() {
        Goal goal = createTestGoal();
        goal.setName(null);

        assertThatThrownBy(() -> repository.save(goal))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Goal name cannot be null or empty");
    }

    @Test
    void save_EmptyName_ThrowsException() {
        Goal goal = createTestGoal();
        goal.setName("");

        assertThatThrownBy(() -> repository.save(goal))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Goal name cannot be null or empty");
    }

    @Test
    void findByUserIdAndGoalId_ValidIds_ReturnsGoal() {
        Goal goal = createTestGoal();
        when(goalTable.getItem(any(Key.class))).thenReturn(goal);

        Goal result = repository.findByUserIdAndGoalId(USER_ID, GOAL_ID);

        assertThat(result).isNotNull();
        assertThat(result.getUserId()).isEqualTo(USER_ID);
        assertThat(result.getGoalId()).isEqualTo(GOAL_ID);
    }

    @Test
    void findByUserIdAndGoalId_NullUserId_ThrowsException() {
        assertThatThrownBy(() -> repository.findByUserIdAndGoalId(null, GOAL_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("User ID cannot be null or empty");
    }

    @Test
    void findByUserIdAndGoalId_EmptyUserId_ThrowsException() {
        assertThatThrownBy(() -> repository.findByUserIdAndGoalId("", GOAL_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("User ID cannot be null or empty");
    }

    @Test
    void findByUserIdAndGoalId_NullGoalId_ThrowsException() {
        assertThatThrownBy(() -> repository.findByUserIdAndGoalId(USER_ID, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Goal ID cannot be null or empty");
    }

    @Test
    void findByUserIdAndGoalId_EmptyGoalId_ThrowsException() {
        assertThatThrownBy(() -> repository.findByUserIdAndGoalId(USER_ID, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Goal ID cannot be null or empty");
    }

    @Test
    void findAllByUserId_ValidUserId_ReturnsGoals() {
        Goal goal1 = createTestGoal();
        Goal goal2 = createTestGoal();
        goal2.setGoalId("goal-789");
        
        when(goalTable.query(any(QueryConditional.class))).thenReturn(pageIterable);
        when(pageIterable.items()).thenReturn(() -> Stream.of(goal1, goal2).iterator());

        List<Goal> results = repository.findAllByUserId(USER_ID);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).getUserId()).isEqualTo(USER_ID);
        assertThat(results.get(1).getUserId()).isEqualTo(USER_ID);
    }

    @Test
    void findAllByUserId_NullUserId_ThrowsException() {
        assertThatThrownBy(() -> repository.findAllByUserId(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("User ID cannot be null or empty");
    }

    @Test
    void findAllByUserId_EmptyUserId_ThrowsException() {
        assertThatThrownBy(() -> repository.findAllByUserId(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("User ID cannot be null or empty");
    }

    @Test
    void delete_ValidGoal_DeletesSuccessfully() {
        Goal goal = createTestGoal();
        
        repository.delete(goal);

        verify(goalTable).deleteItem(goal);
    }

    @Test
    void delete_NullGoal_ThrowsException() {
        assertThatThrownBy(() -> repository.delete(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Goal cannot be null");
    }

    private Goal createTestGoal() {
        Goal goal = new Goal();
        goal.setUserId(USER_ID);
        goal.setGoalId(GOAL_ID);
        goal.setName("Test Goal");
        goal.setDescription("Test Description");
        
        Map<String, Integer> linkedInstitutions = new HashMap<>();
        linkedInstitutions.put("550e8400-e29b-41d4-a716-446655440001", 50);
        linkedInstitutions.put("550e8400-e29b-41d4-a716-446655440002", 30);
        goal.setLinkedInstitutions(linkedInstitutions);
        
        goal.setCreatedAt(System.currentTimeMillis() / 1000L);
        return goal;
    }
}
