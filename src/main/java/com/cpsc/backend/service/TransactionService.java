package com.cpsc.backend.service;

import com.cpsc.backend.entity.Transaction;
import com.cpsc.backend.exception.InstitutionNotFoundException;
import com.cpsc.backend.exception.InvalidTransactionDataException;
import com.cpsc.backend.model.CreateTransactionRequest;
import com.cpsc.backend.model.TransactionResponse;
import com.cpsc.backend.model.UpdateTransactionRequest;
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
        com.cpsc.backend.entity.Institution institution = 
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
            
            // Use provided transaction date or current time if not specified
            Long transactionDate = request.getTransactionDate();
            if (transactionDate == null) {
                transactionDate = Instant.now().getEpochSecond();
            }
            transaction.setTransactionDate(transactionDate);
            
            // Always set createdAt to current time (when the record is created)
            transaction.setCreatedAt(Instant.now().getEpochSecond());

            logger.info("Creating transaction for institution {} with type {} and amount {} at timestamp {}", 
                institutionId, request.getType(), request.getAmount(), transactionDate);
            
            transactionRepository.save(transaction);
            
            // Update institution's current balance
            updateInstitutionBalance(institution, request.getType().getValue(), request.getAmount(), false);
            
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
            
            // Get institution to update balance
            com.cpsc.backend.entity.Institution institution = 
                institutionRepository.findByUserIdAndInstitutionId(userId, institutionIdStr);
            
            // Update institution's current balance (reverse the transaction)
            updateInstitutionBalance(institution, transactionToDelete.getType(), 
                transactionToDelete.getAmount(), true);
            
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

    public TransactionResponse updateTransaction(String userId, UUID institutionId, UUID transactionId, 
                                                  UpdateTransactionRequest request) {
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
        com.cpsc.backend.entity.Institution institution = 
            institutionRepository.findByUserIdAndInstitutionId(userId, institutionIdStr);
        
        try {
            logger.debug("Fetching transaction {} for institution {} to update", 
                transactionId, institutionIdStr);
            
            // Get all transactions for this institution to find the one with matching transactionId
            List<Transaction> transactions = transactionRepository.findAllByInstitutionId(institutionIdStr);
            
            Transaction existingTransaction = transactions.stream()
                    .filter(t -> transactionId.toString().equals(t.getTransactionId()))
                    .findFirst()
                    .orElseThrow(() -> new InstitutionNotFoundException(
                        "Transaction not found with ID: " + transactionId));
            
            // Verify the transaction belongs to the user
            if (!userId.equals(existingTransaction.getUserId())) {
                throw new InstitutionNotFoundException("Transaction not found with ID: " + transactionId);
            }
            
            logger.info("Updating transaction {} for institution {}", transactionId, institutionIdStr);
            
            // Store old values for balance recalculation
            String oldType = existingTransaction.getType();
            Double oldAmount = existingTransaction.getAmount();
            
            // Update fields if provided
            boolean typeChanged = false;
            boolean amountChanged = false;
            
            if (request.getType() != null) {
                String newType = request.getType().getValue();
                if (!newType.equals(oldType)) {
                    existingTransaction.setType(newType);
                    typeChanged = true;
                }
            }
            
            if (request.getAmount() != null) {
                validateAmount(request.getAmount());
                if (!request.getAmount().equals(oldAmount)) {
                    existingTransaction.setAmount(request.getAmount());
                    amountChanged = true;
                }
            }
            
            if (request.getDescription() != null) {
                existingTransaction.setDescription(request.getDescription());
            }
            
            if (request.getTags() != null) {
                existingTransaction.setTags(request.getTags());
            }
            
            if (request.getTransactionDate() != null) {
                existingTransaction.setTransactionDate(request.getTransactionDate());
            }
            
            // Update balance if type or amount changed
            if (typeChanged || amountChanged) {
                // First, reverse the old transaction
                updateInstitutionBalance(institution, oldType, oldAmount, true);
                
                // Then apply the new transaction
                updateInstitutionBalance(institution, existingTransaction.getType(), 
                    existingTransaction.getAmount(), false);
            }
            
            // Save the updated transaction
            transactionRepository.save(existingTransaction);
            
            logger.info("Successfully updated transaction {} for institution {}", 
                transactionId, institutionIdStr);
            
            return mapToResponse(existingTransaction);
            
        } catch (InstitutionNotFoundException e) {
            throw e;
        } catch (DynamoDbException e) {
            logger.error("DynamoDB error while updating transaction {}: {}", 
                transactionId, e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error while updating transaction {}: {}", 
                transactionId, e.getMessage(), e);
            throw new RuntimeException("Failed to update transaction", e);
        }
    }

    /**
     * Update institution's current balance based on transaction type
     * @param institution The institution to update
     * @param transactionType DEPOSIT or WITHDRAWAL
     * @param amount The transaction amount
     * @param isReverse If true, reverse the transaction (for deletion)
     */
    private void updateInstitutionBalance(com.cpsc.backend.entity.Institution institution, 
                                         String transactionType, Double amount, boolean isReverse) {
        Double currentBalance = institution.getCurrentBalance();
        if (currentBalance == null) {
            currentBalance = institution.getStartingBalance();
        }
        
        boolean isWithdrawal = "WITHDRAWAL".equalsIgnoreCase(transactionType);
        
        // For normal transactions: WITHDRAWAL decreases, DEPOSIT increases
        // For reverse (deletion): opposite behavior
        if (isReverse) {
            isWithdrawal = !isWithdrawal;
        }
        
        Double newBalance = isWithdrawal ? currentBalance - amount : currentBalance + amount;
        
        institution.setCurrentBalance(newBalance);
        
        logger.info("Updating institution {} balance from {} to {} (type={}, amount={}, reverse={})",
            institution.getInstitutionId(), currentBalance, newBalance, transactionType, amount, isReverse);
        
        institutionRepository.save(institution);
    }
    
    private void validateTransactionRequest(CreateTransactionRequest request) {
        if (request.getType() == null) {
            throw new InvalidTransactionDataException("Transaction type cannot be null");
        }
        
        if (request.getAmount() == null) {
            throw new InvalidTransactionDataException("Transaction amount cannot be null");
        }
        
        validateAmount(request.getAmount());
    }
    
    private void validateAmount(Double amount) {
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
            response.setTransactionDate(transaction.getTransactionDate());
            response.setCreatedAt(transaction.getCreatedAt());
            return response;
        } catch (IllegalArgumentException e) {
            logger.error("Invalid UUID format in transaction data: transactionId={}, institutionId={}", 
                transaction.getTransactionId(), transaction.getInstitutionId(), e);
            throw new RuntimeException("Invalid transaction data format", e);
        }
    }
}
