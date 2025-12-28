package com.cpsc.backend.controller;

import com.cpsc.backend.api.InstitutionsApi;
import com.cpsc.backend.model.CreateInstitutionRequest;
import com.cpsc.backend.model.GetInstitutions200Response;
import com.cpsc.backend.model.InstitutionResponse;
import com.cpsc.backend.service.InstitutionService;
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
public class InstitutionController implements InstitutionsApi {

    private static final Logger logger = LoggerFactory.getLogger(InstitutionController.class);
    
    private final InstitutionService institutionService;

    public InstitutionController(InstitutionService institutionService) {
        this.institutionService = institutionService;
    }

    @Override
    public ResponseEntity<InstitutionResponse> createInstitution(CreateInstitutionRequest createInstitutionRequest) {
        String userId = getAuthenticatedUserId();
        
        logger.info("Request to create institution from user {}", userId);
        
        InstitutionResponse response = institutionService.createInstitution(userId, createInstitutionRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Override
    public ResponseEntity<GetInstitutions200Response> getInstitutions(Integer limit, String lastEvaluatedKey) {
        String userId = getAuthenticatedUserId();
        
        logger.debug("Request to get institutions for user {} with limit={}", userId, limit);
        
        GetInstitutions200Response response = institutionService.getUserInstitutionsPaginated(userId, limit, lastEvaluatedKey);
        
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<Void> deleteInstitution(UUID institutionId) {
        String userId = getAuthenticatedUserId();
        
        logger.info("Request to delete institution {} for user {}", institutionId, userId);
        
        institutionService.deleteInstitution(userId, institutionId.toString());
        
        return ResponseEntity.noContent().build();
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
