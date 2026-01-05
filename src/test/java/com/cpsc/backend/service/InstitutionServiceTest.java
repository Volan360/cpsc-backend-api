package com.cpsc.backend.service;

import com.cpsc.backend.entity.Institution;
import com.cpsc.backend.exception.InvalidInstitutionDataException;
import com.cpsc.backend.model.CreateInstitutionRequest;
import com.cpsc.backend.model.GetInstitutions200Response;
import com.cpsc.backend.model.InstitutionResponse;
import com.cpsc.backend.repository.InstitutionRepository;
import com.cpsc.backend.repository.TransactionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InstitutionServiceTest {

    @Mock
    private InstitutionRepository institutionRepository;

    @Mock
    private TransactionRepository transactionRepository;
    
    @Mock
    private GoalService goalService;

    private InstitutionService institutionService;

    private CreateInstitutionRequest validRequest;

    @BeforeEach
    void setUp() {
        institutionService = new InstitutionService(institutionRepository, transactionRepository, goalService);
        validRequest = new CreateInstitutionRequest();
        validRequest.setInstitutionName("Test Bank");
        validRequest.setStartingBalance(1000.0);
    }

    @Test
    void createInstitution_Success() {
        doNothing().when(institutionRepository).save(any(Institution.class));

        InstitutionResponse response = institutionService.createInstitution("3c925d70-6d8d-4e59-9d2c-2d86a5f0bf28", validRequest);

        assertThat(response).isNotNull();
        assertThat(response.getInstitutionId()).isNotNull();
        assertThat(response.getInstitutionName()).isEqualTo("Test Bank");
        assertThat(response.getStartingBalance()).isEqualTo(1000.0);
        assertThat(response.getCurrentBalance()).isEqualTo(1000.0); // currentBalance should equal startingBalance
        assertThat(response.getAllocatedPercent()).isEqualTo(0); // allocatedPercent should default to 0
        verify(institutionRepository).save(any(Institution.class));
    }

    @Test
    void createInstitution_CurrentBalanceEqualsStartingBalance() {
        doNothing().when(institutionRepository).save(any(Institution.class));

        validRequest.setStartingBalance(2500.75);
        InstitutionResponse response = institutionService.createInstitution("3c925d70-6d8d-4e59-9d2c-2d86a5f0bf28", validRequest);

        assertThat(response.getCurrentBalance()).isEqualTo(2500.75);
        assertThat(response.getCurrentBalance()).isEqualTo(response.getStartingBalance());
    }

    @Test
    void createInstitution_NullName_ThrowsException() {
        validRequest.setInstitutionName(null);

        assertThatThrownBy(() -> institutionService.createInstitution("user-123", validRequest))
                .isInstanceOf(InvalidInstitutionDataException.class)
                .hasMessage("Institution name cannot be empty");
    }

    @Test
    void createInstitution_EmptyName_ThrowsException() {
        validRequest.setInstitutionName("");

        assertThatThrownBy(() -> institutionService.createInstitution("user-123", validRequest))
                .isInstanceOf(InvalidInstitutionDataException.class)
                .hasMessage("Institution name cannot be empty");
    }

    @Test
    void createInstitution_NameTooLong_ThrowsException() {
        validRequest.setInstitutionName("a".repeat(101));

        assertThatThrownBy(() -> institutionService.createInstitution("user-123", validRequest))
                .isInstanceOf(InvalidInstitutionDataException.class)
                .hasMessage("Institution name cannot exceed 100 characters");
    }

    @Test
    void createInstitution_NullBalance_ThrowsException() {
        validRequest.setStartingBalance(null);

        assertThatThrownBy(() -> institutionService.createInstitution("user-123", validRequest))
                .isInstanceOf(InvalidInstitutionDataException.class)
                .hasMessage("Starting balance cannot be null");
    }

    @Test
    void createInstitution_NegativeBalance_ThrowsException() {
        validRequest.setStartingBalance(-100.0);

        assertThatThrownBy(() -> institutionService.createInstitution("user-123", validRequest))
                .isInstanceOf(InvalidInstitutionDataException.class)
                .hasMessage("Starting balance cannot be negative");
    }

    @Test
    void createInstitution_BalanceTooLarge_ThrowsException() {
        validRequest.setStartingBalance(1_000_000_001.0);

        assertThatThrownBy(() -> institutionService.createInstitution("user-123", validRequest))
                .isInstanceOf(InvalidInstitutionDataException.class)
                .hasMessage("Starting balance cannot exceed 1.0E9");
    }

    @Test
    void createInstitution_InfiniteBalance_ThrowsException() {
        validRequest.setStartingBalance(Double.POSITIVE_INFINITY);

        assertThatThrownBy(() -> institutionService.createInstitution("user-123", validRequest))
                .isInstanceOf(InvalidInstitutionDataException.class)
                .hasMessage("Starting balance cannot exceed 1.0E9");
    }

    @Test
    void createInstitution_NaNBalance_ThrowsException() {
        validRequest.setStartingBalance(Double.NaN);

        assertThatThrownBy(() -> institutionService.createInstitution("user-123", validRequest))
                .isInstanceOf(InvalidInstitutionDataException.class)
                .hasMessage("Starting balance must be a valid number");
    }

    @Test
    void getUserInstitutions_ReturnsMappedResponses() {
        Institution institution = new Institution();
        institution.setInstitutionId(UUID.randomUUID().toString());
        institution.setUserId(UUID.randomUUID().toString());
        institution.setInstitutionName("Mapped Bank");
        institution.setStartingBalance(500.0);
        institution.setCurrentBalance(750.0);
        institution.setCreatedAt(1704067200L); // 2024-01-01T00:00:00Z in epoch seconds

        when(institutionRepository.findAllByUserId("user-123")).thenReturn(List.of(institution));

        List<InstitutionResponse> responses = institutionService.getUserInstitutions("user-123");

        assertThat(responses).hasSize(1);
        InstitutionResponse mapped = responses.getFirst();
        assertThat(mapped.getInstitutionName()).isEqualTo("Mapped Bank");
        assertThat(mapped.getStartingBalance()).isEqualTo(500.0);
        assertThat(mapped.getUserId()).isEqualTo(UUID.fromString(institution.getUserId()));
        assertThat(mapped.getCreatedAt()).isEqualTo(institution.getCreatedAt());
        verify(institutionRepository).findAllByUserId("user-123");
    }

    @Test
    void getUserInstitutionsPaginated_UsesDefaultLimitWhenNull() {
        InstitutionRepository.PaginatedResult<Institution> result =
                new InstitutionRepository.PaginatedResult<>(Collections.emptyList(), null);

        when(institutionRepository.findAllByUserIdPaginated("user-123", 50, null)).thenReturn(result);

        GetInstitutions200Response response = institutionService.getUserInstitutionsPaginated("user-123", null, null);

        assertThat(response.getInstitutions()).isEmpty();
        assertThat(response.getNextToken()).isNull();
        verify(institutionRepository).findAllByUserIdPaginated("user-123", 50, null);
    }

    @Test
    void getUserInstitutionsPaginated_ReturnsNextTokenWhenHasMore() throws Exception {
        Institution institution = new Institution();
        institution.setInstitutionId(UUID.randomUUID().toString());
        institution.setUserId(UUID.randomUUID().toString());
        institution.setInstitutionName("Paged Bank");
        institution.setStartingBalance(250.0);
        institution.setCreatedAt(System.currentTimeMillis() / 1000L);

        Map<String, AttributeValue> lastEvaluatedKey = Map.of(
                "institutionId", AttributeValue.builder().s("last-id").build()
        );

        InstitutionRepository.PaginatedResult<Institution> result =
                new InstitutionRepository.PaginatedResult<>(List.of(institution), lastEvaluatedKey);

        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(Map.of("institutionId", "last-id"));
        String token = Base64.getUrlEncoder().encodeToString(json.getBytes());

        when(institutionRepository.findAllByUserIdPaginated("user-123", 10, lastEvaluatedKey)).thenReturn(result);

        GetInstitutions200Response response = institutionService.getUserInstitutionsPaginated("user-123", 10, token);

        assertThat(response.getInstitutions()).hasSize(1);
        assertThat(response.getNextToken()).isEqualTo(token);
        verify(institutionRepository).findAllByUserIdPaginated("user-123", 10, lastEvaluatedKey);
    }

    @Test
    void createInstitution_NullUserId_ThrowsException() {
        assertThatThrownBy(() -> institutionService.createInstitution(null, validRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("User ID cannot be null or empty");
    }

    @Test
    void createInstitution_EmptyUserId_ThrowsException() {
        assertThatThrownBy(() -> institutionService.createInstitution("", validRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("User ID cannot be null or empty");
    }

    @Test
    void createInstitution_DynamoDbException_ThrowsException() {
        doThrow(DynamoDbException.builder().message("DynamoDB error").build())
                .when(institutionRepository).save(any(Institution.class));

        assertThatThrownBy(() -> institutionService.createInstitution("user-123", validRequest))
                .isInstanceOf(DynamoDbException.class);
    }

    @Test
    void createInstitution_GenericException_ThrowsRuntimeException() {
        doThrow(new RuntimeException("Unexpected error"))
                .when(institutionRepository).save(any(Institution.class));

        assertThatThrownBy(() -> institutionService.createInstitution("user-123", validRequest))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Failed to create institution");
    }

    @Test
    void getUserInstitutions_NullUserId_ThrowsException() {
        assertThatThrownBy(() -> institutionService.getUserInstitutions(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("User ID cannot be null or empty");
    }

    @Test
    void getUserInstitutions_EmptyUserId_ThrowsException() {
        assertThatThrownBy(() -> institutionService.getUserInstitutions(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("User ID cannot be null or empty");
    }

    @Test
    void getUserInstitutions_DynamoDbException_ThrowsException() {
        when(institutionRepository.findAllByUserId("user-123"))
                .thenThrow(DynamoDbException.builder().message("DynamoDB error").build());

        assertThatThrownBy(() -> institutionService.getUserInstitutions("user-123"))
                .isInstanceOf(DynamoDbException.class);
    }

    @Test
    void getUserInstitutions_GenericException_ThrowsRuntimeException() {
        when(institutionRepository.findAllByUserId("user-123"))
                .thenThrow(new RuntimeException("Unexpected error"));

        assertThatThrownBy(() -> institutionService.getUserInstitutions("user-123"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Failed to fetch institutions");
    }

    @Test
    void getUserInstitutionsPaginated_NullUserId_ThrowsException() {
        assertThatThrownBy(() -> institutionService.getUserInstitutionsPaginated(null, 10, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("User ID cannot be null or empty");
    }

    @Test
    void getUserInstitutionsPaginated_EmptyUserId_ThrowsException() {
        assertThatThrownBy(() -> institutionService.getUserInstitutionsPaginated("", 10, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("User ID cannot be null or empty");
    }

    @Test
    void getUserInstitutionsPaginated_LimitExceedsMaximum_UsesMaxLimit() {
        InstitutionRepository.PaginatedResult<Institution> result =
                new InstitutionRepository.PaginatedResult<>(Collections.emptyList(), null);

        when(institutionRepository.findAllByUserIdPaginated("user-123", 100, null)).thenReturn(result);

        GetInstitutions200Response response = institutionService.getUserInstitutionsPaginated("user-123", 200, null);

        verify(institutionRepository).findAllByUserIdPaginated("user-123", 100, null);
        assertThat(response.getInstitutions()).isEmpty();
    }

    @Test
    void getUserInstitutionsPaginated_NegativeLimit_UsesDefault() {
        InstitutionRepository.PaginatedResult<Institution> result =
                new InstitutionRepository.PaginatedResult<>(Collections.emptyList(), null);

        when(institutionRepository.findAllByUserIdPaginated("user-123", 50, null)).thenReturn(result);

        GetInstitutions200Response response = institutionService.getUserInstitutionsPaginated("user-123", -1, null);

        verify(institutionRepository).findAllByUserIdPaginated("user-123", 50, null);
        assertThat(response.getInstitutions()).isEmpty();
    }

    @Test
    void getUserInstitutionsPaginated_DynamoDbException_ThrowsException() {
        when(institutionRepository.findAllByUserIdPaginated(eq("user-123"), anyInt(), any()))
                .thenThrow(DynamoDbException.builder().message("DynamoDB error").build());

        assertThatThrownBy(() -> institutionService.getUserInstitutionsPaginated("user-123", 10, null))
                .isInstanceOf(DynamoDbException.class);
    }

    @Test
    void getUserInstitutionsPaginated_GenericException_ThrowsRuntimeException() {
        when(institutionRepository.findAllByUserIdPaginated(eq("user-123"), anyInt(), any()))
                .thenThrow(new RuntimeException("Unexpected error"));

        assertThatThrownBy(() -> institutionService.getUserInstitutionsPaginated("user-123", 10, null))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Failed to fetch institutions");
    }

    @Test
    void getUserInstitutionsPaginated_InvalidToken_ThrowsRuntimeException() {
        String invalidToken = "not-a-valid-base64-token!!!";

        assertThatThrownBy(() -> institutionService.getUserInstitutionsPaginated("user-123", 10, invalidToken))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void getUserInstitutionsPaginated_EmptyList_ReturnsEmptyResponse() {
        InstitutionRepository.PaginatedResult<Institution> result =
                new InstitutionRepository.PaginatedResult<>(Collections.emptyList(), null);

        when(institutionRepository.findAllByUserIdPaginated("user-123", 50, null)).thenReturn(result);

        GetInstitutions200Response response = institutionService.getUserInstitutionsPaginated("user-123", null, null);

        assertThat(response.getInstitutions()).isEmpty();
        assertThat(response.getNextToken()).isNull();
    }

    @Test
    void getUserInstitutions_EmptyList_ReturnsEmptyList() {
        when(institutionRepository.findAllByUserId("user-123")).thenReturn(Collections.emptyList());

        List<InstitutionResponse> responses = institutionService.getUserInstitutions("user-123");

        assertThat(responses).isEmpty();
        verify(institutionRepository).findAllByUserId("user-123");
    }

    @Test
    void mapToResponse_InvalidInstitutionIdUUID_ThrowsRuntimeException() {
        Institution institution = new Institution();
        institution.setInstitutionId("invalid-uuid");
        institution.setUserId(UUID.randomUUID().toString());
        institution.setInstitutionName("Test Bank");
        institution.setStartingBalance(1000.0);
        institution.setCreatedAt(System.currentTimeMillis() / 1000L);

        when(institutionRepository.findAllByUserId("user-123")).thenReturn(List.of(institution));

        assertThatThrownBy(() -> institutionService.getUserInstitutions("user-123"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Failed to fetch institutions")
                .hasCauseInstanceOf(RuntimeException.class)
                .cause()
                .hasMessage("Invalid institution data format");
    }

    @Test
    void mapToResponse_InvalidUserIdUUID_ThrowsRuntimeException() {
        Institution institution = new Institution();
        institution.setInstitutionId(UUID.randomUUID().toString());
        institution.setUserId("not-a-valid-uuid");
        institution.setInstitutionName("Test Bank");
        institution.setStartingBalance(1000.0);
        institution.setCreatedAt(System.currentTimeMillis() / 1000L);

        when(institutionRepository.findAllByUserId("user-123")).thenReturn(List.of(institution));

        assertThatThrownBy(() -> institutionService.getUserInstitutions("user-123"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Failed to fetch institutions")
                .hasCauseInstanceOf(RuntimeException.class)
                .cause()
                .hasMessage("Invalid institution data format");
    }

    @Test
    void deleteInstitution_Success() {
        Institution institution = new Institution();
        institution.setInstitutionId("inst-123");
        institution.setUserId("user-123");
        institution.setInstitutionName("Test Bank");
        institution.setStartingBalance(1000.0);
        institution.setCreatedAt(System.currentTimeMillis() / 1000L);

        when(institutionRepository.findByUserIdAndInstitutionId("user-123", "inst-123")).thenReturn(institution);
        doNothing().when(transactionRepository).deleteAllByInstitutionId("inst-123");
        doNothing().when(institutionRepository).delete("user-123", "inst-123");

        institutionService.deleteInstitution("user-123", "inst-123");

        verify(institutionRepository).findByUserIdAndInstitutionId("user-123", "inst-123");
        verify(transactionRepository).deleteAllByInstitutionId("inst-123");
        verify(institutionRepository).delete("user-123", "inst-123");
    }

    @Test
    void deleteInstitution_NullUserId_ThrowsException() {
        assertThatThrownBy(() -> institutionService.deleteInstitution(null, "inst-123"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("User ID cannot be null or empty");
    }

    @Test
    void deleteInstitution_EmptyUserId_ThrowsException() {
        assertThatThrownBy(() -> institutionService.deleteInstitution("", "inst-123"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("User ID cannot be null or empty");
    }

    @Test
    void deleteInstitution_NullInstitutionId_ThrowsException() {
        assertThatThrownBy(() -> institutionService.deleteInstitution("user-123", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Institution ID cannot be null or empty");
    }

    @Test
    void deleteInstitution_EmptyInstitutionId_ThrowsException() {
        assertThatThrownBy(() -> institutionService.deleteInstitution("user-123", ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Institution ID cannot be null or empty");
    }

    @Test
    void deleteInstitution_DynamoDbException_ThrowsException() {
        when(institutionRepository.findByUserIdAndInstitutionId("user-123", "inst-123"))
                .thenThrow(DynamoDbException.builder().message("DynamoDB error").build());

        assertThatThrownBy(() -> institutionService.deleteInstitution("user-123", "inst-123"))
                .isInstanceOf(DynamoDbException.class);
    }

    @Test
    void deleteInstitution_GenericException_ThrowsRuntimeException() {
        when(institutionRepository.findByUserIdAndInstitutionId("user-123", "inst-123"))
                .thenThrow(new RuntimeException("Unexpected error"));

        assertThatThrownBy(() -> institutionService.deleteInstitution("user-123", "inst-123"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Failed to delete institution");
    }
    
    // ===== EDIT INSTITUTION TESTS =====
    
    @Test
    void editInstitution_UpdateNameOnly_Success() {
        String userId = "3c925d70-6d8d-4e59-9d2c-2d86a5f0bf28";
        String institutionId = UUID.randomUUID().toString();
        
        Institution institution = new Institution();
        institution.setUserId(userId);
        institution.setInstitutionId(institutionId);
        institution.setInstitutionName("Old Bank Name");
        institution.setStartingBalance(1000.0);
        institution.setCurrentBalance(1200.0);
        institution.setCreatedAt(System.currentTimeMillis() / 1000L);
        
        when(institutionRepository.findByUserIdAndInstitutionId(userId, institutionId)).thenReturn(institution);
        doNothing().when(institutionRepository).save(any(Institution.class));
        
        com.cpsc.backend.model.EditInstitutionRequest request = new com.cpsc.backend.model.EditInstitutionRequest();
        request.setInstitutionName("New Bank Name");
        
        InstitutionResponse response = institutionService.editInstitution(userId, institutionId, request);
        
        assertThat(response.getInstitutionName()).isEqualTo("New Bank Name");
        assertThat(response.getStartingBalance()).isEqualTo(1000.0);
        assertThat(response.getCurrentBalance()).isEqualTo(1200.0); // Should not change
        verify(institutionRepository).save(institution);
    }
    
    @Test
    void editInstitution_UpdateStartingBalanceOnly_AdjustsCurrentBalance() {
        String userId = "3c925d70-6d8d-4e59-9d2c-2d86a5f0bf28";
        String institutionId = UUID.randomUUID().toString();
        
        Institution institution = new Institution();
        institution.setUserId(userId);
        institution.setInstitutionId(institutionId);
        institution.setInstitutionName("Test Bank");
        institution.setStartingBalance(1000.0);
        institution.setCurrentBalance(1500.0);
        institution.setCreatedAt(System.currentTimeMillis() / 1000L);
        
        when(institutionRepository.findByUserIdAndInstitutionId(userId, institutionId)).thenReturn(institution);
        doNothing().when(institutionRepository).save(any(Institution.class));
        
        com.cpsc.backend.model.EditInstitutionRequest request = new com.cpsc.backend.model.EditInstitutionRequest();
        request.setStartingBalance(800.0); // Decrease by 200
        
        InstitutionResponse response = institutionService.editInstitution(userId, institutionId, request);
        
        assertThat(response.getStartingBalance()).isEqualTo(800.0);
        assertThat(response.getCurrentBalance()).isEqualTo(1300.0); // 1500 - 200
        verify(institutionRepository).save(institution);
    }
    
    @Test
    void editInstitution_IncreaseStartingBalance_IncreasesCurrentBalance() {
        String userId = "3c925d70-6d8d-4e59-9d2c-2d86a5f0bf28";
        String institutionId = UUID.randomUUID().toString();
        
        Institution institution = new Institution();
        institution.setUserId(userId);
        institution.setInstitutionId(institutionId);
        institution.setInstitutionName("Test Bank");
        institution.setStartingBalance(500.0);
        institution.setCurrentBalance(750.0);
        institution.setCreatedAt(System.currentTimeMillis() / 1000L);
        
        when(institutionRepository.findByUserIdAndInstitutionId(userId, institutionId)).thenReturn(institution);
        doNothing().when(institutionRepository).save(any(Institution.class));
        
        com.cpsc.backend.model.EditInstitutionRequest request = new com.cpsc.backend.model.EditInstitutionRequest();
        request.setStartingBalance(700.0); // Increase by 200
        
        InstitutionResponse response = institutionService.editInstitution(userId, institutionId, request);
        
        assertThat(response.getStartingBalance()).isEqualTo(700.0);
        assertThat(response.getCurrentBalance()).isEqualTo(950.0); // 750 + 200
        verify(institutionRepository).save(institution);
    }
    
    @Test
    void editInstitution_UpdateBoth_Success() {
        String userId = "3c925d70-6d8d-4e59-9d2c-2d86a5f0bf28";
        String institutionId = UUID.randomUUID().toString();
        
        Institution institution = new Institution();
        institution.setUserId(userId);
        institution.setInstitutionId(institutionId);
        institution.setInstitutionName("Old Name");
        institution.setStartingBalance(1000.0);
        institution.setCurrentBalance(1200.0);
        institution.setCreatedAt(System.currentTimeMillis() / 1000L);
        
        when(institutionRepository.findByUserIdAndInstitutionId(userId, institutionId)).thenReturn(institution);
        doNothing().when(institutionRepository).save(any(Institution.class));
        
        com.cpsc.backend.model.EditInstitutionRequest request = new com.cpsc.backend.model.EditInstitutionRequest();
        request.setInstitutionName("New Name");
        request.setStartingBalance(950.0); // Decrease by 50
        
        InstitutionResponse response = institutionService.editInstitution(userId, institutionId, request);
        
        assertThat(response.getInstitutionName()).isEqualTo("New Name");
        assertThat(response.getStartingBalance()).isEqualTo(950.0);
        assertThat(response.getCurrentBalance()).isEqualTo(1150.0); // 1200 - 50
        verify(institutionRepository).save(institution);
    }
    
    @Test
    void editInstitution_NullCurrentBalance_UsesStartingBalance() {
        String userId = "3c925d70-6d8d-4e59-9d2c-2d86a5f0bf28";
        String institutionId = UUID.randomUUID().toString();
        
        Institution institution = new Institution();
        institution.setUserId(userId);
        institution.setInstitutionId(institutionId);
        institution.setInstitutionName("Test Bank");
        institution.setStartingBalance(1000.0);
        institution.setCurrentBalance(null); // Null current balance
        institution.setCreatedAt(System.currentTimeMillis() / 1000L);
        
        when(institutionRepository.findByUserIdAndInstitutionId(userId, institutionId)).thenReturn(institution);
        doNothing().when(institutionRepository).save(any(Institution.class));
        
        com.cpsc.backend.model.EditInstitutionRequest request = new com.cpsc.backend.model.EditInstitutionRequest();
        request.setStartingBalance(1100.0); // Increase by 100
        
        InstitutionResponse response = institutionService.editInstitution(userId, institutionId, request);
        
        assertThat(response.getStartingBalance()).isEqualTo(1100.0);
        assertThat(response.getCurrentBalance()).isEqualTo(1100.0); // 1000 (old starting) + 100
        verify(institutionRepository).save(institution);
    }
    
    @Test
    void editInstitution_EmptyRequest_NoChanges() {
        String userId = "3c925d70-6d8d-4e59-9d2c-2d86a5f0bf28";
        String institutionId = UUID.randomUUID().toString();
        
        Institution institution = new Institution();
        institution.setUserId(userId);
        institution.setInstitutionId(institutionId);
        institution.setInstitutionName("Test Bank");
        institution.setStartingBalance(1000.0);
        institution.setCurrentBalance(1200.0);
        institution.setCreatedAt(System.currentTimeMillis() / 1000L);
        
        when(institutionRepository.findByUserIdAndInstitutionId(userId, institutionId)).thenReturn(institution);
        
        com.cpsc.backend.model.EditInstitutionRequest request = new com.cpsc.backend.model.EditInstitutionRequest();
        
        InstitutionResponse response = institutionService.editInstitution(userId, institutionId, request);
        
        assertThat(response.getInstitutionName()).isEqualTo("Test Bank");
        assertThat(response.getStartingBalance()).isEqualTo(1000.0);
        assertThat(response.getCurrentBalance()).isEqualTo(1200.0);
        // save() should not be called when there are no changes
    }
    
    @Test
    void editInstitution_EmptyName_ThrowsException() {
        String userId = "3c925d70-6d8d-4e59-9d2c-2d86a5f0bf28";
        String institutionId = UUID.randomUUID().toString();
        
        Institution institution = new Institution();
        institution.setUserId(userId);
        institution.setInstitutionId(institutionId);
        institution.setInstitutionName("Test Bank");
        institution.setStartingBalance(1000.0);
        institution.setCurrentBalance(1200.0);
        institution.setCreatedAt(System.currentTimeMillis() / 1000L);
        
        when(institutionRepository.findByUserIdAndInstitutionId(userId, institutionId)).thenReturn(institution);
        
        com.cpsc.backend.model.EditInstitutionRequest request = new com.cpsc.backend.model.EditInstitutionRequest();
        request.setInstitutionName("");
        
        assertThatThrownBy(() -> institutionService.editInstitution(userId, institutionId, request))
                .isInstanceOf(InvalidInstitutionDataException.class)
                .hasMessage("Institution name cannot be empty");
    }
    
    @Test
    void editInstitution_NameTooLong_ThrowsException() {
        String userId = "3c925d70-6d8d-4e59-9d2c-2d86a5f0bf28";
        String institutionId = UUID.randomUUID().toString();
        
        Institution institution = new Institution();
        institution.setUserId(userId);
        institution.setInstitutionId(institutionId);
        institution.setInstitutionName("Test Bank");
        institution.setStartingBalance(1000.0);
        institution.setCurrentBalance(1200.0);
        institution.setCreatedAt(System.currentTimeMillis() / 1000L);
        
        when(institutionRepository.findByUserIdAndInstitutionId(userId, institutionId)).thenReturn(institution);
        
        com.cpsc.backend.model.EditInstitutionRequest request = new com.cpsc.backend.model.EditInstitutionRequest();
        request.setInstitutionName("a".repeat(101));
        
        assertThatThrownBy(() -> institutionService.editInstitution(userId, institutionId, request))
                .isInstanceOf(InvalidInstitutionDataException.class)
                .hasMessage("Institution name cannot exceed 100 characters");
    }
    
    @Test
    void editInstitution_NegativeBalance_ThrowsException() {
        String userId = "3c925d70-6d8d-4e59-9d2c-2d86a5f0bf28";
        String institutionId = UUID.randomUUID().toString();
        
        Institution institution = new Institution();
        institution.setUserId(userId);
        institution.setInstitutionId(institutionId);
        institution.setInstitutionName("Test Bank");
        institution.setStartingBalance(1000.0);
        institution.setCurrentBalance(1200.0);
        institution.setCreatedAt(System.currentTimeMillis() / 1000L);
        
        when(institutionRepository.findByUserIdAndInstitutionId(userId, institutionId)).thenReturn(institution);
        
        com.cpsc.backend.model.EditInstitutionRequest request = new com.cpsc.backend.model.EditInstitutionRequest();
        request.setStartingBalance(-100.0);
        
        assertThatThrownBy(() -> institutionService.editInstitution(userId, institutionId, request))
                .isInstanceOf(InvalidInstitutionDataException.class)
                .hasMessage("Starting balance cannot be negative");
    }
    
    @Test
    void editInstitution_NullUserId_ThrowsException() {
        com.cpsc.backend.model.EditInstitutionRequest request = new com.cpsc.backend.model.EditInstitutionRequest();
        
        assertThatThrownBy(() -> institutionService.editInstitution(null, "inst-123", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("User ID cannot be null or empty");
    }
    
    @Test
    void editInstitution_NullInstitutionId_ThrowsException() {
        com.cpsc.backend.model.EditInstitutionRequest request = new com.cpsc.backend.model.EditInstitutionRequest();
        
        assertThatThrownBy(() -> institutionService.editInstitution("user-123", null, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Institution ID cannot be null or empty");
    }

    @Test
    void editInstitution_UpdateAllocatedPercent_Success() {
        String userId = "3c925d70-6d8d-4e59-9d2c-2d86a5f0bf28";
        String institutionId = UUID.randomUUID().toString();
        
        Institution institution = new Institution();
        institution.setUserId(userId);
        institution.setInstitutionId(institutionId);
        institution.setInstitutionName("Test Bank");
        institution.setStartingBalance(1000.0);
        institution.setCurrentBalance(1000.0);
        institution.setCreatedAt(System.currentTimeMillis() / 1000L);
        institution.setAllocatedPercent(0);
        
        when(institutionRepository.findByUserIdAndInstitutionId(userId, institutionId)).thenReturn(institution);
        doNothing().when(institutionRepository).save(any(Institution.class));
        
        com.cpsc.backend.model.EditInstitutionRequest request = new com.cpsc.backend.model.EditInstitutionRequest();
        request.setAllocatedPercent(50);
        
        InstitutionResponse response = institutionService.editInstitution(userId, institutionId, request);
        
        assertThat(response.getAllocatedPercent()).isEqualTo(50);
        verify(institutionRepository).save(any(Institution.class));
    }

    @Test
    void editInstitution_AllocatedPercentMaxValue_Success() {
        String userId = "3c925d70-6d8d-4e59-9d2c-2d86a5f0bf28";
        String institutionId = UUID.randomUUID().toString();
        
        Institution institution = new Institution();
        institution.setUserId(userId);
        institution.setInstitutionId(institutionId);
        institution.setInstitutionName("Test Bank");
        institution.setStartingBalance(1000.0);
        institution.setCurrentBalance(1000.0);
        institution.setCreatedAt(System.currentTimeMillis() / 1000L);
        institution.setAllocatedPercent(0);
        
        when(institutionRepository.findByUserIdAndInstitutionId(userId, institutionId)).thenReturn(institution);
        doNothing().when(institutionRepository).save(any(Institution.class));
        
        com.cpsc.backend.model.EditInstitutionRequest request = new com.cpsc.backend.model.EditInstitutionRequest();
        request.setAllocatedPercent(100);
        
        InstitutionResponse response = institutionService.editInstitution(userId, institutionId, request);
        
        assertThat(response.getAllocatedPercent()).isEqualTo(100);
    }

    @Test
    void editInstitution_AllocatedPercentZero_Success() {
        String userId = "3c925d70-6d8d-4e59-9d2c-2d86a5f0bf28";
        String institutionId = UUID.randomUUID().toString();
        
        Institution institution = new Institution();
        institution.setUserId(userId);
        institution.setInstitutionId(institutionId);
        institution.setInstitutionName("Test Bank");
        institution.setStartingBalance(1000.0);
        institution.setCurrentBalance(1000.0);
        institution.setCreatedAt(System.currentTimeMillis() / 1000L);
        institution.setAllocatedPercent(50);
        
        when(institutionRepository.findByUserIdAndInstitutionId(userId, institutionId)).thenReturn(institution);
        doNothing().when(institutionRepository).save(any(Institution.class));
        
        com.cpsc.backend.model.EditInstitutionRequest request = new com.cpsc.backend.model.EditInstitutionRequest();
        request.setAllocatedPercent(0);
        
        InstitutionResponse response = institutionService.editInstitution(userId, institutionId, request);
        
        assertThat(response.getAllocatedPercent()).isEqualTo(0);
    }

    @Test
    void editInstitution_AllocatedPercentNegative_ThrowsException() {
        String userId = "3c925d70-6d8d-4e59-9d2c-2d86a5f0bf28";
        String institutionId = UUID.randomUUID().toString();
        
        Institution institution = new Institution();
        institution.setUserId(userId);
        institution.setInstitutionId(institutionId);
        institution.setInstitutionName("Test Bank");
        institution.setStartingBalance(1000.0);
        institution.setCurrentBalance(1000.0);
        institution.setCreatedAt(System.currentTimeMillis() / 1000L);
        institution.setAllocatedPercent(0);
        
        when(institutionRepository.findByUserIdAndInstitutionId(userId, institutionId)).thenReturn(institution);
        
        com.cpsc.backend.model.EditInstitutionRequest request = new com.cpsc.backend.model.EditInstitutionRequest();
        request.setAllocatedPercent(-1);
        
        assertThatThrownBy(() -> institutionService.editInstitution(userId, institutionId, request))
                .isInstanceOf(InvalidInstitutionDataException.class)
                .hasMessage("Allocated percent must be between 0 and 100");
    }

    @Test
    void editInstitution_AllocatedPercentExceedsMax_ThrowsException() {
        String userId = "3c925d70-6d8d-4e59-9d2c-2d86a5f0bf28";
        String institutionId = UUID.randomUUID().toString();
        
        Institution institution = new Institution();
        institution.setUserId(userId);
        institution.setInstitutionId(institutionId);
        institution.setInstitutionName("Test Bank");
        institution.setStartingBalance(1000.0);
        institution.setCurrentBalance(1000.0);
        institution.setCreatedAt(System.currentTimeMillis() / 1000L);
        institution.setAllocatedPercent(0);
        
        when(institutionRepository.findByUserIdAndInstitutionId(userId, institutionId)).thenReturn(institution);
        
        com.cpsc.backend.model.EditInstitutionRequest request = new com.cpsc.backend.model.EditInstitutionRequest();
        request.setAllocatedPercent(101);
        
        assertThatThrownBy(() -> institutionService.editInstitution(userId, institutionId, request))
                .isInstanceOf(InvalidInstitutionDataException.class)
                .hasMessage("Allocated percent must be between 0 and 100");
    }
}
