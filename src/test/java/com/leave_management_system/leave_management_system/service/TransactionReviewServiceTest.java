package com.leave_management_system.leave_management_system.service;

import com.leave_management_system.leave_management_system.dto.AssignReviewerDTO;
import com.leave_management_system.leave_management_system.dto.FlagTransactionDTO;
import com.leave_management_system.leave_management_system.dto.ReviewDecisionDTO;
import com.leave_management_system.leave_management_system.dto.TransactionReviewResponseDTO;
import com.leave_management_system.leave_management_system.dto.TransactionReviewSummaryDTO;
import com.leave_management_system.leave_management_system.entity.*;
import com.leave_management_system.leave_management_system.exception.ResourceNotFoundException;
import com.leave_management_system.leave_management_system.repository.EmployeeRepository;
import com.leave_management_system.leave_management_system.repository.TransactionReviewRepository;
import com.leave_management_system.leave_management_system.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class TransactionReviewServiceTest {

    @Mock
    private TransactionReviewRepository transactionReviewRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    @InjectMocks
    private TransactionReviewService transactionReviewService;

    private User testUser;
    private Employee testEmployee;
    private LeaveRequest leaveRequest;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setEmail("actor@example.com");

        testEmployee = new Employee();
        testEmployee.setId(10L);
        testEmployee.setFirstName("John");
        testEmployee.setLastName("Doe");
        testEmployee.setUser(testUser);

        leaveRequest = new LeaveRequest();
        leaveRequest.setId(100L);
        leaveRequest.setEmployee(testEmployee);
        leaveRequest.setStartDate(LocalDate.now().plusDays(5));
        leaveRequest.setEndDate(LocalDate.now().plusDays(8));
        leaveRequest.setStatus(LeaveStatus.PENDING);
    }

    @Test
    @DisplayName("recordLeaveRequest: calculates risk, creates review, and saves record")
    void recordLeaveRequest_Success() {
        when(transactionReviewRepository.save(any(TransactionReview.class))).thenAnswer(inv -> inv.getArgument(0));

        TransactionReview review = transactionReviewService.recordLeaveRequest(leaveRequest, testUser, "actor@example.com");

        assertNotNull(review);
        assertEquals(TransactionType.LEAVE_REQUEST, review.getTransactionType());
        assertEquals("LeaveRequest", review.getEntityType());
        assertEquals(100L, review.getEntityId());
        assertEquals("actor@example.com", review.getActorEmail());
        assertNotNull(review.getRiskLevel());
        verify(transactionReviewRepository, times(1)).save(any(TransactionReview.class));
    }

    @Test
    @DisplayName("recordBalanceAdjustment: assigns HIGH risk for delta > 5 days")
    void recordBalanceAdjustment_HighDelta_AssignsHighRisk() {
        LeaveBalance balance = new LeaveBalance();
        balance.setId(50L);
        balance.setEmployee(testEmployee);
        balance.setTotal(20);
        balance.setAvailable(12);

        when(transactionReviewRepository.save(any(TransactionReview.class))).thenAnswer(inv -> inv.getArgument(0));

        TransactionReview review = transactionReviewService.recordBalanceAdjustment(balance, 10, 2, testUser, "admin@example.com");

        assertNotNull(review);
        assertEquals(RiskLevel.HIGH, review.getRiskLevel());
        assertEquals(ReviewStatus.FLAGGED, review.getReviewStatus());
        verify(transactionReviewRepository, times(1)).save(any(TransactionReview.class));
    }

    @Test
    @DisplayName("getTransactionReviewById: returns DTO when review exists")
    void getTransactionReviewById_Success() {
        TransactionReview review = new TransactionReview();
        review.setId(1L);
        review.setTransactionType(TransactionType.LEAVE_REQUEST);
        review.setRiskLevel(RiskLevel.LOW);
        review.setReviewStatus(ReviewStatus.PENDING_REVIEW);
        review.setCreatedAt(LocalDateTime.now());

        when(transactionReviewRepository.findById(1L)).thenReturn(Optional.of(review));

        TransactionReviewResponseDTO dto = transactionReviewService.getTransactionReviewById(1L);

        assertNotNull(dto);
        assertEquals(1L, dto.getId());
    }

    @Test
    @DisplayName("getTransactionReviewById: throws ResourceNotFoundException when review does not exist")
    void getTransactionReviewById_NotFound_ThrowsException() {
        when(transactionReviewRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> transactionReviewService.getTransactionReviewById(999L));
    }

    @Test
    @DisplayName("assignReviewer: assigns reviewer and moves status to UNDER_REVIEW")
    void assignReviewer_Success() {
        TransactionReview review = new TransactionReview();
        review.setId(1L);
        review.setReviewStatus(ReviewStatus.PENDING_REVIEW);

        User reviewer = new User();
        reviewer.setId(5L);
        reviewer.setEmail("reviewer@example.com");

        AssignReviewerDTO dto = new AssignReviewerDTO();
        dto.setReviewerId(5L);

        when(transactionReviewRepository.findById(1L)).thenReturn(Optional.of(review));
        when(userRepository.findById(5L)).thenReturn(Optional.of(reviewer));
        when(transactionReviewRepository.save(any(TransactionReview.class))).thenAnswer(inv -> inv.getArgument(0));

        TransactionReviewResponseDTO result = transactionReviewService.assignReviewer(1L, dto, "admin@example.com");

        assertNotNull(result);
        assertEquals(ReviewStatus.UNDER_REVIEW, review.getReviewStatus());
        assertEquals(reviewer, review.getReviewer());
        verify(transactionReviewRepository, times(1)).save(review);
    }

    @Test
    @DisplayName("submitReview: updates status to REVIEWED, notes, and sets reviewedAt timestamp")
    void submitReview_Success() {
        TransactionReview review = new TransactionReview();
        review.setId(1L);
        review.setReviewStatus(ReviewStatus.UNDER_REVIEW);

        ReviewDecisionDTO dto = new ReviewDecisionDTO();
        dto.setDecision(ReviewStatus.REVIEWED);
        dto.setNotes("Audit verified and approved");
        dto.setResolution("VERIFIED_OK");

        when(transactionReviewRepository.findById(1L)).thenReturn(Optional.of(review));
        when(transactionReviewRepository.save(any(TransactionReview.class))).thenAnswer(inv -> inv.getArgument(0));

        TransactionReviewResponseDTO result = transactionReviewService.submitReview(1L, dto, "auditor@example.com");

        assertNotNull(result);
        assertEquals(ReviewStatus.REVIEWED, review.getReviewStatus());
        assertEquals("Audit verified and approved", review.getReviewNotes());
        assertEquals("VERIFIED_OK", review.getResolution());
        assertNotNull(review.getReviewedAt());
        verify(transactionReviewRepository, times(1)).save(review);
    }

    @Test
    @DisplayName("flagTransaction: sets status to FLAGGED and appends auditor reason")
    void flagTransaction_Success() {
        TransactionReview review = new TransactionReview();
        review.setId(1L);
        review.setReviewStatus(ReviewStatus.PENDING_REVIEW);
        review.setRiskReason("Initial risk");

        FlagTransactionDTO dto = new FlagTransactionDTO();
        dto.setReason("Suspicious multiple back-to-back edits");

        when(transactionReviewRepository.findById(1L)).thenReturn(Optional.of(review));
        when(transactionReviewRepository.save(any(TransactionReview.class))).thenAnswer(inv -> inv.getArgument(0));

        TransactionReviewResponseDTO result = transactionReviewService.flagTransaction(1L, dto, "auditor@example.com");

        assertNotNull(result);
        assertEquals(ReviewStatus.FLAGGED, review.getReviewStatus());
        assertTrue(review.getRiskReason().contains("Auditor Flag: Suspicious multiple back-to-back edits"));
        verify(transactionReviewRepository, times(1)).save(review);
    }

    @Test
    @DisplayName("getSummaryMetrics: computes totals, risk breakdown, and review completion rate")
    void getSummaryMetrics_Success() {
        when(transactionReviewRepository.count()).thenReturn(10L);
        lenient().when(transactionReviewRepository.countByReviewStatus(any(ReviewStatus.class))).thenReturn(0L);
        lenient().when(transactionReviewRepository.countByReviewStatus(ReviewStatus.REVIEWED)).thenReturn(6L);
        lenient().when(transactionReviewRepository.countByReviewStatus(ReviewStatus.RESOLVED)).thenReturn(2L);
        lenient().when(transactionReviewRepository.countByRiskLevel(any(RiskLevel.class))).thenReturn(0L);
        lenient().when(transactionReviewRepository.countByRiskLevelAndReviewStatus(any(RiskLevel.class), any(ReviewStatus.class))).thenReturn(0L);

        TransactionReviewSummaryDTO summary = transactionReviewService.getSummaryMetrics();

        assertNotNull(summary);
        assertEquals(10L, summary.getTotalTransactions());
        assertEquals(6L, summary.getTotalReviewed());
        assertEquals(2L, summary.getTotalResolved());
        assertEquals(80.0, summary.getReviewCompletionRate());
    }
}
