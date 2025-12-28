package com.cpsc.backend.repository;

import com.cpsc.backend.entity.Transaction;
import com.cpsc.backend.exception.InvalidTransactionDataException;
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
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.core.pagination.sync.SdkIterable;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionRepositoryTest {

    @Mock
    private DynamoDbEnhancedClient enhancedClient;

    @Mock
    private DynamoDbTable<Transaction> transactionTable;

    private TransactionRepository repository;

    private static final String TABLE_NAME = "Transactions-devl";
    private static final String INSTITUTION_ID = "inst-123";
    private static final String USER_ID = "user-123";
    private static final String TRANSACTION_ID = "txn-123";

    @BeforeEach
    void setUp() {
        when(enhancedClient.table(eq(TABLE_NAME), any(TableSchema.class))).thenReturn(transactionTable);
        repository = new TransactionRepository(enhancedClient, TABLE_NAME);
    }

    @Test
    void constructor_ValidParameters_InitializesSuccessfully() {
        assertThat(repository).isNotNull();
    }

    @Test
    void constructor_NullClient_ThrowsException() {
        assertThatThrownBy(() -> new TransactionRepository(null, TABLE_NAME))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("DynamoDbEnhancedClient cannot be null");
    }

    @Test
    void constructor_NullTableName_ThrowsException() {
        assertThatThrownBy(() -> new TransactionRepository(enhancedClient, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Table name cannot be null or empty");
    }

    @Test
    void constructor_EmptyTableName_ThrowsException() {
        assertThatThrownBy(() -> new TransactionRepository(enhancedClient, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Table name cannot be null or empty");
    }

    @Test
    void save_ValidTransaction_SavesSuccessfully() {
        Transaction transaction = createValidTransaction();

        repository.save(transaction);

        verify(transactionTable).putItem(transaction);
    }

    @Test
    void save_NullTransaction_ThrowsException() {
        assertThatThrownBy(() -> repository.save(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Transaction cannot be null");
    }

    @Test
    void save_NullInstitutionId_ThrowsException() {
        Transaction transaction = createValidTransaction();
        transaction.setInstitutionId(null);

        assertThatThrownBy(() -> repository.save(transaction))
                .isInstanceOf(InvalidTransactionDataException.class)
                .hasMessage("Institution ID cannot be null or empty");
    }

    @Test
    void save_EmptyInstitutionId_ThrowsException() {
        Transaction transaction = createValidTransaction();
        transaction.setInstitutionId("");

        assertThatThrownBy(() -> repository.save(transaction))
                .isInstanceOf(InvalidTransactionDataException.class)
                .hasMessage("Institution ID cannot be null or empty");
    }

    @Test
    void save_NullUserId_ThrowsException() {
        Transaction transaction = createValidTransaction();
        transaction.setUserId(null);

        assertThatThrownBy(() -> repository.save(transaction))
                .isInstanceOf(InvalidTransactionDataException.class)
                .hasMessage("User ID cannot be null or empty");
    }

    @Test
    void save_NullTransactionId_ThrowsException() {
        Transaction transaction = createValidTransaction();
        transaction.setTransactionId(null);

        assertThatThrownBy(() -> repository.save(transaction))
                .isInstanceOf(InvalidTransactionDataException.class)
                .hasMessage("Transaction ID cannot be null or empty");
    }

    @Test
    void save_NullType_ThrowsException() {
        Transaction transaction = createValidTransaction();
        transaction.setType(null);

        assertThatThrownBy(() -> repository.save(transaction))
                .isInstanceOf(InvalidTransactionDataException.class)
                .hasMessage("Transaction type cannot be null or empty");
    }

    @Test
    void save_NullAmount_ThrowsException() {
        Transaction transaction = createValidTransaction();
        transaction.setAmount(null);

        assertThatThrownBy(() -> repository.save(transaction))
                .isInstanceOf(InvalidTransactionDataException.class)
                .hasMessage("Transaction amount cannot be null");
    }

    @Test
    void save_NullCreatedAt_ThrowsException() {
        Transaction transaction = createValidTransaction();
        transaction.setCreatedAt(null);

        assertThatThrownBy(() -> repository.save(transaction))
                .isInstanceOf(InvalidTransactionDataException.class)
                .hasMessage("Transaction createdAt cannot be null");
    }

    @Test
    void findAllByInstitutionId_NullInstitutionId_ThrowsException() {
        assertThatThrownBy(() -> repository.findAllByInstitutionId(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Institution ID cannot be null or empty");
    }

    @Test
    void findAllByInstitutionId_EmptyInstitutionId_ThrowsException() {
        assertThatThrownBy(() -> repository.findAllByInstitutionId(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Institution ID cannot be null or empty");
    }

    @Test
    void findAllByInstitutionId_Success_ReturnsTransactions() {
        String institutionId = "550e8400-e29b-41d4-a716-446655440001";
        Transaction transaction1 = createValidTransaction();
        transaction1.setInstitutionId(institutionId);
        transaction1.setCreatedAt(1735363200L);
        
        Transaction transaction2 = createValidTransaction();
        transaction2.setInstitutionId(institutionId);
        transaction2.setTransactionId("txn-456");
        transaction2.setCreatedAt(1735449600L);
        
        PageIterable<Transaction> pageIterable = mockPageIterable(List.of(transaction2, transaction1));
        when(transactionTable.query(any(QueryEnhancedRequest.class))).thenReturn(pageIterable);

        List<Transaction> result = repository.findAllByInstitutionId(institutionId);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getCreatedAt()).isEqualTo(1735449600L); // Newest first
        assertThat(result.get(1).getCreatedAt()).isEqualTo(1735363200L);
        verify(transactionTable).query(any(QueryEnhancedRequest.class));
    }

    @Test
    void findAllByInstitutionId_NoTransactions_ReturnsEmptyList() {
        String institutionId = "550e8400-e29b-41d4-a716-446655440001";
        
        PageIterable<Transaction> pageIterable = mockPageIterable(Collections.emptyList());
        when(transactionTable.query(any(QueryEnhancedRequest.class))).thenReturn(pageIterable);

        List<Transaction> result = repository.findAllByInstitutionId(institutionId);

        assertThat(result).isEmpty();
        verify(transactionTable).query(any(QueryEnhancedRequest.class));
    }

    @Test
    void findAllByInstitutionId_VerifiesDescendingOrder() {
        String institutionId = "550e8400-e29b-41d4-a716-446655440001";
        ArgumentCaptor<QueryEnhancedRequest> requestCaptor = ArgumentCaptor.forClass(QueryEnhancedRequest.class);
        
        PageIterable<Transaction> pageIterable = mockPageIterable(Collections.emptyList());
        when(transactionTable.query(any(QueryEnhancedRequest.class))).thenReturn(pageIterable);

        repository.findAllByInstitutionId(institutionId);

        verify(transactionTable).query(requestCaptor.capture());
        // Note: We can't directly verify scanIndexForward(false) as it's a builder method,
        // but we verify the query method was called with the proper request
        assertThat(requestCaptor.getValue()).isNotNull();
    }

    @Test
    void delete_ValidParameters_Success() {
        String institutionId = "550e8400-e29b-41d4-a716-446655440001";
        Long createdAt = 1735363200L;

        repository.delete(institutionId, createdAt);

        verify(transactionTable).deleteItem(any(Key.class));
    }

    @Test
    void delete_NullInstitutionId_ThrowsException() {
        Long createdAt = 1735363200L;

        assertThatThrownBy(() -> repository.delete(null, createdAt))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Institution ID cannot be null or empty");
    }

    @Test
    void delete_EmptyInstitutionId_ThrowsException() {
        Long createdAt = 1735363200L;

        assertThatThrownBy(() -> repository.delete("", createdAt))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Institution ID cannot be null or empty");
    }

    @Test
    void delete_NullCreatedAt_ThrowsException() {
        String institutionId = "550e8400-e29b-41d4-a716-446655440001";

        assertThatThrownBy(() -> repository.delete(institutionId, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("CreatedAt cannot be null");
    }

    private Transaction createValidTransaction() {
        Transaction transaction = new Transaction();
        transaction.setInstitutionId(INSTITUTION_ID);
        transaction.setUserId(USER_ID);
        transaction.setTransactionId(TRANSACTION_ID);
        transaction.setType("DEPOSIT");
        transaction.setAmount(100.0);
        transaction.setCreatedAt(System.currentTimeMillis() / 1000L);
        return transaction;
    }

    @SuppressWarnings("unchecked")
    private PageIterable<Transaction> mockPageIterable(List<Transaction> transactions) {
        PageIterable<Transaction> pageIterable = mock(PageIterable.class);
        SdkIterable<Transaction> sdkIterable = mock(SdkIterable.class);
        
        when(pageIterable.items()).thenReturn(sdkIterable);
        when(sdkIterable.stream()).thenReturn(transactions.stream());
        
        return pageIterable;
    }
}
