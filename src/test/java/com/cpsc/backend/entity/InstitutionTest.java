package com.cpsc.backend.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InstitutionTest {

    @Test
    void constructor_CreatesEmptyInstitution() {
        Institution institution = new Institution();
        
        assertThat(institution).isNotNull();
        assertThat(institution.getUserId()).isNull();
        assertThat(institution.getInstitutionId()).isNull();
        assertThat(institution.getInstitutionName()).isNull();
        assertThat(institution.getStartingBalance()).isNull();
        assertThat(institution.getCreatedAt()).isNull();
    }

    @Test
    void settersAndGetters_WorkCorrectly() {
        Institution institution = new Institution();
        
        String userId = "user-123";
        String institutionId = "inst-456";
        String institutionName = "Test Bank";
        Double startingBalance = 1000.0;
        Long createdAt = System.currentTimeMillis() / 1000L;

        institution.setUserId(userId);
        institution.setInstitutionId(institutionId);
        institution.setInstitutionName(institutionName);
        institution.setStartingBalance(startingBalance);
        institution.setCreatedAt(createdAt);

        assertThat(institution.getUserId()).isEqualTo(userId);
        assertThat(institution.getInstitutionId()).isEqualTo(institutionId);
        assertThat(institution.getInstitutionName()).isEqualTo(institutionName);
        assertThat(institution.getStartingBalance()).isEqualTo(startingBalance);
        assertThat(institution.getCreatedAt()).isEqualTo(createdAt);
    }

    @Test
    void setUserId_AllowsNullValue() {
        Institution institution = new Institution();
        institution.setUserId("user-123");
        institution.setUserId(null);
        
        assertThat(institution.getUserId()).isNull();
    }

    @Test
    void setInstitutionId_AllowsNullValue() {
        Institution institution = new Institution();
        institution.setInstitutionId("inst-456");
        institution.setInstitutionId(null);
        
        assertThat(institution.getInstitutionId()).isNull();
    }

    @Test
    void setInstitutionName_AllowsNullValue() {
        Institution institution = new Institution();
        institution.setInstitutionName("Test Bank");
        institution.setInstitutionName(null);
        
        assertThat(institution.getInstitutionName()).isNull();
    }

    @Test
    void setStartingBalance_AllowsZero() {
        Institution institution = new Institution();
        institution.setStartingBalance(0.0);
        
        assertThat(institution.getStartingBalance()).isEqualTo(0.0);
    }

    @Test
    void setStartingBalance_AllowsNegativeValues() {
        Institution institution = new Institution();
        institution.setStartingBalance(-100.0);
        
        assertThat(institution.getStartingBalance()).isEqualTo(-100.0);
    }

    @Test
    void setStartingBalance_AllowsLargeValues() {
        Institution institution = new Institution();
        Double largeValue = 999999999.99;
        institution.setStartingBalance(largeValue);
        
        assertThat(institution.getStartingBalance()).isEqualTo(largeValue);
    }

    @Test
    void setCreatedAt_AllowsUnixTimestamp() {
        Institution institution = new Institution();
        Long timestamp = 1704067200L; // 2024-01-01T00:00:00Z
        institution.setCreatedAt(timestamp);
        
        assertThat(institution.getCreatedAt()).isEqualTo(timestamp);
    }

    @Test
    void setCreatedAt_AllowsCurrentTimestamp() {
        Institution institution = new Institution();
        Long now = System.currentTimeMillis() / 1000L;
        institution.setCreatedAt(now);
        
        assertThat(institution.getCreatedAt()).isEqualTo(now);
    }

    @Test
    void multipleInstances_AreIndependent() {
        Institution institution1 = new Institution();
        Institution institution2 = new Institution();
        
        institution1.setUserId("user-1");
        institution1.setInstitutionName("Bank 1");
        
        institution2.setUserId("user-2");
        institution2.setInstitutionName("Bank 2");
        
        assertThat(institution1.getUserId()).isEqualTo("user-1");
        assertThat(institution1.getInstitutionName()).isEqualTo("Bank 1");
        assertThat(institution2.getUserId()).isEqualTo("user-2");
        assertThat(institution2.getInstitutionName()).isEqualTo("Bank 2");
    }

    @Test
    void allFields_CanBeSetAndRetrieved() {
        Institution institution = new Institution();
        
        institution.setUserId("user-123");
        institution.setInstitutionId("inst-456");
        institution.setInstitutionName("Chase Bank");
        institution.setStartingBalance(5000.50);
        institution.setCreatedAt(1704067200L);
        
        assertThat(institution.getUserId()).isEqualTo("user-123");
        assertThat(institution.getInstitutionId()).isEqualTo("inst-456");
        assertThat(institution.getInstitutionName()).isEqualTo("Chase Bank");
        assertThat(institution.getStartingBalance()).isEqualTo(5000.50);
        assertThat(institution.getCreatedAt()).isEqualTo(1704067200L);
    }
}
