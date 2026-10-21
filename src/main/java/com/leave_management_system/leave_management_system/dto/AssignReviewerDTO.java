package com.leave_management_system.leave_management_system.dto;

public class AssignReviewerDTO {

    private Long reviewerId;
    private String reviewerEmail;

    public AssignReviewerDTO() {
    }

    public AssignReviewerDTO(Long reviewerId, String reviewerEmail) {
        this.reviewerId = reviewerId;
        this.reviewerEmail = reviewerEmail;
    }

    public Long getReviewerId() {
        return reviewerId;
    }

    public void setReviewerId(Long reviewerId) {
        this.reviewerId = reviewerId;
    }

    public String getReviewerEmail() {
        return reviewerEmail;
    }

    public void setReviewerEmail(String reviewerEmail) {
        this.reviewerEmail = reviewerEmail;
    }
}
