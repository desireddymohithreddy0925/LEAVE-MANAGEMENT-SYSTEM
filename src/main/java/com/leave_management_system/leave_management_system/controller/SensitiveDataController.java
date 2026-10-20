package com.leave_management_system.leave_management_system.controller;

import com.leave_management_system.leave_management_system.dto.*;
import com.leave_management_system.leave_management_system.entity.Employee;
import com.leave_management_system.leave_management_system.exception.ResourceNotFoundException;
import com.leave_management_system.leave_management_system.security.SecurityAuthorizationService;
import com.leave_management_system.leave_management_system.security.sensitive.CryptoService;
import com.leave_management_system.leave_management_system.security.sensitive.DataAnonymizationService;
import com.leave_management_system.leave_management_system.security.sensitive.DataMaskingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/sensitive-data")
@Validated
@Tag(name = "Sensitive Data Protection API", description = "Endpoints for cryptographic operations, data masking, GDPR anonymization, and data portability")
public class SensitiveDataController {

    private final DataMaskingService maskingService;
    private final CryptoService cryptoService;
    private final DataAnonymizationService anonymizationService;
    private final SecurityAuthorizationService securityAuthorizationService;

    public SensitiveDataController(DataMaskingService maskingService,
                                   CryptoService cryptoService,
                                   DataAnonymizationService anonymizationService,
                                   SecurityAuthorizationService securityAuthorizationService) {
        this.maskingService = maskingService;
        this.cryptoService = cryptoService;
        this.anonymizationService = anonymizationService;
        this.securityAuthorizationService = securityAuthorizationService;
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/mask")
    @Operation(summary = "Preview data masking", description = "Applies masking rules according to the sensitive data type.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully masked value"),
            @ApiResponse(responseCode = "400", description = "Invalid request payload")
    })
    public ResponseEntity<MaskResponseDTO> maskData(@Valid @RequestBody MaskRequestDTO dto) {
        String input = dto.getValue();
        String masked = switch (dto.getType()) {
            case PHONE -> maskingService.maskPhone(input);
            case EMAIL -> maskingService.maskEmail(input);
            case SALARY -> {
                try {
                    yield maskingService.maskSalaryDisplay(new BigDecimal(input));
                } catch (Exception e) {
                    yield "[CONFIDENTIAL]";
                }
            }
            case TOKEN -> maskingService.maskToken(input);
            case PASSWORD, CREDENTIAL -> maskingService.maskPassword(input);
            case CREDIT_CARD -> maskingService.maskCreditCard(input);
            case NATIONAL_ID -> maskingService.maskNationalId(input);
            case GENERIC -> maskingService.maskGeneric(input, 2, 2, '*');
        };

        return ResponseEntity.ok(new MaskResponseDTO(masked, dto.getType(), input != null ? input.length() : 0));
    }

    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ADMIN_SECURITY_VIEW')")
    @PostMapping("/encrypt")
    @Operation(summary = "Encrypt sensitive data", description = "Encrypts plaintext using AES-256-GCM authenticated encryption.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Data encrypted successfully"),
            @ApiResponse(responseCode = "403", description = "Access denied")
    })
    public ResponseEntity<CryptoResponseDTO> encryptData(@Valid @RequestBody CryptoRequestDTO dto) {
        String ciphertext = cryptoService.encrypt(dto.getData());
        return ResponseEntity.ok(new CryptoResponseDTO(ciphertext, true));
    }

    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ADMIN_SECURITY_VIEW')")
    @PostMapping("/decrypt")
    @Operation(summary = "Decrypt sensitive data", description = "Decrypts ciphertext and verifies GCM authentication integrity.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Data decrypted successfully"),
            @ApiResponse(responseCode = "400", description = "Decryption or authentication failed"),
            @ApiResponse(responseCode = "403", description = "Access denied")
    })
    public ResponseEntity<CryptoResponseDTO> decryptData(@Valid @RequestBody CryptoRequestDTO dto) {
        String plaintext = cryptoService.decrypt(dto.getData());
        return ResponseEntity.ok(new CryptoResponseDTO(plaintext, false));
    }

    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ADMIN_USER_MANAGE')")
    @PostMapping("/anonymize/{employeeId:\\d+}")
    @Operation(summary = "Anonymize employee PII", description = "Anonymizes employee personal data in compliance with GDPR Right to Erasure.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Employee anonymized successfully"),
            @ApiResponse(responseCode = "404", description = "Employee not found"),
            @ApiResponse(responseCode = "403", description = "Access denied")
    })
    public ResponseEntity<AnonymizeResponseDTO> anonymizeEmployee(@PathVariable Long employeeId) {
        AnonymizeResponseDTO response = anonymizationService.anonymizeEmployee(employeeId);
        return ResponseEntity.ok(response);
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/me/export")
    @Operation(summary = "Export own personal data", description = "Provides a complete export of personal data held for the authenticated employee.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Personal data exported successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "404", description = "Employee profile not found")
    })
    public ResponseEntity<PersonalDataExportDTO> exportMyPersonalData(Authentication authentication) {
        Employee employee = securityAuthorizationService.getAuthenticatedEmployee(authentication)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated employee profile not found"));
        PersonalDataExportDTO export = anonymizationService.exportPersonalData(employee.getId());
        return ResponseEntity.ok(export);
    }

    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('EMPLOYEE_VIEW')")
    @GetMapping("/export/{employeeId:\\d+}")
    @Operation(summary = "Admin export of employee personal data", description = "Exports all personal data held for a specific employee.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Personal data exported successfully"),
            @ApiResponse(responseCode = "404", description = "Employee not found"),
            @ApiResponse(responseCode = "403", description = "Access denied")
    })
    public ResponseEntity<PersonalDataExportDTO> exportEmployeePersonalData(@PathVariable Long employeeId) {
        PersonalDataExportDTO export = anonymizationService.exportPersonalData(employeeId);
        return ResponseEntity.ok(export);
    }
}
