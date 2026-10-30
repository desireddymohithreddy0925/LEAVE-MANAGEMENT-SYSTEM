package com.leave_management_system.leave_management_system.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.leave_management_system.leave_management_system.dto.AuditLogResponseDTO;
import com.leave_management_system.leave_management_system.dto.AuthorizationAuditResponseDTO;
import com.leave_management_system.leave_management_system.dto.TransactionReviewResponseDTO;
import com.leave_management_system.leave_management_system.exception.GlobalExceptionHandler;
import com.leave_management_system.leave_management_system.exception.ResourceNotFoundException;
import com.leave_management_system.leave_management_system.security.SecurityEventType;
import com.leave_management_system.leave_management_system.service.AuditService;
import com.leave_management_system.leave_management_system.service.AuthorizationAuditService;
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
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class AuditLogControllerTest {

    @Mock
    private AuditService auditService;

    @Mock
    private AuthorizationAuditService authorizationAuditService;

    @Mock
    private TransactionReviewService transactionReviewService;

    @InjectMocks
    private AuditLogController auditLogController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        MappingJackson2HttpMessageConverter jacksonConverter = new MappingJackson2HttpMessageConverter();
        jacksonConverter.setObjectMapper(objectMapper);

        mockMvc = MockMvcBuilders.standaloneSetup(auditLogController)
                .setMessageConverters(new StringHttpMessageConverter(), jacksonConverter)
                .setControllerAdvice(new GlobalExceptionHandler(null))
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();
    }

    @Test
    @DisplayName("GET /api/audit-logs - Search audit logs returns paginated list")
    void searchAuditLogs_Success() throws Exception {
        AuditLogResponseDTO dto = new AuditLogResponseDTO();
        dto.setId(1L);
        dto.setAction("CREATE");
        dto.setEntityType("LEAVE_REQUEST");
        dto.setTimestamp(LocalDateTime.now());

        Page<AuditLogResponseDTO> page = new PageImpl<>(List.of(dto), PageRequest.of(0, 10), 1);

        when(auditService.searchAuditLogs(isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(page);

        mockMvc.perform(get("/api/audit-logs")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.content[0].action").value("CREATE"));

        verify(auditService).searchAuditLogs(isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), any(Pageable.class));
    }

    @Test
    @DisplayName("GET /api/audit-logs/security-events - Returns security events audit page")
    void getSecurityEvents_Success() throws Exception {
        AuditLogResponseDTO dto = new AuditLogResponseDTO();
        dto.setId(2L);
        dto.setAction("BRUTE_FORCE_ATTEMPT");
        dto.setEntityType(SecurityEventType.ENTITY_TYPE_SECURITY_EVENT);
        dto.setTimestamp(LocalDateTime.now());

        Page<AuditLogResponseDTO> page = new PageImpl<>(List.of(dto), PageRequest.of(0, 10), 1);

        when(auditService.searchAuditLogs(isNull(), isNull(), eq(SecurityEventType.ENTITY_TYPE_SECURITY_EVENT), isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(page);

        mockMvc.perform(get("/api/audit-logs/security-events")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(2))
                .andExpect(jsonPath("$.content[0].action").value("BRUTE_FORCE_ATTEMPT"));

        verify(auditService).searchAuditLogs(isNull(), isNull(), eq(SecurityEventType.ENTITY_TYPE_SECURITY_EVENT), isNull(), isNull(), isNull(), any(Pageable.class));
    }

    @Test
    @DisplayName("GET /api/audit-logs/{id} - Success returns audit log")
    void getAuditLogById_Success() throws Exception {
        AuditLogResponseDTO dto = new AuditLogResponseDTO();
        dto.setId(1L);
        dto.setAction("UPDATE");
        dto.setTimestamp(LocalDateTime.now());

        when(auditService.getAuditLogById(1L)).thenReturn(dto);

        mockMvc.perform(get("/api/audit-logs/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.action").value("UPDATE"));

        verify(auditService).getAuditLogById(1L);
    }

    @Test
    @DisplayName("GET /api/audit-logs/{id} - Not found returns 404")
    void getAuditLogById_NotFound() throws Exception {
        when(auditService.getAuditLogById(99L)).thenThrow(new ResourceNotFoundException("Audit log not found with id: 99"));

        mockMvc.perform(get("/api/audit-logs/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));

        verify(auditService).getAuditLogById(99L);
    }

    @Test
    @DisplayName("GET /api/audit-logs/entity/{entityType}/{entityId} - Returns entity audit trail")
    void getAuditLogsByEntity_Success() throws Exception {
        AuditLogResponseDTO dto = new AuditLogResponseDTO();
        dto.setId(1L);
        dto.setEntityType("EMPLOYEE");
        dto.setEntityId(10L);
        dto.setTimestamp(LocalDateTime.now());

        Page<AuditLogResponseDTO> page = new PageImpl<>(List.of(dto), PageRequest.of(0, 10), 1);

        when(auditService.getAuditLogsByEntity(eq("EMPLOYEE"), eq(10L), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/audit-logs/entity/EMPLOYEE/10")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].entityType").value("EMPLOYEE"))
                .andExpect(jsonPath("$.content[0].entityId").value(10));

        verify(auditService).getAuditLogsByEntity(eq("EMPLOYEE"), eq(10L), any(Pageable.class));
    }

    @Test
    @DisplayName("GET /api/audit-logs/authorization - Returns authorization audits page")
    void getAuthorizationAudits_Success() throws Exception {
        AuthorizationAuditResponseDTO dto = new AuthorizationAuditResponseDTO();
        dto.setId(1L);
        dto.setAction("LEAVE_APPROVE");
        dto.setDecision("GRANTED");
        dto.setTimestamp(LocalDateTime.now());

        Page<AuthorizationAuditResponseDTO> page = new PageImpl<>(List.of(dto), PageRequest.of(0, 10), 1);

        when(authorizationAuditService.searchAuthorizationAudits(isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(page);

        mockMvc.perform(get("/api/audit-logs/authorization")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].decision").value("GRANTED"));

        verify(authorizationAuditService).searchAuthorizationAudits(isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), any(Pageable.class));
    }

    @Test
    @DisplayName("GET /api/audit-logs/transaction-reviews - Returns transaction reviews audit page")
    void getTransactionReviews_Success() throws Exception {
        TransactionReviewResponseDTO dto = new TransactionReviewResponseDTO();
        dto.setId(1L);
        dto.setCreatedAt(LocalDateTime.now());

        Page<TransactionReviewResponseDTO> page = new PageImpl<>(List.of(dto), PageRequest.of(0, 10), 1);

        when(transactionReviewService.searchTransactionReviews(isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(page);

        mockMvc.perform(get("/api/audit-logs/transaction-reviews")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1));

        verify(transactionReviewService).searchTransactionReviews(isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), any(Pageable.class));
    }
}

