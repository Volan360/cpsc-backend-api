package com.cpsc.backend.service;

import com.cpsc.backend.entity.Institution;
import com.cpsc.backend.entity.Transaction;
import com.cpsc.backend.exception.InvalidTransactionDataException;
import com.cpsc.backend.model.CreateTransactionRequest;
import com.cpsc.backend.model.TransactionResponse;
import com.cpsc.backend.repository.InstitutionRepository;
import com.cpsc.backend.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private InstitutionRepository institutionRepository;

    @InjectMocks
    private TransactionService transactionService;

    private CreateTransactionRequest validRequest;
    private Institution validInstitution;
    private static final String USER_ID = "550e8400-e29b-41d4-a716-446655440000";
    private static final String INSTITUTION_ID = "550e8400-e29b-41d4-a716-446655440001";

    @BeforeEach
    void setUp() {
        validRequest = new CreateTransactionRequest();
        validRequest.setType(CreateTransactionRequest.TypeEnum.DEPOSIT);
        validRequest.setAmount(100.50);
        validRequest.setTags(List.of("grocery", "food"));
        validRequest.setDescription("Weekly groceries");

        validInstitution = new Institution();
        validInstitution.setUserId(USER_ID);
        validInstitution.setInstitutionId(INSTITUTION_ID);
        validInstitution.setInstitutionName("Test Bank");
        validInstitution.setStartingBalance(1000.0);
        validInstitution.setCreatedAt(System.currentTimeMillis() / 1000L);
    }

    @Test
    void createTransaction_Success() {
        when(institutionRepository.findByUserIdAndInstitutionId(USER_ID, INSTITUTION_ID))
                .thenReturn(validInstitution);
        doNothing().when(transactionRepository).save(any(Transaction.class));

        TransactionResponse response = transactionService.createTransaction(USER_ID, INSTITUTION_ID, validRequest);

        assertThat(response).isNotNull();
        assertThat(response.getTransactionId()).isNotNull();
        assertThat(response.getInstitutionId()).isEqualTo(UUID.fromString(INSTITUTION_ID));
        assertThat(response.getType()).isEqualTo(TransactionResponse.TypeEnum.DEPOSIT);
        assertThat(response.getAmount()).isEqualTo(100.50);
        assertThat(response.getTags()).containsExactly("grocery", "food");
        assertThat(response.getDescription()).isEqualTo("Weekly groceries");
        verify(transactionRepository).save(any(Transaction.class));
    }

    @Test
    void createTransaction_WithdrawalType_Success() {
        validRequest.setType(CreateTransactionRequest.TypeEnum.WITHDRAWAL);
        when(institutionRepository.findByUserIdAndInstitutionId(USER_ID, INSTITUTION_ID))
                .thenReturn(validInstitution);
        doNothing().when(transactionRepository).save(any(Transaction.class));

        TransactionResponse response = transactionService.createTransaction(USER_ID, INSTITUTION_ID, validRequest);

        assertThat(response.getType()).isEqualTo(TransactionResponse.TypeEnum.WITHDRAWAL);
    }

    @Test
    void createTransaction_NoTagsOrDescription_Success() {
        validRequest.setTags(null);
        validRequest.setDescription(null);
        when(institutionRepository.findByUserIdAndInstitutionId(USER_ID, INSTITUTION_ID))
                .thenReturn(validInstitution);
        doNothing().when(transactionRepository).save(any(Transaction.class));

        TransactionResponse response = transactionService.createTransaction(USER_ID, INSTITUTION_ID, validRequest);

        assertThat(response.getTags()).isNull();
        assertThat(response.getDescription()).isNull();
    }

    @Test
    void createTransaction_NullUserId_ThrowsException() {
        assertThatThrownBy(() -> transactionService.createTransaction(null, INSTITUTION_ID, validRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("User ID cannot be null or empty");
    }

    @Test
    void createTransaction_EmptyUserId_ThrowsException() {
        assertThatThrownBy(() -> transactionService.createTransaction("", INSTITUTION_ID, validRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("User ID cannot be null or empty");
    }

    @Test
    void createTransaction_NullInstitutionId_ThrowsException() {
        assertThatThrownBy(() -> transactionService.createTransaction(USER_ID, null, validRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Institution ID cannot be null or empty");
    }

    @Test
    void createTransaction_EmptyInstitutionId_ThrowsException() {
        assertThatThrownBy(() -> transactionService.createTransaction(USER_ID, "", validRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Institution ID cannot be null or empty");
    }

    @Test
    void createTransaction_NullType_ThrowsException() {
        validRequest.setType(null);

        assertThatThrownBy(() -> transactionService.createTransaction(USER_ID, INSTITUTION_ID, validRequest))
                .isInstanceOf(InvalidTransactionDataException.class)
                .hasMessage("Transaction type cannot be null");
    }

    @Test
    void createTransaction_NullAmount_ThrowsException() {
        validRequest.setAmount(null);

        assertThatThrownBy(() -> transactionService.createTransaction(USER_ID, INSTITUTION_ID, validRequest))
                .isInstanceOf(InvalidTransactionDataException.class)
                .hasMessage("Transaction amount cannot be null");
    }

    @Test
    void createTransaction_ZeroAmount_ThrowsException() {
        validRequest.setAmount(0.0);

        assertThatThrownBy(() -> transactionService.createTransaction(USER_ID, INSTITUTION_ID, validRequest))
                .isInstanceOf(InvalidTransactionDataException.class)
                .hasMessage("Transaction amount must be greater than zero");
    }

    @Test
    void createTransaction_NegativeAmount_ThrowsException() {
        validRequest.setAmount(-50.0);

        assertThatThrownBy(() -> transactionService.createTransaction(USER_ID, INSTITUTION_ID, validRequest))
                .isInstanceOf(InvalidTransactionDataException.class)
                .hasMessage("Transaction amount must be greater than zero");
    }

    @Test
    void createTransaction_AmountTooLarge_ThrowsException() {
        validRequest.setAmount(1_000_000_001.0);

        assertThatThrownBy(() -> transactionService.createTransaction(USER_ID, INSTITUTION_ID, validRequest))
                .isInstanceOf(InvalidTransactionDataException.class)
                .hasMessage("Transaction amount cannot exceed 1.0E9");
    }

    @Test
    void createTransaction_NaNAmount_ThrowsException() {
        validRequest.setAmount(Double.NaN);

        assertThatThrownBy(() -> transactionService.createTransaction(USER_ID, INSTITUTION_ID, validRequest))
                .isInstanceOf(InvalidTransactionDataException.class)
                .hasMessage("Transaction amount must be a valid number");
    }

    @Test
    void createTransaction_InfiniteAmount_ThrowsException() {
        validRequest.setAmount(Double.POSITIVE_INFINITY);

        assertThatThrownBy(() -> transactionService.createTransaction(USER_ID, INSTITUTION_ID, validRequest))
                .isInstanceOf(InvalidTransactionDataException.class)
                .hasMessage("Transaction amount must be a valid number");
    }

    @Test
    void createTransaction_DynamoDbException_ThrowsException() {
        when(institutionRepository.findByUserIdAndInstitutionId(USER_ID, INSTITUTION_ID))
                .thenReturn(validInstitution);
        doThrow(DynamoDbException.builder().message("DynamoDB error").build())
                .when(transactionRepository).save(any(Transaction.class));

        assertThatThrownBy(() -> transactionService.createTransaction(USER_ID, INSTITUTION_ID, validRequest))
                .isInstanceOf(DynamoDbException.class);
    }

    @Test
    void createTransaction_GenericException_ThrowsRuntimeException() {
        when(institutionRepository.findByUserIdAndInstitutionId(USER_ID, INSTITUTION_ID))
                .thenReturn(validInstitution);
        doThrow(new RuntimeException("Unexpected error"))
                .when(transactionRepository).save(any(Transaction.class));

        assertThatThrownBy(() -> transactionService.createTransaction(USER_ID, INSTITUTION_ID, validRequest))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Failed to create transaction");
    }

    @Test
    void getInstitutionTransactions_ReturnsTransactions() {
        Transaction transaction = new Transaction();
        transaction.setTransactionId(UUID.randomUUID().toString());
        transaction.setInstitutionId(INSTITUTION_ID);
        transaction.setUserId(USER_ID);
        transaction.setType("DEPOSIT");
        transaction.setAmount(100.0);
        transaction.setTags(List.of("test"));
        transaction.setDescription("Test transaction");
        transaction.setCreatedAt(System.currentTimeMillis() / 1000L);

        when(institutionRepository.findByUserIdAndInstitutionId(USER_ID, INSTITUTION_ID))
                .thenReturn(validInstitution);
        when(transactionRepository.findAllByInstitutionId(INSTITUTION_ID))
                .thenReturn(List.of(transaction));

        List<TransactionResponse> responses = transactionService.getInstitutionTransactions(USER_ID, INSTITUTION_ID);

        assertThat(responses).hasSize(1);
        TransactionResponse response = responses.get(0);
        assertThat(response.getType()).isEqualTo(TransactionResponse.TypeEnum.DEPOSIT);
        assertThat(response.getAmount()).isEqualTo(100.0);
    }

    @Test
    void getInstitutionTransactions_EmptyList_ReturnsEmptyList() {
        when(institutionRepository.findByUserIdAndInstitutionId(USER_ID, INSTITUTION_ID))
                .thenReturn(validInstitution);
        when(transactionRepository.findAllByInstitutionId(INSTITUTION_ID))
                .thenReturn(Collections.emptyList());

        List<TransactionResponse> responses = transactionService.getInstitutionTransactions(USER_ID, INSTITUTION_ID);

        assertThat(responses).isEmpty();
    }

    @Test
    void getInstitutionTransactions_NullUserId_ThrowsException() {
        assertThatThrownBy(() -> transactionService.getInstitutionTransactions(null, INSTITUTION_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("User ID cannot be null or empty");
    }

    @Test
    void getInstitutionTransactions_NullInstitutionId_ThrowsException() {
        assertThatThrownBy(() -> transactionService.getInstitutionTransactions(USER_ID, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Institution ID cannot be null or empty");
    }

    @Test
    void getInstitutionTransactions_DynamoDbException_ThrowsException() {
        when(institutionRepository.findByUserIdAndInstitutionId(USER_ID, INSTITUTION_ID))
                .thenReturn(validInstitution);
        when(transactionRepository.findAllByInstitutionId(INSTITUTION_ID))
                .thenThrow(DynamoDbException.builder().message("DynamoDB error").build());

        assertThatThrownBy(() -> transactionService.getInstitutionTransactions(USER_ID, INSTITUTION_ID))
                .isInstanceOf(DynamoDbException.class);
    }

    @Test
    void getInstitutionTransactions_GenericException_ThrowsRuntimeException() {
        when(institutionRepository.findByUserIdAndInstitutionId(USER_ID, INSTITUTION_ID))
                .thenReturn(validInstitution);
        when(transactionRepository.findAllByInstitutionId(INSTITUTION_ID))
                .thenThrow(new RuntimeException("Unexpected error"));

        assertThatThrownBy(() -> transactionService.getInstitutionTransactions(USER_ID, INSTITUTION_ID))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Failed to fetch transactions");
    }
}
