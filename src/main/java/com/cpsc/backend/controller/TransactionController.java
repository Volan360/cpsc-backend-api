package com.cpsc.backend.controller;

import com.cpsc.backend.api.TransactionsApi;
import com.cpsc.backend.model.CreateTransactionRequest;
import com.cpsc.backend.model.TransactionResponse;
import com.cpsc.backend.model.UpdateTransactionRequest;
import com.cpsc.backend.service.TransactionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class TransactionController implements TransactionsApi {

    private static final Logger logger = LoggerFactory.getLogger(TransactionController.class);
    
    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @Override
    public ResponseEntity<TransactionResponse> createTransaction(UUID institutionId, CreateTransactionRequest createTransactionRequest) {
        String userId = getAuthenticatedUserId();
        
        logger.info("Request to create transaction for institution {} from user {}", institutionId, userId);
        
        TransactionResponse response = transactionService.createTransaction(userId, institutionId.toString(), createTransactionRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Override
    public ResponseEntity<List<TransactionResponse>> getInstitutionTransactions(UUID institutionId) {
        String userId = getAuthenticatedUserId();
        
        logger.debug("Request to get transactions for institution {} from user {}", institutionId, userId);
        
        List<TransactionResponse> response = transactionService.getInstitutionTransactions(userId, institutionId.toString());
        
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<Void> deleteTransaction(UUID institutionId, UUID transactionId) {
        String userId = getAuthenticatedUserId();
        
        logger.info("Request to delete transaction {} for institution {} from user {}", 
            transactionId, institutionId, userId);
        
        transactionService.deleteTransaction(userId, institutionId, transactionId);
        
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<TransactionResponse> updateTransaction(UUID institutionId, UUID transactionId, 
                                                                   UpdateTransactionRequest updateTransactionRequest) {
        String userId = getAuthenticatedUserId();
        
        logger.info("Request to update transaction {} for institution {} from user {}", 
            transactionId, institutionId, userId);
        
        TransactionResponse response = transactionService.updateTransaction(userId, institutionId, 
            transactionId, updateTransactionRequest);
        
        return ResponseEntity.ok(response);
    }

    private String getAuthenticatedUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication == null) {
            logger.error("No authentication found in SecurityContext");
            throw new IllegalStateException("User not authenticated");
        }
        
        String userId = authentication.getName();
        
        if (userId == null || userId.trim().isEmpty()) {
            logger.error("Authentication present but user ID is null or empty");
            throw new IllegalStateException("Invalid authentication state");
        }
        
        return userId;
    }
}
