package com.cpsc.backend.controller;

import com.cpsc.backend.model.CreateTransactionRequest;
import com.cpsc.backend.model.TransactionResponse;
import com.cpsc.backend.service.TransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionControllerTest {

    @Mock
    private TransactionService transactionService;

    @Mock
    private Authentication authentication;

    @Mock
    private SecurityContext securityContext;

    @InjectMocks
    private TransactionController transactionController;

    private CreateTransactionRequest createRequest;
    private TransactionResponse transactionResponse;
    private static final UUID INSTITUTION_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final String USER_ID = "550e8400-e29b-41d4-a716-446655440001";

    @BeforeEach
    void setUp() {
        createRequest = new CreateTransactionRequest();
        createRequest.setType(CreateTransactionRequest.TypeEnum.DEPOSIT);
        createRequest.setAmount(150.75);
        createRequest.setTags(List.of("grocery", "food"));
        createRequest.setDescription("Weekly groceries");

        transactionResponse = new TransactionResponse();
        transactionResponse.setTransactionId(UUID.randomUUID());
        transactionResponse.setInstitutionId(INSTITUTION_ID);
        transactionResponse.setType(TransactionResponse.TypeEnum.DEPOSIT);
        transactionResponse.setAmount(150.75);
        transactionResponse.setTags(List.of("grocery", "food"));
        transactionResponse.setDescription("Weekly groceries");
        transactionResponse.setCreatedAt(System.currentTimeMillis() / 1000L);
    }

    @Test
    void createTransaction_Success() {
        try (MockedStatic<SecurityContextHolder> securityContextHolder = mockStatic(SecurityContextHolder.class)) {
            securityContextHolder.when(SecurityContextHolder::getContext).thenReturn(securityContext);
            when(securityContext.getAuthentication()).thenReturn(authentication);
            when(authentication.getName()).thenReturn(USER_ID);
            when(transactionService.createTransaction(eq(USER_ID), eq(INSTITUTION_ID.toString()), any(CreateTransactionRequest.class)))
                    .thenReturn(transactionResponse);

            ResponseEntity<TransactionResponse> response = transactionController.createTransaction(INSTITUTION_ID, createRequest);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getInstitutionId()).isEqualTo(INSTITUTION_ID);
            assertThat(response.getBody().getType()).isEqualTo(TransactionResponse.TypeEnum.DEPOSIT);
            assertThat(response.getBody().getAmount()).isEqualTo(150.75);
        }
    }

    @Test
    void getInstitutionTransactions_Success() {
        try (MockedStatic<SecurityContextHolder> securityContextHolder = mockStatic(SecurityContextHolder.class)) {
            securityContextHolder.when(SecurityContextHolder::getContext).thenReturn(securityContext);
            when(securityContext.getAuthentication()).thenReturn(authentication);
            when(authentication.getName()).thenReturn(USER_ID);
            when(transactionService.getInstitutionTransactions(USER_ID, INSTITUTION_ID.toString()))
                    .thenReturn(List.of(transactionResponse));

            ResponseEntity<List<TransactionResponse>> response = transactionController.getInstitutionTransactions(INSTITUTION_ID);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody()).hasSize(1);
            assertThat(response.getBody().get(0).getAmount()).isEqualTo(150.75);
        }
    }

    @Test
    void getInstitutionTransactions_EmptyList_ReturnsEmptyList() {
        try (MockedStatic<SecurityContextHolder> securityContextHolder = mockStatic(SecurityContextHolder.class)) {
            securityContextHolder.when(SecurityContextHolder::getContext).thenReturn(securityContext);
            when(securityContext.getAuthentication()).thenReturn(authentication);
            when(authentication.getName()).thenReturn(USER_ID);
            when(transactionService.getInstitutionTransactions(USER_ID, INSTITUTION_ID.toString()))
                    .thenReturn(Collections.emptyList());

            ResponseEntity<List<TransactionResponse>> response = transactionController.getInstitutionTransactions(INSTITUTION_ID);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isEmpty();
        }
    }
}
