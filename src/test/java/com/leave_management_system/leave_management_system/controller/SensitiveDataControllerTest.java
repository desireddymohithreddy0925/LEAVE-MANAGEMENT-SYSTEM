package com.leave_management_system.leave_management_system.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.leave_management_system.leave_management_system.dto.*;
import com.leave_management_system.leave_management_system.entity.Employee;
import com.leave_management_system.leave_management_system.exception.GlobalExceptionHandler;
import com.leave_management_system.leave_management_system.exception.ResourceNotFoundException;
import com.leave_management_system.leave_management_system.security.SecurityAuthorizationService;
import com.leave_management_system.leave_management_system.security.sensitive.CryptoService;
import com.leave_management_system.leave_management_system.security.sensitive.DataAnonymizationService;
import com.leave_management_system.leave_management_system.security.sensitive.DataMaskingService;
import com.leave_management_system.leave_management_system.security.sensitive.SensitiveType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class SensitiveDataControllerTest {

    @Mock
    private DataMaskingService maskingService;

    @Mock
    private CryptoService cryptoService;

    @Mock
    private DataAnonymizationService anonymizationService;

    @Mock
    private SecurityAuthorizationService securityAuthorizationService;

    @InjectMocks
    private SensitiveDataController sensitiveDataController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        MappingJackson2HttpMessageConverter jacksonConverter = new MappingJackson2HttpMessageConverter();
        jacksonConverter.setObjectMapper(objectMapper);

        mockMvc = MockMvcBuilders.standaloneSetup(sensitiveDataController)
                .setMessageConverters(new StringHttpMessageConverter(), jacksonConverter)
                .setControllerAdvice(new GlobalExceptionHandler(null))
                .build();
    }

    @Test
    @DisplayName("POST /api/sensitive-data/mask - Masks email value")
    void maskData_Email_Success() throws Exception {
        MaskRequestDTO request = new MaskRequestDTO("john.doe@example.com", SensitiveType.EMAIL);

        when(maskingService.maskEmail("john.doe@example.com")).thenReturn("j***e@example.com");

        mockMvc.perform(post("/api/sensitive-data/mask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maskedValue").value("j***e@example.com"))
                .andExpect(jsonPath("$.type").value("EMAIL"))
                .andExpect(jsonPath("$.originalLength").value("john.doe@example.com".length()));

        verify(maskingService).maskEmail("john.doe@example.com");
    }

    @Test
    @DisplayName("POST /api/sensitive-data/mask - Masks phone value")
    void maskData_Phone_Success() throws Exception {
        MaskRequestDTO request = new MaskRequestDTO("+1234567890", SensitiveType.PHONE);

        when(maskingService.maskPhone("+1234567890")).thenReturn("+123****890");

        mockMvc.perform(post("/api/sensitive-data/mask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maskedValue").value("+123****890"))
                .andExpect(jsonPath("$.type").value("PHONE"));

        verify(maskingService).maskPhone("+1234567890");
    }

    @Test
    @DisplayName("POST /api/sensitive-data/encrypt - Encrypts data")
    void encryptData_Success() throws Exception {
        CryptoRequestDTO request = new CryptoRequestDTO("sensitive text");

        when(cryptoService.encrypt("sensitive text")).thenReturn("aes256:ciphertext");

        mockMvc.perform(post("/api/sensitive-data/encrypt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("aes256:ciphertext"))
                .andExpect(jsonPath("$.encrypted").value(true));

        verify(cryptoService).encrypt("sensitive text");
    }

    @Test
    @DisplayName("POST /api/sensitive-data/decrypt - Decrypts data")
    void decryptData_Success() throws Exception {
        CryptoRequestDTO request = new CryptoRequestDTO("aes256:ciphertext");

        when(cryptoService.decrypt("aes256:ciphertext")).thenReturn("plaintext");

        mockMvc.perform(post("/api/sensitive-data/decrypt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("plaintext"))
                .andExpect(jsonPath("$.encrypted").value(false));

        verify(cryptoService).decrypt("aes256:ciphertext");
    }

    @Test
    @DisplayName("POST /api/sensitive-data/anonymize/{employeeId} - Anonymizes employee")
    void anonymizeEmployee_Success() throws Exception {
        AnonymizeResponseDTO response = new AnonymizeResponseDTO(10L, LocalDateTime.now(), "ANONYMIZED", "Employee data anonymized successfully");

        when(anonymizationService.anonymizeEmployee(10L)).thenReturn(response);

        mockMvc.perform(post("/api/sensitive-data/anonymize/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employeeId").value(10))
                .andExpect(jsonPath("$.status").value("ANONYMIZED"));

        verify(anonymizationService).anonymizeEmployee(10L);
    }

    @Test
    @DisplayName("GET /api/sensitive-data/me/export - Exports authenticated user personal data")
    void exportMyPersonalData_Success() throws Exception {
        Employee emp = new Employee();
        emp.setId(7L);
        UsernamePasswordAuthenticationToken principal = new UsernamePasswordAuthenticationToken(
                "user@example.com", null, Collections.emptyList());

        PersonalDataExportDTO export = new PersonalDataExportDTO();
        export.setExportTimestamp(LocalDateTime.now());

        when(securityAuthorizationService.getAuthenticatedEmployee(any(Authentication.class))).thenReturn(Optional.of(emp));
        when(anonymizationService.exportPersonalData(7L)).thenReturn(export);

        mockMvc.perform(get("/api/sensitive-data/me/export").principal(principal))
                .andExpect(status().isOk());

        verify(anonymizationService).exportPersonalData(7L);
    }

    @Test
    @DisplayName("GET /api/sensitive-data/me/export - Employee profile not found returns 404")
    void exportMyPersonalData_NotFound() throws Exception {
        UsernamePasswordAuthenticationToken principal = new UsernamePasswordAuthenticationToken(
                "user@example.com", null, Collections.emptyList());

        when(securityAuthorizationService.getAuthenticatedEmployee(any(Authentication.class))).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/sensitive-data/me/export").principal(principal))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("GET /api/sensitive-data/export/{employeeId} - Admin exports employee personal data")
    void exportEmployeePersonalData_Success() throws Exception {
        PersonalDataExportDTO export = new PersonalDataExportDTO();
        export.setExportTimestamp(LocalDateTime.now());

        when(anonymizationService.exportPersonalData(10L)).thenReturn(export);

        mockMvc.perform(get("/api/sensitive-data/export/10"))
                .andExpect(status().isOk());

        verify(anonymizationService).exportPersonalData(10L);
    }
}

