package com.cpsc.backend.service;

import com.cpsc.backend.entity.Institution;
import com.cpsc.backend.exception.InvalidInstitutionDataException;
import com.cpsc.backend.model.CreateInstitutionRequest;
import com.cpsc.backend.model.GetInstitutions200Response;
import com.cpsc.backend.model.InstitutionResponse;
import com.cpsc.backend.repository.InstitutionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InstitutionServiceTest {

    @Mock
    private InstitutionRepository institutionRepository;

    @InjectMocks
    private InstitutionService institutionService;

    private CreateInstitutionRequest validRequest;

    @BeforeEach
    void setUp() {
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
        verify(institutionRepository).save(any(Institution.class));
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
        institution.setCreatedAt(Instant.parse("2024-01-01T00:00:00Z"));

        when(institutionRepository.findAllByUserId("user-123")).thenReturn(List.of(institution));

        List<InstitutionResponse> responses = institutionService.getUserInstitutions("user-123");

        assertThat(responses).hasSize(1);
        InstitutionResponse mapped = responses.getFirst();
        assertThat(mapped.getInstitutionName()).isEqualTo("Mapped Bank");
        assertThat(mapped.getStartingBalance()).isEqualTo(500.0);
        assertThat(mapped.getUserId()).isEqualTo(UUID.fromString(institution.getUserId()));
        assertThat(mapped.getCreatedAt()).isEqualTo(OffsetDateTime.ofInstant(institution.getCreatedAt(), ZoneOffset.UTC));
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
        institution.setCreatedAt(Instant.now());

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
}
