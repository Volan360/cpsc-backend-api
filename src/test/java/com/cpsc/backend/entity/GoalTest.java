package com.cpsc.backend.entity;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GoalTest {

    @Test
    void constructor_CreatesEmptyGoal() {
        Goal goal = new Goal();
        
        assertThat(goal).isNotNull();
        assertThat(goal.getUserId()).isNull();
        assertThat(goal.getGoalId()).isNull();
        assertThat(goal.getName()).isNull();
        assertThat(goal.getDescription()).isNull();
        assertThat(goal.getLinkedInstitutions()).isNull();
        assertThat(goal.getCreatedAt()).isNull();
    }

    @Test
    void settersAndGetters_WorkCorrectly() {
        Goal goal = new Goal();
        
        String userId = "user-123";
        String goalId = "goal-456";
        String name = "Emergency Fund";
        String description = "Save 6 months of expenses";
        Map<String, Integer> linkedInstitutions = new HashMap<>();
        linkedInstitutions.put("inst-1", 50);
        linkedInstitutions.put("inst-2", 30);
        Long createdAt = System.currentTimeMillis() / 1000L;

        goal.setUserId(userId);
        goal.setGoalId(goalId);
        goal.setName(name);
        goal.setDescription(description);
        goal.setLinkedInstitutions(linkedInstitutions);
        goal.setCreatedAt(createdAt);

        assertThat(goal.getUserId()).isEqualTo(userId);
        assertThat(goal.getGoalId()).isEqualTo(goalId);
        assertThat(goal.getName()).isEqualTo(name);
        assertThat(goal.getDescription()).isEqualTo(description);
        assertThat(goal.getLinkedInstitutions()).isEqualTo(linkedInstitutions);
        assertThat(goal.getCreatedAt()).isEqualTo(createdAt);
    }

    @Test
    void setUserId_AllowsNullValue() {
        Goal goal = new Goal();
        goal.setUserId("user-123");
        goal.setUserId(null);
        
        assertThat(goal.getUserId()).isNull();
    }

    @Test
    void setGoalId_AllowsNullValue() {
        Goal goal = new Goal();
        goal.setGoalId("goal-456");
        goal.setGoalId(null);
        
        assertThat(goal.getGoalId()).isNull();
    }

    @Test
    void setName_AllowsNullValue() {
        Goal goal = new Goal();
        goal.setName("Test Goal");
        goal.setName(null);
        
        assertThat(goal.getName()).isNull();
    }

    @Test
    void setDescription_AllowsNullValue() {
        Goal goal = new Goal();
        goal.setDescription("Test Description");
        goal.setDescription(null);
        
        assertThat(goal.getDescription()).isNull();
    }

    @Test
    void setLinkedInstitutions_AllowsEmptyMap() {
        Goal goal = new Goal();
        goal.setLinkedInstitutions(new HashMap<>());
        
        assertThat(goal.getLinkedInstitutions()).isEmpty();
    }

    @Test
    void setLinkedInstitutions_AllowsNullValue() {
        Goal goal = new Goal();
        Map<String, Integer> linkedInstitutions = new HashMap<>();
        linkedInstitutions.put("inst-1", 50);
        goal.setLinkedInstitutions(linkedInstitutions);
        goal.setLinkedInstitutions(null);
        
        assertThat(goal.getLinkedInstitutions()).isNull();
    }

    @Test
    void setCreatedAt_AllowsUnixTimestamp() {
        Goal goal = new Goal();
        Long timestamp = 1704067200L; // 2024-01-01T00:00:00Z
        goal.setCreatedAt(timestamp);
        
        assertThat(goal.getCreatedAt()).isEqualTo(timestamp);
    }

    @Test
    void setCreatedAt_AllowsCurrentTimestamp() {
        Goal goal = new Goal();
        Long now = System.currentTimeMillis() / 1000L;
        goal.setCreatedAt(now);
        
        assertThat(goal.getCreatedAt()).isEqualTo(now);
    }

    @Test
    void multipleInstances_AreIndependent() {
        Goal goal1 = new Goal();
        Goal goal2 = new Goal();
        
        goal1.setUserId("user-1");
        goal1.setName("Goal 1");
        
        goal2.setUserId("user-2");
        goal2.setName("Goal 2");
        
        assertThat(goal1.getUserId()).isEqualTo("user-1");
        assertThat(goal1.getName()).isEqualTo("Goal 1");
        assertThat(goal2.getUserId()).isEqualTo("user-2");
        assertThat(goal2.getName()).isEqualTo("Goal 2");
    }

    @Test
    void allFields_CanBeSetAndRetrieved() {
        Goal goal = new Goal();
        
        Map<String, Integer> linkedInstitutions = new HashMap<>();
        linkedInstitutions.put("inst-1", 50);
        linkedInstitutions.put("inst-2", 30);
        linkedInstitutions.put("inst-3", 20);
        
        goal.setUserId("user-123");
        goal.setGoalId("goal-456");
        goal.setName("Vacation Fund");
        goal.setDescription("Save for summer vacation");
        goal.setLinkedInstitutions(linkedInstitutions);
        goal.setCreatedAt(1704067200L);
        
        assertThat(goal.getUserId()).isEqualTo("user-123");
        assertThat(goal.getGoalId()).isEqualTo("goal-456");
        assertThat(goal.getName()).isEqualTo("Vacation Fund");
        assertThat(goal.getDescription()).isEqualTo("Save for summer vacation");
        assertThat(goal.getLinkedInstitutions()).hasSize(3);
        assertThat(goal.getLinkedInstitutions().get("inst-1")).isEqualTo(50);
        assertThat(goal.getLinkedInstitutions().get("inst-2")).isEqualTo(30);
        assertThat(goal.getLinkedInstitutions().get("inst-3")).isEqualTo(20);
        assertThat(goal.getCreatedAt()).isEqualTo(1704067200L);
    }

    @Test
    void linkedInstitutions_CanBeModified() {
        Goal goal = new Goal();
        
        Map<String, Integer> linkedInstitutions = new HashMap<>();
        linkedInstitutions.put("inst-1", 50);
        goal.setLinkedInstitutions(linkedInstitutions);
        
        assertThat(goal.getLinkedInstitutions()).hasSize(1);
        
        goal.getLinkedInstitutions().put("inst-2", 30);
        
        assertThat(goal.getLinkedInstitutions()).hasSize(2);
        assertThat(goal.getLinkedInstitutions().get("inst-2")).isEqualTo(30);
    }
}
