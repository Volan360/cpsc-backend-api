package com.cpsc.backend.repository;

import com.cpsc.backend.entity.Institution;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.PageIterable;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InstitutionRepositoryTest {

    @Mock
    private DynamoDbEnhancedClient enhancedClient;

    @Mock
    private DynamoDbTable<Institution> institutionTable;

    @Mock
    private PageIterable<Institution> pageIterable;

    @Mock
    private Page<Institution> page;

    private InstitutionRepository repository;

    private static final String TABLE_NAME = "test-institutions";
    private static final String USER_ID = "user-123";
    private static final String INSTITUTION_ID = "inst-456";

    @BeforeEach
    void setUp() {
        when(enhancedClient.table(eq(TABLE_NAME), any(TableSchema.class))).thenReturn(institutionTable);
        repository = new InstitutionRepository(enhancedClient, TABLE_NAME);
    }

    @Test
    void constructor_NullClient_ThrowsException() {
        assertThatThrownBy(() -> new InstitutionRepository(null, TABLE_NAME))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("DynamoDbEnhancedClient cannot be null");
    }

    @Test
    void constructor_NullTableName_ThrowsException() {
        assertThatThrownBy(() -> new InstitutionRepository(enhancedClient, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Table name cannot be null or empty");
    }

    @Test
    void constructor_EmptyTableName_ThrowsException() {
        assertThatThrownBy(() -> new InstitutionRepository(enhancedClient, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Table name cannot be null or empty");
    }

    @Test
    void save_ValidInstitution_SavesSuccessfully() {
        Institution institution = createTestInstitution();
        doNothing().when(institutionTable).putItem(institution);

        repository.save(institution);

        verify(institutionTable).putItem(institution);
    }

    @Test
    void save_NullInstitution_ThrowsException() {
        assertThatThrownBy(() -> repository.save(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Institution cannot be null");
    }

    @Test
    void save_NullUserId_ThrowsException() {
        Institution institution = createTestInstitution();
        institution.setUserId(null);

        assertThatThrownBy(() -> repository.save(institution))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Institution userId cannot be null or empty");
    }

    @Test
    void save_EmptyUserId_ThrowsException() {
        Institution institution = createTestInstitution();
        institution.setUserId("");

        assertThatThrownBy(() -> repository.save(institution))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Institution userId cannot be null or empty");
    }

    @Test
    void save_NullInstitutionId_ThrowsException() {
        Institution institution = createTestInstitution();
        institution.setInstitutionId(null);

        assertThatThrownBy(() -> repository.save(institution))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Institution institutionId cannot be null or empty");
    }

    @Test
    void findByUserIdAndInstitutionId_ValidKeys_ReturnsInstitution() {
        Institution institution = createTestInstitution();
        when(institutionTable.getItem(any(Key.class))).thenReturn(institution);

        Institution result = repository.findByUserIdAndInstitutionId(USER_ID, INSTITUTION_ID);

        assertThat(result).isNotNull();
        assertThat(result.getUserId()).isEqualTo(USER_ID);
        assertThat(result.getInstitutionId()).isEqualTo(INSTITUTION_ID);

        ArgumentCaptor<Key> keyCaptor = ArgumentCaptor.forClass(Key.class);
        verify(institutionTable).getItem(keyCaptor.capture());
    }

    @Test
    void findByUserIdAndInstitutionId_NullUserId_ThrowsException() {
        assertThatThrownBy(() -> repository.findByUserIdAndInstitutionId(null, INSTITUTION_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("User ID cannot be null or empty");
    }

    @Test
    void findByUserIdAndInstitutionId_NullInstitutionId_ThrowsException() {
        assertThatThrownBy(() -> repository.findByUserIdAndInstitutionId(USER_ID, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Institution ID cannot be null or empty");
    }

    @Test
    void findAllByUserId_ValidUserId_ReturnsInstitutions() {
        Institution institution = createTestInstitution();
        
        when(institutionTable.query(any(QueryConditional.class))).thenReturn(pageIterable);
        when(pageIterable.items()).thenReturn(List.of(institution).stream()::iterator);

        List<Institution> results = repository.findAllByUserId(USER_ID);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getUserId()).isEqualTo(USER_ID);
        verify(institutionTable).query(any(QueryConditional.class));
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
    void findAllByUserIdPaginated_ValidRequest_ReturnsPage() {
        Institution institution = createTestInstitution();
        Map<String, AttributeValue> lastKey = new HashMap<>();
        lastKey.put("userId", AttributeValue.builder().s(USER_ID).build());
        
        when(institutionTable.query(any(QueryEnhancedRequest.class))).thenReturn(pageIterable);
        when(pageIterable.stream()).thenReturn(List.of(page).stream());
        when(page.items()).thenReturn(List.of(institution));
        when(page.lastEvaluatedKey()).thenReturn(lastKey);

        InstitutionRepository.PaginatedResult<Institution> result = 
                repository.findAllByUserIdPaginated(USER_ID, 10, null);

        assertThat(result).isNotNull();
        assertThat(result.getItems()).hasSize(1);
        assertThat(result.hasMore()).isTrue();
        assertThat(result.getLastEvaluatedKey()).isEqualTo(lastKey);
    }

    @Test
    void findAllByUserIdPaginated_NoMoreResults_ReturnsPageWithoutKey() {
        Institution institution = createTestInstitution();
        
        when(institutionTable.query(any(QueryEnhancedRequest.class))).thenReturn(pageIterable);
        when(pageIterable.stream()).thenReturn(List.of(page).stream());
        when(page.items()).thenReturn(List.of(institution));
        when(page.lastEvaluatedKey()).thenReturn(null);

        InstitutionRepository.PaginatedResult<Institution> result = 
                repository.findAllByUserIdPaginated(USER_ID, 10, null);

        assertThat(result).isNotNull();
        assertThat(result.getItems()).hasSize(1);
        assertThat(result.hasMore()).isFalse();
        assertThat(result.getLastEvaluatedKey()).isNull();
    }

    @Test
    void findAllByUserIdPaginated_NullUserId_ThrowsException() {
        assertThatThrownBy(() -> repository.findAllByUserIdPaginated(null, 10, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("User ID cannot be null or empty");
    }

    @Test
    void findAllByUserIdPaginated_InvalidLimit_ThrowsException() {
        assertThatThrownBy(() -> repository.findAllByUserIdPaginated(USER_ID, 0, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Limit must be between 1 and 100");
    }

    @Test
    void delete_ValidKeys_DeletesSuccessfully() {
        repository.delete(USER_ID, INSTITUTION_ID);

        ArgumentCaptor<Key> keyCaptor = ArgumentCaptor.forClass(Key.class);
        verify(institutionTable).deleteItem(keyCaptor.capture());
    }

    @Test
    void delete_NullUserId_ThrowsException() {
        assertThatThrownBy(() -> repository.delete(null, INSTITUTION_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("User ID cannot be null or empty");
    }

    @Test
    void delete_EmptyUserId_ThrowsException() {
        assertThatThrownBy(() -> repository.delete("", INSTITUTION_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("User ID cannot be null or empty");
    }

    @Test
    void delete_NullInstitutionId_ThrowsException() {
        assertThatThrownBy(() -> repository.delete(USER_ID, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Institution ID cannot be null or empty");
    }

    @Test
    void delete_EmptyInstitutionId_ThrowsException() {
        assertThatThrownBy(() -> repository.delete(USER_ID, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Institution ID cannot be null or empty");
    }

    private Institution createTestInstitution() {
        Institution institution = new Institution();
        institution.setUserId(USER_ID);
        institution.setInstitutionId(INSTITUTION_ID);
        institution.setInstitutionName("Test Bank");
        institution.setStartingBalance(1000.0);
        institution.setCreatedAt(System.currentTimeMillis() / 1000L);
        return institution;
    }
}
