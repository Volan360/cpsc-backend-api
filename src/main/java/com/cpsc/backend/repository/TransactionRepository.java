package com.cpsc.backend.repository;

import com.cpsc.backend.entity.Transaction;
import com.cpsc.backend.exception.InvalidTransactionDataException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;

import java.util.List;
import java.util.stream.Collectors;

@Repository
public class TransactionRepository {

    private static final Logger logger = LoggerFactory.getLogger(TransactionRepository.class);
    
    private final DynamoDbTable<Transaction> transactionTable;

    public TransactionRepository(DynamoDbEnhancedClient enhancedClient,
                                  @Value("${dynamodb.transaction.table.name}") String tableName) {
        if (enhancedClient == null) {
            throw new IllegalArgumentException("DynamoDbEnhancedClient cannot be null");
        }
        if (tableName == null || tableName.trim().isEmpty()) {
            throw new IllegalArgumentException("Table name cannot be null or empty");
        }
        
        this.transactionTable = enhancedClient.table(tableName, TableSchema.fromBean(Transaction.class));
        logger.info("TransactionRepository initialized with table: {}", tableName);
    }

    public void save(Transaction transaction) {
        if (transaction == null) {
            throw new IllegalArgumentException("Transaction cannot be null");
        }
        
        validateTransaction(transaction);
        
        logger.debug("Saving transaction: institutionId={}, transactionId={}", 
            transaction.getInstitutionId(), transaction.getTransactionId());
        
        transactionTable.putItem(transaction);
    }

    /**
     * Find all transactions for an institution, sorted by createdAt descending (newest first)
     */
    public List<Transaction> findAllByInstitutionId(String institutionId) {
        if (institutionId == null || institutionId.trim().isEmpty()) {
            throw new IllegalArgumentException("Institution ID cannot be null or empty");
        }
        
        logger.debug("Finding all transactions for institutionId={}", institutionId);
        
        QueryConditional queryConditional = QueryConditional
                .keyEqualTo(Key.builder().partitionValue(institutionId).build());
        
        QueryEnhancedRequest queryRequest = QueryEnhancedRequest.builder()
                .queryConditional(queryConditional)
                .scanIndexForward(false) // Sort descending (newest first)
                .build();
        
        List<Transaction> transactions = transactionTable.query(queryRequest)
                .items()
                .stream()
                .collect(Collectors.toList());
        
        logger.debug("Found {} transactions for institutionId={}", transactions.size(), institutionId);
        
        return transactions;
    }

    /**
     * Delete a transaction by institutionId and createdAt
     */
    public void delete(String institutionId, Long createdAt) {
        if (institutionId == null || institutionId.trim().isEmpty()) {
            throw new IllegalArgumentException("Institution ID cannot be null or empty");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("CreatedAt cannot be null");
        }
        
        logger.debug("Deleting transaction: institutionId={}, createdAt={}", institutionId, createdAt);
        
        Key key = Key.builder()
                .partitionValue(institutionId)
                .sortValue(createdAt.toString())
                .build();
        
        transactionTable.deleteItem(key);
    }

    private void validateTransaction(Transaction transaction) {
        if (transaction.getInstitutionId() == null || transaction.getInstitutionId().trim().isEmpty()) {
            throw new InvalidTransactionDataException("Institution ID cannot be null or empty");
        }
        if (transaction.getUserId() == null || transaction.getUserId().trim().isEmpty()) {
            throw new InvalidTransactionDataException("User ID cannot be null or empty");
        }
        if (transaction.getTransactionId() == null || transaction.getTransactionId().trim().isEmpty()) {
            throw new InvalidTransactionDataException("Transaction ID cannot be null or empty");
        }
        if (transaction.getType() == null || transaction.getType().trim().isEmpty()) {
            throw new InvalidTransactionDataException("Transaction type cannot be null or empty");
        }
        if (transaction.getAmount() == null) {
            throw new InvalidTransactionDataException("Transaction amount cannot be null");
        }
        if (transaction.getCreatedAt() == null) {
            throw new InvalidTransactionDataException("Transaction createdAt cannot be null");
        }
    }
}
