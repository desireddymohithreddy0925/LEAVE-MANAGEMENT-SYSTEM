package com.leave_management_system.leave_management_system.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leave_management_system.leave_management_system.dto.*;
import com.leave_management_system.leave_management_system.entity.Employee;
import com.leave_management_system.leave_management_system.repository.EmployeeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class ProfileSecurityIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private EmployeeRepository employeeRepository;

    private ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private MockMvc mockMvc;

    @BeforeEach
    public void setup() {
        this.mockMvc = MockMvcBuilders
                .webAppContextSetup(this.context)
                .apply(springSecurity())
                .build();
    }

    private static class AuthAccount {
        String email;
        String password;
        String token;
        String refreshToken;
        Long employeeId;

        AuthAccount(String email, String password, String token, String refreshToken, Long employeeId) {
            this.email = email;
            this.password = password;
            this.token = token;
            this.refreshToken = refreshToken;
            this.employeeId = employeeId;
        }
    }

    private AuthAccount registerEmployee(String prefix, String password) throws Exception {
        String email = prefix + "_" + System.currentTimeMillis() + "@example.com";
        RegisterRequestDTO register = new RegisterRequestDTO();
        register.setFirstName("First" + prefix);
        register.setLastName("Last" + prefix);
        register.setEmail(email);
        register.setPassword(password);
        register.setPhone("+15550100");
        register.setEmployeeCode("CODE-" + prefix + "-" + System.nanoTime());

        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(register)))
                .andExpect(status().isOk())
                .andReturn();

        AuthResponseDTO authResponse = objectMapper.readValue(
                result.getResponse().getContentAsString(), AuthResponseDTO.class);

        Employee emp = employeeRepository.findByUserEmail(email).orElseThrow();
        return new AuthAccount(email, password, authResponse.getAccessToken(), authResponse.getRefreshToken(), emp.getId());
    }

    @Test
    void testGetOwnProfile_ViaMeEndpoint_Success() throws Exception {
        AuthAccount user = registerEmployee("getme", "Password123!");

        mockMvc.perform(get("/api/employees/me")
                        .header("Authorization", "Bearer " + user.token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.employeeId))
                .andExpect(jsonPath("$.email").value(user.email))
                .andExpect(jsonPath("$.firstName").value("Firstgetme"))
                .andExpect(jsonPath("$.lastName").value("Lastgetme"));
    }

    @Test
    void testGetOwnProfile_ViaIdEndpoint_Success() throws Exception {
        AuthAccount user = registerEmployee("getid", "Password123!");

        mockMvc.perform(get("/api/employees/" + user.employeeId)
                        .header("Authorization", "Bearer " + user.token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.employeeId))
                .andExpect(jsonPath("$.email").value(user.email));
    }

    @Test
    void testGetProfile_Unauthenticated_ReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/employees/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testUpdateOwnProfile_ViaMeEndpoint_Success() throws Exception {
        AuthAccount user = registerEmployee("updateme", "Password123!");

        EmployeeProfileUpdateDTO updateDTO = new EmployeeProfileUpdateDTO();
        updateDTO.setFirstName("UpdatedFirst");
        updateDTO.setLastName("UpdatedLast");
        updateDTO.setPhone("+1 555-0999");

        mockMvc.perform(put("/api/employees/me/profile")
                        .header("Authorization", "Bearer " + user.token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.employeeId))
                .andExpect(jsonPath("$.firstName").value("UpdatedFirst"))
                .andExpect(jsonPath("$.lastName").value("UpdatedLast"))
                .andExpect(jsonPath("$.phone").value("+1 555-0999"))
                .andExpect(jsonPath("$.email").value(user.email));
    }

    @Test
    void testUpdateOwnProfile_ViaIdEndpoint_Success() throws Exception {
        AuthAccount user = registerEmployee("updateid", "Password123!");

        EmployeeProfileUpdateDTO updateDTO = new EmployeeProfileUpdateDTO();
        updateDTO.setFirstName("Alice");
        updateDTO.setLastName("Wonderland");
        updateDTO.setPhone("+1 555-0888");

        mockMvc.perform(put("/api/employees/" + user.employeeId + "/profile")
                        .header("Authorization", "Bearer " + user.token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.employeeId))
                .andExpect(jsonPath("$.firstName").value("Alice"))
                .andExpect(jsonPath("$.lastName").value("Wonderland"))
                .andExpect(jsonPath("$.phone").value("+1 555-0888"));
    }

    @Test
    void testPreventCrossEmployee_ProfileViewing_Forbidden() throws Exception {
        AuthAccount userA = registerEmployee("empA", "Password123!");
        AuthAccount userB = registerEmployee("empB", "Password123!");

        // User A tries to view User B's profile
        mockMvc.perform(get("/api/employees/" + userB.employeeId)
                        .header("Authorization", "Bearer " + userA.token))
                .andExpect(status().isForbidden());
    }

    @Test
    void testPreventCrossEmployee_ProfileTampering_Forbidden() throws Exception {
        AuthAccount attacker = registerEmployee("attacker", "Password123!");
        AuthAccount victim = registerEmployee("victim", "Password123!");

        EmployeeProfileUpdateDTO maliciousUpdate = new EmployeeProfileUpdateDTO();
        maliciousUpdate.setFirstName("Hacked");
        maliciousUpdate.setLastName("Target");
        maliciousUpdate.setPhone("+1 000-0000");

        // Attacker attempts to update victim's profile
        mockMvc.perform(put("/api/employees/" + victim.employeeId + "/profile")
                        .header("Authorization", "Bearer " + attacker.token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(maliciousUpdate)))
                .andExpect(status().isForbidden());
    }

    @Test
    void testPreventEmployee_AdministrativeUpdate_Forbidden() throws Exception {
        AuthAccount emp = registerEmployee("employee1", "Password123!");

        // Employee tries to use the admin PUT /api/employees/{id} to raise their salary
        EmployeeRequestDTO adminDTO = new EmployeeRequestDTO();
        adminDTO.setFirstName("Employee");
        adminDTO.setLastName("One");
        adminDTO.setEmail(emp.email);
        adminDTO.setPhone("+1 555-0100");
        adminDTO.setEmployeeCode("CODE-ADMIN-TEST");
        adminDTO.setDepartmentId(1L);
        adminDTO.setStatus("ACTIVE");
        adminDTO.setSalary(new BigDecimal("99999999"));

        mockMvc.perform(put("/api/employees/" + emp.employeeId)
                        .header("Authorization", "Bearer " + emp.token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(adminDTO)))
                .andExpect(status().isForbidden());
    }

    @Test
    void testPreventEmployee_RoleChange_Forbidden() throws Exception {
        AuthAccount emp = registerEmployee("employee2", "Password123!");

        mockMvc.perform(patch("/api/employees/" + emp.employeeId + "/role")
                        .header("Authorization", "Bearer " + emp.token)
                        .param("roleName", "ADMIN"))
                .andExpect(status().isForbidden());
    }

    @Test
    void testPreventEmployee_StatusChange_Forbidden() throws Exception {
        AuthAccount emp = registerEmployee("employee3", "Password123!");

        mockMvc.perform(patch("/api/employees/" + emp.employeeId + "/status")
                        .header("Authorization", "Bearer " + emp.token)
                        .param("status", "INACTIVE"))
                .andExpect(status().isForbidden());
    }

    @Test
    void testPreventEmployee_ManagerAssignment_Forbidden() throws Exception {
        AuthAccount emp = registerEmployee("employee4", "Password123!");

        mockMvc.perform(patch("/api/employees/" + emp.employeeId + "/manager")
                        .header("Authorization", "Bearer " + emp.token)
                        .param("managerId", "1"))
                .andExpect(status().isForbidden());
    }

    @Test
    void testDeactivatedEmployee_CannotUpdateProfile_Conflict() throws Exception {
        AuthAccount emp = registerEmployee("deactivated", "Password123!");

        // Deactivate employee in repository
        Employee employee = employeeRepository.findById(emp.employeeId).orElseThrow();
        employee.setStatus("INACTIVE");
        employeeRepository.save(employee);

        EmployeeProfileUpdateDTO updateDTO = new EmployeeProfileUpdateDTO();
        updateDTO.setFirstName("ShouldFail");
        updateDTO.setLastName("Deactivated");
        updateDTO.setPhone("+1 555-0000");

        // Attempting via /me/profile
        mockMvc.perform(put("/api/employees/me/profile")
                        .header("Authorization", "Bearer " + emp.token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDTO)))
                .andExpect(status().isConflict());

        // Attempting via /{id}/profile
        mockMvc.perform(put("/api/employees/" + emp.employeeId + "/profile")
                        .header("Authorization", "Bearer " + emp.token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDTO)))
                .andExpect(status().isConflict());
    }

    @Test
    void testChangePassword_Success_AndCanLoginWithNewPassword() throws Exception {
        AuthAccount emp = registerEmployee("pwchange", "OldPassword123!");

        PasswordChangeRequestDTO changeDTO = new PasswordChangeRequestDTO("OldPassword123!", "NewPassword123!");

        // Call /api/employees/me/change-password
        mockMvc.perform(post("/api/employees/me/change-password")
                        .header("Authorization", "Bearer " + emp.token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(changeDTO)))
                .andExpect(status().isOk());

        // Attempt login with old password - must fail
        AuthRequestDTO oldLogin = new AuthRequestDTO(emp.email, "OldPassword123!");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(oldLogin)))
                .andExpect(status().isUnauthorized());

        // Attempt login with new password - must succeed
        AuthRequestDTO newLogin = new AuthRequestDTO(emp.email, "NewPassword123!");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newLogin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());

        // Refresh token from before password change must be revoked
        TokenRefreshRequestDTO refreshReq = new TokenRefreshRequestDTO();
        refreshReq.setRefreshToken(emp.refreshToken);
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshReq)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testChangePassword_ViaAuthEndpoint_Success() throws Exception {
        AuthAccount emp = registerEmployee("authpwchange", "OldPassword123!");

        PasswordChangeRequestDTO changeDTO = new PasswordChangeRequestDTO("OldPassword123!", "BrandNewPassword123!");

        mockMvc.perform(post("/api/auth/change-password")
                        .header("Authorization", "Bearer " + emp.token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(changeDTO)))
                .andExpect(status().isOk());

        // Verify login with new password works
        AuthRequestDTO newLogin = new AuthRequestDTO(emp.email, "BrandNewPassword123!");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newLogin)))
                .andExpect(status().isOk());
    }

    @Test
    void testChangePassword_ViaMePasswordPut_Success() throws Exception {
        AuthAccount emp = registerEmployee("putpwchange", "OldPassword123!");

        PasswordChangeRequestDTO changeDTO = new PasswordChangeRequestDTO("OldPassword123!", "BrandNewPassword123!");

        mockMvc.perform(put("/api/employees/me/password")
                        .header("Authorization", "Bearer " + emp.token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(changeDTO)))
                .andExpect(status().isOk());
    }

    @Test
    void testChangePassword_WrongCurrentPassword_BadRequest() throws Exception {
        AuthAccount emp = registerEmployee("wrongpw", "CorrectPassword123!");

        PasswordChangeRequestDTO changeDTO = new PasswordChangeRequestDTO("WrongPassword999!", "BrandNewPassword123!");

        mockMvc.perform(post("/api/employees/me/change-password")
                        .header("Authorization", "Bearer " + emp.token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(changeDTO)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Current password is incorrect"));
    }

    @Test
    void testChangePassword_SamePassword_BadRequest() throws Exception {
        AuthAccount emp = registerEmployee("samepw", "MyPassword123!");

        PasswordChangeRequestDTO changeDTO = new PasswordChangeRequestDTO("MyPassword123!", "MyPassword123!");

        mockMvc.perform(post("/api/employees/me/change-password")
                        .header("Authorization", "Bearer " + emp.token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(changeDTO)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("New password cannot be the same as current password"));
    }

    @Test
    void testChangePassword_TooShortNewPassword_BadRequest() throws Exception {
        AuthAccount emp = registerEmployee("shortpw", "MyPassword123!");

        PasswordChangeRequestDTO changeDTO = new PasswordChangeRequestDTO("MyPassword123!", "short");

        mockMvc.perform(post("/api/employees/me/change-password")
                        .header("Authorization", "Bearer " + emp.token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(changeDTO)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testChangePassword_Unauthenticated_Unauthorized() throws Exception {
        PasswordChangeRequestDTO changeDTO = new PasswordChangeRequestDTO("AnyPassword123!", "BrandNewPassword123!");

        mockMvc.perform(post("/api/employees/me/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(changeDTO)))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/auth/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(changeDTO)))
                .andExpect(status().isUnauthorized());
    }
}
