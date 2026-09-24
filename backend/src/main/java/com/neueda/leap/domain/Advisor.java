package com.neueda.leap.domain;

/**
 * Domain model for the `advisors` table.
 */
public class Advisor {
    private Integer advisorId;
    private String advisorName;
    private Integer userId;

    public Integer getAdvisorId() {
        return advisorId;
    }

    public void setAdvisorId(Integer advisorId) {
        this.advisorId = advisorId;
    }

    public String getAdvisorName() {
        return advisorName;
    }

    public void setAdvisorName(String advisorName) {
        this.advisorName = advisorName;
    }

    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }
}


