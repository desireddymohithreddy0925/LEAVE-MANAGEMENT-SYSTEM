package com.leave_management_system.leave_management_system.dto;

public class TransactionReviewSummaryDTO {

    private long totalTransactions;
    private long totalPendingReview;
    private long totalUnderReview;
    private long totalReviewed;
    private long totalFlagged;
    private long totalResolved;
    private long totalLowRisk;
    private long totalMediumRisk;
    private long totalHighRisk;
    private long totalCriticalRisk;
    private long pendingHighRiskCount;
    private double reviewCompletionRate;

    public TransactionReviewSummaryDTO() {
    }

    public long getTotalTransactions() {
        return totalTransactions;
    }

    public void setTotalTransactions(long totalTransactions) {
        this.totalTransactions = totalTransactions;
    }

    public long getTotalPendingReview() {
        return totalPendingReview;
    }

    public void setTotalPendingReview(long totalPendingReview) {
        this.totalPendingReview = totalPendingReview;
    }

    public long getTotalUnderReview() {
        return totalUnderReview;
    }

    public void setTotalUnderReview(long totalUnderReview) {
        this.totalUnderReview = totalUnderReview;
    }

    public long getTotalReviewed() {
        return totalReviewed;
    }

    public void setTotalReviewed(long totalReviewed) {
        this.totalReviewed = totalReviewed;
    }

    public long getTotalFlagged() {
        return totalFlagged;
    }

    public void setTotalFlagged(long totalFlagged) {
        this.totalFlagged = totalFlagged;
    }

    public long getTotalResolved() {
        return totalResolved;
    }

    public void setTotalResolved(long totalResolved) {
        this.totalResolved = totalResolved;
    }

    public long getTotalLowRisk() {
        return totalLowRisk;
    }

    public void setTotalLowRisk(long totalLowRisk) {
        this.totalLowRisk = totalLowRisk;
    }

    public long getTotalMediumRisk() {
        return totalMediumRisk;
    }

    public void setTotalMediumRisk(long totalMediumRisk) {
        this.totalMediumRisk = totalMediumRisk;
    }

    public long getTotalHighRisk() {
        return totalHighRisk;
    }

    public void setTotalHighRisk(long totalHighRisk) {
        this.totalHighRisk = totalHighRisk;
    }

    public long getTotalCriticalRisk() {
        return totalCriticalRisk;
    }

    public void setTotalCriticalRisk(long totalCriticalRisk) {
        this.totalCriticalRisk = totalCriticalRisk;
    }

    public long getPendingHighRiskCount() {
        return pendingHighRiskCount;
    }

    public void setPendingHighRiskCount(long pendingHighRiskCount) {
        this.pendingHighRiskCount = pendingHighRiskCount;
    }

    public double getReviewCompletionRate() {
        return reviewCompletionRate;
    }

    public void setReviewCompletionRate(double reviewCompletionRate) {
        this.reviewCompletionRate = reviewCompletionRate;
    }
}
