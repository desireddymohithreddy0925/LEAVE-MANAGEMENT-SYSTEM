package com.leave_management_system.leave_management_system.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leave_management_system.leave_management_system.dto.*;
import com.leave_management_system.leave_management_system.entity.*;
import com.leave_management_system.leave_management_system.repository.*;
import com.leave_management_system.leave_management_system.security.JwtUtils;
import com.leave_management_system.leave_management_system.security.sensitive.CryptoService;
import com.leave_management_system.leave_management_system.security.sensitive.DataAnonymizationService;
import com.leave_management_system.leave_management_system.security.sensitive.DataMaskingService;
import com.leave_management_system.leave_management_system.security.sensitive.SensitiveType;
import com.leave_management_system.leave_management_system.service.AuditService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
public class SensitiveDataProtectionIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

    @Autowired
    private CryptoService cryptoService;

    @Autowired
    private DataMaskingService maskingService;

    @Autowired
    private DataAnonymizationService anonymizationService;

    @Autowired
    private AuditService auditService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private LeaveTypeRepository leaveTypeRepository;

    @Autowired
    private LeaveRequestRepository leaveRequestRepository;

    @Autowired
    private LeaveBalanceRepository leaveBalanceRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private AuthorizationAuditLogRepository authorizationAuditLogRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private LoginHistoryRepository loginHistoryRepository;

    @Autowired
    private com.leave_management_system.leave_management_system.repository.TransactionReviewRepository transactionReviewRepository;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User adminUser;
    private String adminToken;

    private User employeeUser;
    private Employee employee;
    private String employeeToken;

    private LeaveType leaveType;
    private LeaveRequest leaveRequest;

    @BeforeEach
    public void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);

        // 1. Roles & Permissions setup
        Role adminRole = roleRepository.findByName("ADMIN").orElseGet(() -> roleRepository.save(new Role("ADMIN")));
        Role empRole = roleRepository.findByName("EMPLOYEE").orElseGet(() -> roleRepository.save(new Role("EMPLOYEE")));

        Permission secViewPerm = permissionRepository.findByName("ADMIN_SECURITY_VIEW")
                .orElseGet(() -> permissionRepository.save(new Permission("ADMIN_SECURITY_VIEW", "Security Admin View")));
        Permission userManagePerm = permissionRepository.findByName("ADMIN_USER_MANAGE")
                .orElseGet(() -> permissionRepository.save(new Permission("ADMIN_USER_MANAGE", "User Management")));
        Permission empViewPerm = permissionRepository.findByName("EMPLOYEE_VIEW")
                .orElseGet(() -> permissionRepository.save(new Permission("EMPLOYEE_VIEW", "View Employee")));

        adminRole.getPermissions().add(secViewPerm);
        adminRole.getPermissions().add(userManagePerm);
        adminRole.getPermissions().add(empViewPerm);
        roleRepository.save(adminRole);

        // 2. Admin User Setup
        adminUser = new User();
        adminUser.setEmail("sens-admin-" + uniqueSuffix + "@test.com");
        adminUser.setPassword(passwordEncoder.encode("AdminPass123!"));
        adminUser.setActive(true);
        adminUser.getRoles().add(adminRole);
        adminUser = userRepository.save(adminUser);

        List<SimpleGrantedAuthority> adminAuthorities = new ArrayList<>();
        adminAuthorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
        adminAuthorities.add(new SimpleGrantedAuthority("ADMIN_SECURITY_VIEW"));
        adminAuthorities.add(new SimpleGrantedAuthority("ADMIN_USER_MANAGE"));
        adminAuthorities.add(new SimpleGrantedAuthority("EMPLOYEE_VIEW"));
        UserDetails adminDetails = new org.springframework.security.core.userdetails.User(
                adminUser.getEmail(), adminUser.getPassword(), adminAuthorities);
        adminToken = jwtUtils.generateJwtToken(new UsernamePasswordAuthenticationToken(adminDetails, null, adminDetails.getAuthorities()));

        // 3. Employee User Setup
        employeeUser = new User();
        employeeUser.setEmail("sens-emp-" + uniqueSuffix + "@test.com");
        employeeUser.setPassword(passwordEncoder.encode("EmpPass123!"));
        employeeUser.setActive(true);
        employeeUser.getRoles().add(empRole);
        employeeUser = userRepository.save(employeeUser);

        employee = new Employee();
        employee.setFirstName("John");
        employee.setLastName("Doe");
        employee.setEmployeeCode("EMP-" + uniqueSuffix);
        employee.setPhone("+1-555-876-5432");
        employee.setSalary(BigDecimal.valueOf(85000));
        employee.setStatus("ACTIVE");
        employee.setUser(employeeUser);
        employee = employeeRepository.save(employee);

        List<SimpleGrantedAuthority> empAuthorities = new ArrayList<>();
        empAuthorities.add(new SimpleGrantedAuthority("ROLE_EMPLOYEE"));
        UserDetails empDetails = new org.springframework.security.core.userdetails.User(
                employeeUser.getEmail(), employeeUser.getPassword(), empAuthorities);
        employeeToken = jwtUtils.generateJwtToken(new UsernamePasswordAuthenticationToken(empDetails, null, empDetails.getAuthorities()));

        // 4. Leave Setup
        leaveType = new LeaveType();
        leaveType.setName("Annual-" + uniqueSuffix);
        leaveType.setDefaultDays(20);
        leaveType = leaveTypeRepository.save(leaveType);

        LeaveBalance balance = new LeaveBalance();
        balance.setEmployee(employee);
        balance.setLeaveType(leaveType);
        balance.setYear(LocalDate.now().getYear());
        balance.setTotal(20);
        balance.setAvailable(18);
        balance.setUsed(2);
        leaveBalanceRepository.save(balance);

        leaveRequest = new LeaveRequest();
        leaveRequest.setEmployee(employee);
        leaveRequest.setLeaveType(leaveType);
        leaveRequest.setStartDate(LocalDate.now().plusDays(5));
        leaveRequest.setEndDate(LocalDate.now().plusDays(7));
        leaveRequest.setStatus(LeaveStatus.PENDING);
        leaveRequest.setReason("Medical checkup and personal leave");
        leaveRequest = leaveRequestRepository.save(leaveRequest);
    }

    @AfterEach
    public void cleanup() {
        transactionReviewRepository.deleteAll();
        leaveRequestRepository.deleteAll();
        leaveBalanceRepository.deleteAll();
        leaveTypeRepository.deleteAll();
        authorizationAuditLogRepository.deleteAll();
        auditLogRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        loginHistoryRepository.deleteAll();

        if (employee != null && employee.getId() != null) {
            employeeRepository.deleteById(employee.getId());
            employee = null;
        }
        if (employeeUser != null && employeeUser.getId() != null) {
            userRepository.deleteById(employeeUser.getId());
            employeeUser = null;
        }
        if (adminUser != null && adminUser.getId() != null) {
            userRepository.deleteById(adminUser.getId());
            adminUser = null;
        }
        SecurityContextHolder.clearContext();
    }

    // 1. AES-256-GCM Encryption & Decryption round trip
    @Test
    void aesGcmEncryption_EncryptAndDecrypt_RoundTripSuccess() {
        String plaintext = "HighlyConfidentialSalary$150,000+PersonalTaxID_999-88-7777";

        String ciphertext = cryptoService.encrypt(plaintext);
        assertNotNull(ciphertext);
        assertTrue(ciphertext.startsWith(CryptoService.ENCRYPTION_PREFIX));
        assertNotEquals(plaintext, ciphertext);

        String decrypted = cryptoService.decrypt(ciphertext);
        assertEquals(plaintext, decrypted);
    }

    // 2. AES-GCM Tampering detection
    @Test
    void aesGcmEncryption_TamperedCiphertext_ThrowsSecurityException() {
        String plaintext = "SensitiveFinancialInformation";
        String ciphertext = cryptoService.encrypt(plaintext);

        String base64Payload = ciphertext.substring(CryptoService.ENCRYPTION_PREFIX.length());
        byte[] raw = Base64.getDecoder().decode(base64Payload);

        // Flip bit in the payload
        raw[raw.length - 1] ^= 0x01;
        String tamperedCiphertext = CryptoService.ENCRYPTION_PREFIX + Base64.getEncoder().encodeToString(raw);

        assertThrows(SecurityException.class, () -> cryptoService.decrypt(tamperedCiphertext));
    }

    // 3. Blind indexing generates consistent deterministic hash
    @Test
    void blindIndex_GeneratesConsistentHmacSha256() {
        String value = "john.doe@company.com";
        String index1 = cryptoService.generateHmacBlindIndex(value);
        String index2 = cryptoService.generateHmacBlindIndex("  JOHN.DOE@company.com  ");

        assertNotNull(index1);
        assertEquals(index1, index2, "HMAC blind index should be deterministic and case-insensitive");
    }

    // 4. Data masking for phone numbers
    @Test
    void dataMasking_PhoneVariations_MasksCorrectly() {
        assertEquals("+* *******5432", maskingService.maskPhone("+1-555-876-5432"));
        assertEquals("******3210", maskingService.maskPhone("9876543210"));
        assertEquals("****", maskingService.maskPhone("1234"));
        assertNull(maskingService.maskPhone(null));
    }

    // 5. Data masking for email addresses
    @Test
    void dataMasking_EmailVariations_MasksCorrectly() {
        assertEquals("j***e@company.com", maskingService.maskEmail("john.doe@company.com"));
        assertEquals("a*@company.com", maskingService.maskEmail("ab@company.com"));
        assertEquals("* @company.com", maskingService.maskEmail("a@company.com"));
        assertNull(maskingService.maskEmail(null));
    }

    // 6. Data masking for salary, tokens, and credentials
    @Test
    void dataMasking_SalaryAndCredentials_MasksSecurely() {
        assertNull(maskingService.maskSalary(BigDecimal.valueOf(95000)));
        assertEquals("[CONFIDENTIAL]", maskingService.maskSalaryDisplay(BigDecimal.valueOf(95000)));
        assertEquals("********", maskingService.maskPassword("SuperSecretP@ss123!"));
        assertTrue(maskingService.maskToken("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.payload.sig").contains("[REDACTED]"));
        assertEquals("************4444", maskingService.maskCreditCard("4111222233334444"));
        assertEquals("***-**-6789", maskingService.maskNationalId("123-45-6789"));
    }

    // 7. AuditService sensitive field redaction
    @Test
    void auditService_RedactsSensitiveFields_InAuditLogs() {
        String rawJson = "{\"email\":\"user@test.com\",\"salary\":\"95000.00\",\"phone\":\"+1 555-123-4567\",\"password\":\"Secret123\",\"token\":\"jwt.token.abc\"}";
        String sanitized = auditService.sanitizeValue(rawJson);

        assertFalse(sanitized.contains("95000.00"), "Salary must be redacted from audit logs");
        assertFalse(sanitized.contains("+1 555-123-4567"), "Phone must be redacted from audit logs");
        assertFalse(sanitized.contains("Secret123"), "Password must be redacted from audit logs");
        assertFalse(sanitized.contains("jwt.token.abc"), "Token must be redacted from audit logs");
        assertTrue(sanitized.contains("[REDACTED]"));
    }

    // 8. REST: Mask preview endpoint
    @Test
    void restApi_MaskPreviewEndpoint_Success() throws Exception {
        MaskRequestDTO dto = new MaskRequestDTO("+1 555-987-6543", SensitiveType.PHONE);

        mockMvc.perform(post("/api/sensitive-data/mask")
                        .header("Authorization", "Bearer " + employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("PHONE"))
                .andExpect(jsonPath("$.maskedValue").value("+* *******6543"));
    }

    // 9. REST: Encrypt and decrypt endpoints as Admin
    @Test
    void restApi_EncryptAndDecryptEndpoints_AsAdmin_Success() throws Exception {
        CryptoRequestDTO encryptReq = new CryptoRequestDTO("ConfidentialDatabaseSecret123");

        String encResultJson = mockMvc.perform(post("/api/sensitive-data/encrypt")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(encryptReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.encrypted").value(true))
                .andReturn().getResponse().getContentAsString();

        CryptoResponseDTO encResponse = objectMapper.readValue(encResultJson, CryptoResponseDTO.class);
        assertNotNull(encResponse.getResult());
        assertTrue(encResponse.getResult().startsWith(CryptoService.ENCRYPTION_PREFIX));

        CryptoRequestDTO decryptReq = new CryptoRequestDTO(encResponse.getResult());
        mockMvc.perform(post("/api/sensitive-data/decrypt")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(decryptReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.encrypted").value(false))
                .andExpect(jsonPath("$.result").value("ConfidentialDatabaseSecret123"));
    }

    // 10. REST: Encrypt endpoint forbidden for unauthorized employee
    @Test
    void restApi_EncryptEndpoint_AsRegularEmployee_Forbidden() throws Exception {
        CryptoRequestDTO encryptReq = new CryptoRequestDTO("Secret");

        mockMvc.perform(post("/api/sensitive-data/encrypt")
                        .header("Authorization", "Bearer " + employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(encryptReq)))
                .andExpect(status().isForbidden());
    }

    // 11. REST: Anonymize departed employee PII (GDPR Right to Erasure)
    @Test
    void restApi_AnonymizeDepartedEmployee_Success() throws Exception {
        mockMvc.perform(post("/api/sensitive-data/anonymize/" + employee.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ANONYMIZED"))
                .andExpect(jsonPath("$.employeeId").value(employee.getId()));

        Employee updated = employeeRepository.findById(employee.getId()).orElseThrow();
        assertEquals("Anonymized", updated.getFirstName());
        assertEquals("Employee", updated.getLastName());
        assertNull(updated.getPhone());
        assertEquals(0, BigDecimal.ZERO.compareTo(updated.getSalary()));
        assertEquals("INACTIVE", updated.getStatus());

        User updatedUser = userRepository.findById(employeeUser.getId()).orElseThrow();
        assertFalse(updatedUser.isActive());
        assertTrue(updatedUser.getEmail().startsWith("anonymized."));
    }

    // 12. REST: Export personal data for current employee (GDPR Portability)
    @Test
    void restApi_ExportPersonalData_AsSelf_Success() throws Exception {
        mockMvc.perform(get("/api/sensitive-data/me/export")
                        .header("Authorization", "Bearer " + employeeToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employee.id").value(employee.getId()))
                .andExpect(jsonPath("$.employee.firstName").value("John"))
                .andExpect(jsonPath("$.employee.phone").value("+1-555-876-5432"))
                .andExpect(jsonPath("$.leaveRequests").isArray())
                .andExpect(jsonPath("$.leaveBalances").isArray());
    }

    // 13. Field-Level Security: Jackson serializes salary/phone securely
    @Test
    void fieldLevelSecurity_EmployeeResponseDTO_ConcealsSalaryFromNonAdmin() throws Exception {
        EmployeeResponseDTO dto = EmployeeResponseDTO.fromEntity(employee);

        // Under unauthenticated / unauthorized context, Jackson masks phone and conceals salary
        SecurityContextHolder.clearContext();
        String jsonUnauthorized = objectMapper.writeValueAsString(dto);
        assertFalse(jsonUnauthorized.contains("85000"), "Salary must not be exposed to unauthorized callers");
        assertTrue(jsonUnauthorized.contains("\"salary\":null"), "Salary should be null in JSON for unauthorized callers");
        assertTrue(jsonUnauthorized.contains("+* *******5432"), "Phone should be masked in JSON for unauthorized callers");

        // Under Admin context, Jackson exposes raw salary and phone
        List<SimpleGrantedAuthority> adminAuths = List.of(new SimpleGrantedAuthority("ROLE_ADMIN"));
        Authentication adminAuth = new UsernamePasswordAuthenticationToken(adminUser.getEmail(), null, adminAuths);
        SecurityContextHolder.getContext().setAuthentication(adminAuth);

        String jsonAdmin = objectMapper.writeValueAsString(dto);
        assertTrue(jsonAdmin.contains("85000"), "Salary must be exposed to ADMIN in JSON");
        assertTrue(jsonAdmin.contains("+1-555-876-5432"), "Full phone must be exposed to ADMIN in JSON");
    }
}
