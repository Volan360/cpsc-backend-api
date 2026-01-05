package com.cpsc.backend.entity;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

import java.util.List;

@DynamoDbBean
public class Institution {

    private String userId;
    private String institutionId;
    private String institutionName;
    private Double startingBalance;
    private Double currentBalance;
    private Long createdAt;
    private Integer allocatedPercent;
    private List<String> linkedGoals;

    public Institution() {
    }

    @DynamoDbPartitionKey
    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    @DynamoDbSortKey
    public String getInstitutionId() {
        return institutionId;
    }

    public void setInstitutionId(String institutionId) {
        this.institutionId = institutionId;
    }

    public String getInstitutionName() {
        return institutionName;
    }

    public void setInstitutionName(String institutionName) {
        this.institutionName = institutionName;
    }

    public Double getStartingBalance() {
        return startingBalance;
    }

    public void setStartingBalance(Double startingBalance) {
        this.startingBalance = startingBalance;
    }

    public Double getCurrentBalance() {
        return currentBalance;
    }

    public void setCurrentBalance(Double currentBalance) {
        this.currentBalance = currentBalance;
    }

    public Long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Long createdAt) {
        this.createdAt = createdAt;
    }

    public Integer getAllocatedPercent() {
        return allocatedPercent;
    }

    public void setAllocatedPercent(Integer allocatedPercent) {
        this.allocatedPercent = allocatedPercent;
    }

    public List<String> getLinkedGoals() {
        return linkedGoals;
    }

    public void setLinkedGoals(List<String> linkedGoals) {
        this.linkedGoals = linkedGoals;
    }
}
