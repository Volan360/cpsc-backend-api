package com.cpsc.backend.service;

import com.cpsc.backend.entity.Transaction;
import com.cpsc.backend.exception.InstitutionNotFoundException;
import com.cpsc.backend.exception.InvalidTransactionDataException;
import com.cpsc.backend.model.CreateTransactionRequest;
import com.cpsc.backend.model.TransactionResponse;
import com.cpsc.backend.repository.InstitutionRepository;
import com.cpsc.backend.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class TransactionService {

    private static final Logger logger = LoggerFactory.getLogger(TransactionService.class);
    private static final double MAX_TRANSACTION_AMOUNT = 1_000_000_000.0; // 1 billion
    
    private final TransactionRepository transactionRepository;
    private final InstitutionRepository institutionRepository;

    public TransactionService(TransactionRepository transactionRepository, InstitutionRepository institutionRepository) {
        this.transactionRepository = transactionRepository;
        this.institutionRepository = institutionRepository;
    }

    public TransactionResponse createTransaction(String userId, String institutionId, CreateTransactionRequest request) {
        if (userId == null || userId.trim().isEmpty()) {
            throw new IllegalArgumentException("User ID cannot be null or empty");
        }
        
        if (institutionId == null || institutionId.trim().isEmpty()) {
            throw new IllegalArgumentException("Institution ID cannot be null or empty");
        }
        
        // Validate the institution exists and belongs to the user
        institutionRepository.findByUserIdAndInstitutionId(userId, institutionId);
        
        // Validate transaction data
        validateTransactionRequest(request);
        
        try {
            Transaction transaction = new Transaction();
            transaction.setTransactionId(UUID.randomUUID().toString());
            transaction.setInstitutionId(institutionId);
            transaction.setUserId(userId);
            transaction.setType(request.getType().getValue());
            transaction.setAmount(request.getAmount());
            transaction.setTags(request.getTags());
            transaction.setDescription(request.getDescription());
            transaction.setCreatedAt(Instant.now().getEpochSecond());

            logger.info("Creating transaction for institution {} with type {} and amount {}", 
                institutionId, request.getType(), request.getAmount());
            
            transactionRepository.save(transaction);
            
            logger.info("Successfully created transaction {} for institution {}", 
                transaction.getTransactionId(), institutionId);

            return mapToResponse(transaction);
            
        } catch (DynamoDbException e) {
            logger.error("DynamoDB error while creating transaction for institution {}: {}", 
                institutionId, e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error while creating transaction for institution {}: {}", 
                institutionId, e.getMessage(), e);
            throw new RuntimeException("Failed to create transaction", e);
        }
    }

    public List<TransactionResponse> getInstitutionTransactions(String userId, String institutionId) {
        if (userId == null || userId.trim().isEmpty()) {
            throw new IllegalArgumentException("User ID cannot be null or empty");
        }
        
        if (institutionId == null || institutionId.trim().isEmpty()) {
            throw new IllegalArgumentException("Institution ID cannot be null or empty");
        }
        
        // Validate the institution exists and belongs to the user
        institutionRepository.findByUserIdAndInstitutionId(userId, institutionId);
        
        try {
            logger.debug("Fetching transactions for institution {}", institutionId);
            
            List<Transaction> transactions = transactionRepository.findAllByInstitutionId(institutionId);
            
            logger.info("Found {} transactions for institution {}", transactions.size(), institutionId);
            
            return transactions.stream()
                    .map(this::mapToResponse)
                    .collect(Collectors.toList());
                    
        } catch (DynamoDbException e) {
            logger.error("DynamoDB error while fetching transactions for institution {}: {}", 
                institutionId, e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error while fetching transactions for institution {}: {}", 
                institutionId, e.getMessage(), e);
            throw new RuntimeException("Failed to fetch transactions", e);
        }
    }

    public void deleteTransaction(String userId, UUID institutionId, UUID transactionId) {
        if (userId == null || userId.trim().isEmpty()) {
            throw new IllegalArgumentException("User ID cannot be null or empty");
        }
        if (institutionId == null) {
            throw new IllegalArgumentException("Institution ID cannot be null");
        }
        if (transactionId == null) {
            throw new IllegalArgumentException("Transaction ID cannot be null");
        }
        
        String institutionIdStr = institutionId.toString();
        
        // Validate the institution exists and belongs to the user
        institutionRepository.findByUserIdAndInstitutionId(userId, institutionIdStr);
        
        try {
            logger.debug("Fetching transaction {} for institution {} to verify ownership", 
                transactionId, institutionIdStr);
            
            // Get all transactions for this institution to find the one with matching transactionId
            List<Transaction> transactions = transactionRepository.findAllByInstitutionId(institutionIdStr);
            
            Transaction transactionToDelete = transactions.stream()
                    .filter(t -> transactionId.toString().equals(t.getTransactionId()))
                    .findFirst()
                    .orElseThrow(() -> new InstitutionNotFoundException(
                        "Transaction not found with ID: " + transactionId));
            
            // Verify the transaction belongs to the user
            if (!userId.equals(transactionToDelete.getUserId())) {
                throw new InstitutionNotFoundException("Transaction not found with ID: " + transactionId);
            }
            
            logger.info("Deleting transaction {} for institution {}", transactionId, institutionIdStr);
            
            transactionRepository.delete(institutionIdStr, transactionToDelete.getCreatedAt());
            
        } catch (InstitutionNotFoundException e) {
            throw e;
        } catch (DynamoDbException e) {
            logger.error("DynamoDB error while deleting transaction {}: {}", 
                transactionId, e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error while deleting transaction {}: {}", 
                transactionId, e.getMessage(), e);
            throw new RuntimeException("Failed to delete transaction", e);
        }
    }

    private void validateTransactionRequest(CreateTransactionRequest request) {
        if (request.getType() == null) {
            throw new InvalidTransactionDataException("Transaction type cannot be null");
        }
        
        if (request.getAmount() == null) {
            throw new InvalidTransactionDataException("Transaction amount cannot be null");
        }
        
        double amount = request.getAmount();
        
        // Check for NaN and Infinity first before numeric comparisons
        if (Double.isNaN(amount) || Double.isInfinite(amount)) {
            throw new InvalidTransactionDataException("Transaction amount must be a valid number");
        }
        
        if (amount <= 0) {
            throw new InvalidTransactionDataException("Transaction amount must be greater than zero");
        }
        
        if (amount > MAX_TRANSACTION_AMOUNT) {
            throw new InvalidTransactionDataException(
                "Transaction amount cannot exceed " + MAX_TRANSACTION_AMOUNT);
        }
    }

    private TransactionResponse mapToResponse(Transaction transaction) {
        try {
            TransactionResponse response = new TransactionResponse();
            response.setTransactionId(UUID.fromString(transaction.getTransactionId()));
            response.setInstitutionId(UUID.fromString(transaction.getInstitutionId()));
            response.setType(TransactionResponse.TypeEnum.fromValue(transaction.getType()));
            response.setAmount(transaction.getAmount());
            response.setTags(transaction.getTags());
            response.setDescription(transaction.getDescription());
            response.setCreatedAt(transaction.getCreatedAt());
            return response;
        } catch (IllegalArgumentException e) {
            logger.error("Invalid UUID format in transaction data: transactionId={}, institutionId={}", 
                transaction.getTransactionId(), transaction.getInstitutionId(), e);
            throw new RuntimeException("Invalid transaction data format", e);
        }
    }
}
