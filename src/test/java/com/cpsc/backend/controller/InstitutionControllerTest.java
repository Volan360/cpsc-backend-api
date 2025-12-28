package com.cpsc.backend.controller;

import com.cpsc.backend.model.CreateInstitutionRequest;
import com.cpsc.backend.model.GetInstitutions200Response;
import com.cpsc.backend.model.InstitutionResponse;
import com.cpsc.backend.service.InstitutionService;
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

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InstitutionControllerTest {

    @Mock
    private InstitutionService institutionService;

    @Mock
    private Authentication authentication;

        @Mock
        private SecurityContext securityContext;

    @InjectMocks
    private InstitutionController institutionController;

    private CreateInstitutionRequest createRequest;
        private InstitutionResponse createResponse;
    private GetInstitutions200Response getResponse;

    @BeforeEach
    void setUp() {
        createRequest = new CreateInstitutionRequest();
        createRequest.setInstitutionName("Test Bank");
        createRequest.setStartingBalance(1000.0);

        createResponse = new InstitutionResponse();
        createResponse.setInstitutionId(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"));
        createResponse.setInstitutionName("Test Bank");
        createResponse.setStartingBalance(1000.0);
        createResponse.setUserId(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"));
        createResponse.setCreatedAt(System.currentTimeMillis() / 1000L);

        getResponse = new GetInstitutions200Response();
        getResponse.setInstitutions(Collections.emptyList());
    }

    @Test
    void createInstitution_Success() {
        try (MockedStatic<SecurityContextHolder> securityContextHolder = mockStatic(SecurityContextHolder.class)) {
            securityContextHolder.when(SecurityContextHolder::getContext).thenReturn(securityContext);
            when(securityContext.getAuthentication()).thenReturn(authentication);
            when(authentication.getName()).thenReturn("550e8400-e29b-41d4-a716-446655440000");
            when(institutionService.createInstitution(eq("550e8400-e29b-41d4-a716-446655440000"), any(CreateInstitutionRequest.class)))
                    .thenReturn(createResponse);

            ResponseEntity<InstitutionResponse> response = institutionController.createInstitution(createRequest);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getInstitutionId()).isEqualTo(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"));
            assertThat(response.getBody().getInstitutionName()).isEqualTo("Test Bank");
        }
    }

    @Test
    void getInstitutions_WithoutPagination() {
        try (MockedStatic<SecurityContextHolder> securityContextHolder = mockStatic(SecurityContextHolder.class)) {
            securityContextHolder.when(SecurityContextHolder::getContext).thenReturn(securityContext);
            when(securityContext.getAuthentication()).thenReturn(authentication);
            when(authentication.getName()).thenReturn("550e8400-e29b-41d4-a716-446655440000");
            when(institutionService.getUserInstitutionsPaginated("550e8400-e29b-41d4-a716-446655440000", null, null))
                    .thenReturn(getResponse);

            ResponseEntity<GetInstitutions200Response> response = institutionController.getInstitutions(null, null);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getInstitutions()).isEmpty();
        }
    }

    @Test
    void getInstitutions_WithPagination() {
        try (MockedStatic<SecurityContextHolder> securityContextHolder = mockStatic(SecurityContextHolder.class)) {
            securityContextHolder.when(SecurityContextHolder::getContext).thenReturn(securityContext);
            when(securityContext.getAuthentication()).thenReturn(authentication);
            when(authentication.getName()).thenReturn("550e8400-e29b-41d4-a716-446655440000");
            when(institutionService.getUserInstitutionsPaginated("550e8400-e29b-41d4-a716-446655440000", 10, "token-123"))
                    .thenReturn(getResponse);

            ResponseEntity<GetInstitutions200Response> response = 
                    institutionController.getInstitutions(10, "token-123");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
        }
    }

    @Test
    void deleteInstitution_Success() {
        try (MockedStatic<SecurityContextHolder> securityContextHolder = mockStatic(SecurityContextHolder.class)) {
            securityContextHolder.when(SecurityContextHolder::getContext).thenReturn(securityContext);
            when(securityContext.getAuthentication()).thenReturn(authentication);
            when(authentication.getName()).thenReturn("550e8400-e29b-41d4-a716-446655440000");
            doNothing().when(institutionService).deleteInstitution("550e8400-e29b-41d4-a716-446655440000", "550e8400-e29b-41d4-a716-446655440001");

            ResponseEntity<Void> response = institutionController.deleteInstitution(UUID.fromString("550e8400-e29b-41d4-a716-446655440001"));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
            assertThat(response.getBody()).isNull();
            verify(institutionService).deleteInstitution("550e8400-e29b-41d4-a716-446655440000", "550e8400-e29b-41d4-a716-446655440001");        }
    }
}