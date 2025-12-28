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
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
}
