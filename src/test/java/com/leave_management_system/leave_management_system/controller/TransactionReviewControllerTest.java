package com.leave_management_system.leave_management_system.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.leave_management_system.leave_management_system.dto.*;
import com.leave_management_system.leave_management_system.entity.ReviewStatus;
import com.leave_management_system.leave_management_system.entity.RiskLevel;
import com.leave_management_system.leave_management_system.entity.TransactionType;
import com.leave_management_system.leave_management_system.exception.GlobalExceptionHandler;
import com.leave_management_system.leave_management_system.exception.ResourceNotFoundException;
import com.leave_management_system.leave_management_system.service.TransactionReviewService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class TransactionReviewControllerTest {

    @Mock
    private TransactionReviewService transactionReviewService;

    @InjectMocks
    private TransactionReviewController transactionReviewController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        MappingJackson2HttpMessageConverter jacksonConverter = new MappingJackson2HttpMessageConverter();
        jacksonConverter.setObjectMapper(objectMapper);

        mockMvc = MockMvcBuilders.standaloneSetup(transactionReviewController)
                .setMessageConverters(new StringHttpMessageConverter(), jacksonConverter)
                .setControllerAdvice(new GlobalExceptionHandler(null))
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();
    }

    @Test
    @DisplayName("GET /api/transaction-reviews - Search transaction reviews returns page")
    void searchTransactionReviews_Success() throws Exception {
        TransactionReviewResponseDTO dto = new TransactionReviewResponseDTO();
        dto.setId(1L);
        dto.setTransactionType(TransactionType.LEAVE_APPROVAL);
        dto.setReviewStatus(ReviewStatus.PENDING_REVIEW);
        dto.setRiskLevel(RiskLevel.HIGH);
        dto.setCreatedAt(LocalDateTime.now());

        Page<TransactionReviewResponseDTO> page = new PageImpl<>(List.of(dto), PageRequest.of(0, 10), 1);

        when(transactionReviewService.searchTransactionReviews(isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(page);

        mockMvc.perform(get("/api/transaction-reviews")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.content[0].transactionType").value("LEAVE_APPROVAL"));

        verify(transactionReviewService).searchTransactionReviews(isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), any(Pageable.class));
    }

    @Test
    @DisplayName("GET /api/transaction-reviews/summary - Returns transaction review summary")
    void getSummaryMetrics_Success() throws Exception {
        TransactionReviewSummaryDTO summary = new TransactionReviewSummaryDTO();
        summary.setTotalTransactions(25L);
        summary.setTotalPendingReview(5L);
        summary.setTotalReviewed(20L);

        when(transactionReviewService.getSummaryMetrics()).thenReturn(summary);

        mockMvc.perform(get("/api/transaction-reviews/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTransactions").value(25))
                .andExpect(jsonPath("$.totalPendingReview").value(5))
                .andExpect(jsonPath("$.totalReviewed").value(20));

        verify(transactionReviewService).getSummaryMetrics();
    }

    @Test
    @DisplayName("GET /api/transaction-reviews/me - Returns personal transaction reviews")
    void getMyTransactionReviews_Success() throws Exception {
        TransactionReviewResponseDTO dto = new TransactionReviewResponseDTO();
        dto.setId(1L);
        dto.setActorEmail("user@example.com");
        dto.setCreatedAt(LocalDateTime.now());

        Page<TransactionReviewResponseDTO> page = new PageImpl<>(List.of(dto), PageRequest.of(0, 10), 1);
        UsernamePasswordAuthenticationToken principal = new UsernamePasswordAuthenticationToken(
                "user@example.com", null, Collections.emptyList());

        when(transactionReviewService.getMyTransactionReviews(eq("user@example.com"), any(Pageable.class)))
                .thenReturn(page);

        mockMvc.perform(get("/api/transaction-reviews/me")
                        .principal(principal)
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1));

        verify(transactionReviewService).getMyTransactionReviews(eq("user@example.com"), any(Pageable.class));
    }

    @Test
    @DisplayName("GET /api/transaction-reviews/employee/{employeeId} - Returns employee reviews")
    void getEmployeeTransactionReviews_Success() throws Exception {
        TransactionReviewResponseDTO dto = new TransactionReviewResponseDTO();
        dto.setId(1L);
        dto.setEmployeeId(10L);
        dto.setCreatedAt(LocalDateTime.now());

        Page<TransactionReviewResponseDTO> page = new PageImpl<>(List.of(dto), PageRequest.of(0, 10), 1);

        when(transactionReviewService.getEmployeeTransactionReviews(eq(10L), any(Pageable.class)))
                .thenReturn(page);

        mockMvc.perform(get("/api/transaction-reviews/employee/10")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].employeeId").value(10));

        verify(transactionReviewService).getEmployeeTransactionReviews(eq(10L), any(Pageable.class));
    }

    @Test
    @DisplayName("GET /api/transaction-reviews/{id} - Success returns transaction review")
    void getTransactionReviewById_Success() throws Exception {
        TransactionReviewResponseDTO dto = new TransactionReviewResponseDTO();
        dto.setId(1L);
        dto.setCreatedAt(LocalDateTime.now());

        when(transactionReviewService.getTransactionReviewById(1L)).thenReturn(dto);

        mockMvc.perform(get("/api/transaction-reviews/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));

        verify(transactionReviewService).getTransactionReviewById(1L);
    }

    @Test
    @DisplayName("GET /api/transaction-reviews/{id} - Not found returns 404")
    void getTransactionReviewById_NotFound() throws Exception {
        when(transactionReviewService.getTransactionReviewById(99L))
                .thenThrow(new ResourceNotFoundException("Transaction review not found with id: 99"));

        mockMvc.perform(get("/api/transaction-reviews/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));

        verify(transactionReviewService).getTransactionReviewById(99L);
    }

    @Test
    @DisplayName("POST /api/transaction-reviews/{id}/assign - Assigns reviewer")
    void assignReviewer_Success() throws Exception {
        AssignReviewerDTO request = new AssignReviewerDTO(5L, "auditor@example.com");
        TransactionReviewResponseDTO response = new TransactionReviewResponseDTO();
        response.setId(1L);
        response.setReviewerId(5L);
        response.setReviewerEmail("auditor@example.com");
        response.setCreatedAt(LocalDateTime.now());

        UsernamePasswordAuthenticationToken principal = new UsernamePasswordAuthenticationToken(
                "admin@example.com", null, Collections.emptyList());

        when(transactionReviewService.assignReviewer(eq(1L), any(AssignReviewerDTO.class), eq("admin@example.com")))
                .thenReturn(response);

        mockMvc.perform(post("/api/transaction-reviews/1/assign")
                        .principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewerId").value(5))
                .andExpect(jsonPath("$.reviewerEmail").value("auditor@example.com"));

        verify(transactionReviewService).assignReviewer(eq(1L), any(AssignReviewerDTO.class), eq("admin@example.com"));
    }

    @Test
    @DisplayName("POST /api/transaction-reviews/{id}/review - Submits review decision")
    void submitReview_Success() throws Exception {
        ReviewDecisionDTO request = new ReviewDecisionDTO(ReviewStatus.REVIEWED, "Reviewed and verified", "Verified");
        TransactionReviewResponseDTO response = new TransactionReviewResponseDTO();
        response.setId(1L);
        response.setReviewStatus(ReviewStatus.REVIEWED);
        response.setReviewNotes("Reviewed and verified");
        response.setCreatedAt(LocalDateTime.now());

        UsernamePasswordAuthenticationToken principal = new UsernamePasswordAuthenticationToken(
                "auditor@example.com", null, Collections.emptyList());

        when(transactionReviewService.submitReview(eq(1L), any(ReviewDecisionDTO.class), eq("auditor@example.com")))
                .thenReturn(response);

        mockMvc.perform(post("/api/transaction-reviews/1/review")
                        .principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewStatus").value("REVIEWED"))
                .andExpect(jsonPath("$.reviewNotes").value("Reviewed and verified"));

        verify(transactionReviewService).submitReview(eq(1L), any(ReviewDecisionDTO.class), eq("auditor@example.com"));
    }

    @Test
    @DisplayName("POST /api/transaction-reviews/{id}/flag - Flags transaction")
    void flagTransaction_Success() throws Exception {
        FlagTransactionDTO request = new FlagTransactionDTO("Suspicious approval pattern", "Flagging for investigation");
        TransactionReviewResponseDTO response = new TransactionReviewResponseDTO();
        response.setId(1L);
        response.setReviewStatus(ReviewStatus.FLAGGED);
        response.setRiskReason("Suspicious approval pattern");
        response.setCreatedAt(LocalDateTime.now());

        UsernamePasswordAuthenticationToken principal = new UsernamePasswordAuthenticationToken(
                "auditor@example.com", null, Collections.emptyList());

        when(transactionReviewService.flagTransaction(eq(1L), any(FlagTransactionDTO.class), eq("auditor@example.com")))
                .thenReturn(response);

        mockMvc.perform(post("/api/transaction-reviews/1/flag")
                        .principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewStatus").value("FLAGGED"));

        verify(transactionReviewService).flagTransaction(eq(1L), any(FlagTransactionDTO.class), eq("auditor@example.com"));
    }

    @Test
    @DisplayName("POST /api/transaction-reviews/{id}/resolve - Resolves flagged review")
    void resolveReview_Success() throws Exception {
        ResolveReviewDTO request = new ResolveReviewDTO("Mitigated by additional audit", "Confirmed valid justification");
        TransactionReviewResponseDTO response = new TransactionReviewResponseDTO();
        response.setId(1L);
        response.setReviewStatus(ReviewStatus.RESOLVED);
        response.setResolution("Mitigated by additional audit");
        response.setCreatedAt(LocalDateTime.now());

        UsernamePasswordAuthenticationToken principal = new UsernamePasswordAuthenticationToken(
                "auditor@example.com", null, Collections.emptyList());

        when(transactionReviewService.resolveReview(eq(1L), any(ResolveReviewDTO.class), eq("auditor@example.com")))
                .thenReturn(response);

        mockMvc.perform(post("/api/transaction-reviews/1/resolve")
                        .principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewStatus").value("RESOLVED"))
                .andExpect(jsonPath("$.resolution").value("Mitigated by additional audit"));

        verify(transactionReviewService).resolveReview(eq(1L), any(ResolveReviewDTO.class), eq("auditor@example.com"));
    }
}

