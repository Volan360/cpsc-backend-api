package com.cpsc.backend.repository;

import com.cpsc.backend.entity.Institution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Repository
public class InstitutionRepository {

    private static final Logger logger = LoggerFactory.getLogger(InstitutionRepository.class);
    
    private final DynamoDbTable<Institution> institutionTable;

    public InstitutionRepository(DynamoDbEnhancedClient enhancedClient,
                                  @Value("${dynamodb.table.name}") String tableName) {
        if (enhancedClient == null) {
            throw new IllegalArgumentException("DynamoDbEnhancedClient cannot be null");
        }
        if (tableName == null || tableName.trim().isEmpty()) {
            throw new IllegalArgumentException("Table name cannot be null or empty");
        }
        
        this.institutionTable = enhancedClient.table(tableName, TableSchema.fromBean(Institution.class));
        logger.info("InstitutionRepository initialized with table: {}", tableName);
    }

    public void save(Institution institution) {
        if (institution == null) {
            throw new IllegalArgumentException("Institution cannot be null");
        }
        
        validateInstitution(institution);
        
        logger.debug("Saving institution: userId={}, institutionId={}", 
            institution.getUserId(), institution.getInstitutionId());
        
        institutionTable.putItem(institution);
    }

    public Institution findByUserIdAndInstitutionId(String userId, String institutionId) {
        if (userId == null || userId.trim().isEmpty()) {
            throw new IllegalArgumentException("User ID cannot be null or empty");
        }
        if (institutionId == null || institutionId.trim().isEmpty()) {
            throw new IllegalArgumentException("Institution ID cannot be null or empty");
        }
        
        logger.debug("Finding institution: userId={}, institutionId={}", userId, institutionId);
        
        Key key = Key.builder()
                .partitionValue(userId)
                .sortValue(institutionId)
                .build();
        return institutionTable.getItem(key);
    }

    public List<Institution> findAllByUserId(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            throw new IllegalArgumentException("User ID cannot be null or empty");
        }
        
        logger.debug("Finding all institutions for userId={}", userId);
        
        QueryConditional queryConditional = QueryConditional
                .keyEqualTo(Key.builder().partitionValue(userId).build());
        
        List<Institution> institutions = institutionTable.query(queryConditional)
                .items()
                .stream()
                .collect(Collectors.toList());
        
        logger.debug("Found {} institutions for userId={}", institutions.size(), userId);
        
        return institutions;
    }
    
    /**
     * Paginated query for user's institutions
     * @param userId The user's ID
     * @param limit Maximum number of items to return
     * @param lastEvaluatedKey Pagination token from previous query (null for first page)
     * @return Page of institutions with pagination token
     */
    public PaginatedResult<Institution> findAllByUserIdPaginated(String userId, int limit, Map<String, AttributeValue> lastEvaluatedKey) {
        if (userId == null || userId.trim().isEmpty()) {
            throw new IllegalArgumentException("User ID cannot be null or empty");
        }
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("Limit must be between 1 and 100");
        }
        
        logger.debug("Finding institutions for userId={} with limit={}", userId, limit);
        
        QueryConditional queryConditional = QueryConditional
                .keyEqualTo(Key.builder().partitionValue(userId).build());
        
        QueryEnhancedRequest.Builder requestBuilder = QueryEnhancedRequest.builder()
                .queryConditional(queryConditional)
                .limit(limit);
        
        if (lastEvaluatedKey != null && !lastEvaluatedKey.isEmpty()) {
            requestBuilder.exclusiveStartKey(lastEvaluatedKey);
        }
        
        Page<Institution> page = institutionTable.query(requestBuilder.build())
                .stream()
                .findFirst()
                .orElse(Page.create(List.of()));
        
        List<Institution> institutions = page.items();
        Map<String, AttributeValue> nextToken = page.lastEvaluatedKey();
        
        logger.debug("Found {} institutions for userId={}, hasMore={}", 
            institutions.size(), userId, nextToken != null && !nextToken.isEmpty());
        
        return new PaginatedResult<>(institutions, nextToken);
    }
    
    /**
     * Simple wrapper for paginated results
     */
    public static class PaginatedResult<T> {
        private final List<T> items;
        private final Map<String, AttributeValue> lastEvaluatedKey;
        
        public PaginatedResult(List<T> items, Map<String, AttributeValue> lastEvaluatedKey) {
            this.items = items;
            this.lastEvaluatedKey = lastEvaluatedKey;
        }
        
        public List<T> getItems() {
            return items;
        }
        
        public Map<String, AttributeValue> getLastEvaluatedKey() {
            return lastEvaluatedKey;
        }
        
        public boolean hasMore() {
            return lastEvaluatedKey != null && !lastEvaluatedKey.isEmpty();
        }
    }

    public void delete(String userId, String institutionId) {
        if (userId == null || userId.trim().isEmpty()) {
            throw new IllegalArgumentException("User ID cannot be null or empty");
        }
        if (institutionId == null || institutionId.trim().isEmpty()) {
            throw new IllegalArgumentException("Institution ID cannot be null or empty");
        }
        
        logger.info("Deleting institution: userId={}, institutionId={}", userId, institutionId);
        
        Key key = Key.builder()
                .partitionValue(userId)
                .sortValue(institutionId)
                .build();
        institutionTable.deleteItem(key);
    }
    
    private void validateInstitution(Institution institution) {
        if (institution.getUserId() == null || institution.getUserId().trim().isEmpty()) {
            throw new IllegalArgumentException("Institution userId cannot be null or empty");
        }
        if (institution.getInstitutionId() == null || institution.getInstitutionId().trim().isEmpty()) {
            throw new IllegalArgumentException("Institution institutionId cannot be null or empty");
        }
    }
}
