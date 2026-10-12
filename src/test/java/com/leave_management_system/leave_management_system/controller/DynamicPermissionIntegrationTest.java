package com.leave_management_system.leave_management_system.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.jayway.jsonpath.JsonPath;
import com.leave_management_system.leave_management_system.dto.RegisterRequestDTO;
import com.leave_management_system.leave_management_system.entity.Permission;
import com.leave_management_system.leave_management_system.entity.Role;
import com.leave_management_system.leave_management_system.repository.PermissionRepository;
import com.leave_management_system.leave_management_system.repository.RoleRepository;
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
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.stream.Collectors;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class DynamicPermissionIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PermissionRepository permissionRepository;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @BeforeEach
    public void setup() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    private String getAuthToken(String email, String password) throws Exception {
        String authPayload = String.format("{\"email\":\"%s\",\"password\":\"%s\"}", email, password);
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(authPayload))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.parse(result.getResponse().getContentAsString()).read("$.accessToken", String.class);
    }

    private Long createDepartment(String adminToken) throws Exception {
        com.leave_management_system.leave_management_system.dto.DepartmentRequestDTO dto = new com.leave_management_system.leave_management_system.dto.DepartmentRequestDTO();
        dto.setName("Dynamic Dept " + System.currentTimeMillis());
        MvcResult result = mockMvc.perform(post("/api/departments")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andReturn();
        return JsonPath.parse(result.getResponse().getContentAsString()).read("$.id", Long.class);
    }

    private Long createEmployee(String email, String role, Long deptId, String adminToken) throws Exception {
        RegisterRequestDTO register = new RegisterRequestDTO();
        register.setFirstName("Test");
        register.setLastName(role);
        register.setEmail(email);
        register.setPassword("password123");
        register.setEmployeeCode("REG-" + System.currentTimeMillis() + "-" + role);
        register.setPhone("1234567890");
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(register)))
                .andExpect(status().isOk());
                
        com.leave_management_system.leave_management_system.entity.Employee employee = 
            org.springframework.web.context.support.WebApplicationContextUtils
            .getRequiredWebApplicationContext(context.getServletContext())
            .getBean(com.leave_management_system.leave_management_system.repository.EmployeeRepository.class)
            .findByUserEmail(email).get();
            
        com.leave_management_system.leave_management_system.entity.Department dept = 
            org.springframework.web.context.support.WebApplicationContextUtils
            .getRequiredWebApplicationContext(context.getServletContext())
            .getBean(com.leave_management_system.leave_management_system.repository.DepartmentRepository.class)
            .findById(deptId).get();
            
        employee.setDepartment(dept);
        org.springframework.web.context.support.WebApplicationContextUtils
            .getRequiredWebApplicationContext(context.getServletContext())
            .getBean(com.leave_management_system.leave_management_system.repository.EmployeeRepository.class)
            .save(employee);
            
        return employee.getId();
    }

    @Test
    public void testDynamicPermissionLifecycle() throws Exception {
        // 1. Setup Admin and Manager
        // Admin user is created by migrations by default? Wait, migrations only create roles.
        // We need to create an Admin user manually.
        RegisterRequestDTO adminReg = new RegisterRequestDTO();
        adminReg.setFirstName("Admin");
        adminReg.setLastName("User");
        adminReg.setEmail("dyn.admin@test.com");
        adminReg.setPassword("password123");
        adminReg.setEmployeeCode("ADM-" + System.currentTimeMillis());
        adminReg.setPhone("9999999999");
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(adminReg)))
                .andExpect(status().isOk());
                
        // Elevate Admin user in DB manually since no endpoint for this exists in tests
        // But wait, how do we make them an Admin? We must update their role in DB.
        com.leave_management_system.leave_management_system.entity.User adminDbUser = 
            org.springframework.web.context.support.WebApplicationContextUtils
            .getRequiredWebApplicationContext(context.getServletContext())
            .getBean(com.leave_management_system.leave_management_system.repository.UserRepository.class)
            .findByEmail("dyn.admin@test.com").get();
        Role adminRole = roleRepository.findByName("ADMIN").get();
        adminDbUser.getRoles().clear();
        adminDbUser.getRoles().add(adminRole);
        org.springframework.web.context.support.WebApplicationContextUtils
            .getRequiredWebApplicationContext(context.getServletContext())
            .getBean(com.leave_management_system.leave_management_system.repository.UserRepository.class)
            .save(adminDbUser);

        String adminToken = getAuthToken("dyn.admin@test.com", "password123");

        // Create department
        Long deptId = createDepartment(adminToken);

        // Create Manager Employee
        Long managerEmpId = createEmployee("dyn.manager@test.com", "MANAGER", deptId, adminToken);
        
        // Elevate manager to MANAGER role in DB
        com.leave_management_system.leave_management_system.entity.User managerDbUser = 
            org.springframework.web.context.support.WebApplicationContextUtils
            .getRequiredWebApplicationContext(context.getServletContext())
            .getBean(com.leave_management_system.leave_management_system.repository.UserRepository.class)
            .findByEmail("dyn.manager@test.com").get();
        Role managerRoleEntity = roleRepository.findByName("MANAGER").get();
        managerDbUser.getRoles().clear();
        managerDbUser.getRoles().add(managerRoleEntity);
        org.springframework.web.context.support.WebApplicationContextUtils
            .getRequiredWebApplicationContext(context.getServletContext())
            .getBean(com.leave_management_system.leave_management_system.repository.UserRepository.class)
            .save(managerDbUser);
            
        // Create Subordinate Employee
        Long subEmpId = createEmployee("dyn.emp@test.com", "EMPLOYEE", deptId, adminToken);
        
        // Set department manager manually in DB
        com.leave_management_system.leave_management_system.entity.Department deptEntity = 
            org.springframework.web.context.support.WebApplicationContextUtils
            .getRequiredWebApplicationContext(context.getServletContext())
            .getBean(com.leave_management_system.leave_management_system.repository.DepartmentRepository.class)
            .findById(deptId).get();
        com.leave_management_system.leave_management_system.entity.Employee mgrEmp = 
            org.springframework.web.context.support.WebApplicationContextUtils
            .getRequiredWebApplicationContext(context.getServletContext())
            .getBean(com.leave_management_system.leave_management_system.repository.EmployeeRepository.class)
            .findById(managerEmpId).get();
        deptEntity.setManager(mgrEmp);
        org.springframework.web.context.support.WebApplicationContextUtils
            .getRequiredWebApplicationContext(context.getServletContext())
            .getBean(com.leave_management_system.leave_management_system.repository.DepartmentRepository.class)
            .save(deptEntity);
        
        String empToken = getAuthToken("dyn.emp@test.com", "password123");
        
        // Set up Leave Type and Balance
        com.leave_management_system.leave_management_system.dto.LeaveTypeRequestDTO typeDto = new com.leave_management_system.leave_management_system.dto.LeaveTypeRequestDTO();
        typeDto.setName("Dynamic Leave");
        typeDto.setDescription("Test");
        typeDto.setDefaultDays(12);
        MvcResult typeResult = mockMvc.perform(post("/api/leave-types")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(typeDto)))
                .andReturn();
        Long leaveTypeId = JsonPath.parse(typeResult.getResponse().getContentAsString()).read("$.id", Long.class);
        
        com.leave_management_system.leave_management_system.dto.LeaveBalanceRequestDTO balDto = new com.leave_management_system.leave_management_system.dto.LeaveBalanceRequestDTO();
        balDto.setEmployeeId(subEmpId);
        balDto.setLeaveTypeId(leaveTypeId);
        balDto.setAvailable(20);
        mockMvc.perform(post("/api/leave-balances")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(balDto)));
                
        // Employee creates leave requests on guaranteed weekdays
        java.time.LocalDate baseMonday = java.time.LocalDate.now().plusWeeks(1).with(java.time.DayOfWeek.MONDAY);

        com.leave_management_system.leave_management_system.dto.LeaveRequestDTO lr1 = new com.leave_management_system.leave_management_system.dto.LeaveRequestDTO();
        lr1.setEmployeeId(subEmpId);
        lr1.setLeaveTypeId(leaveTypeId);
        lr1.setStartDate(baseMonday);
        lr1.setEndDate(baseMonday.plusDays(1));
        lr1.setReason("Test 1");
        MvcResult lr1Result = mockMvc.perform(post("/api/leave-requests")
                .header("Authorization", "Bearer " + empToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(lr1)))
                .andExpect(status().isCreated())
                .andReturn();
        Long leaveReq1 = JsonPath.parse(lr1Result.getResponse().getContentAsString()).read("$.id", Long.class);
        
        com.leave_management_system.leave_management_system.dto.LeaveRequestDTO lr2 = new com.leave_management_system.leave_management_system.dto.LeaveRequestDTO();
        lr2.setEmployeeId(subEmpId);
        lr2.setLeaveTypeId(leaveTypeId);
        lr2.setStartDate(baseMonday.plusDays(2));
        lr2.setEndDate(baseMonday.plusDays(3));
        lr2.setReason("Test 2");
        MvcResult lr2Result = mockMvc.perform(post("/api/leave-requests")
                .header("Authorization", "Bearer " + empToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(lr2)))
                .andExpect(status().isCreated())
                .andReturn();
        Long leaveReq2 = JsonPath.parse(lr2Result.getResponse().getContentAsString()).read("$.id", Long.class);

        // 2. Admin removes LEAVE_APPROVE from MANAGER role
        Role managerRole = roleRepository.findByName("MANAGER").get();
        Permission approvePermission = permissionRepository.findByName("LEAVE_APPROVE").get();
        Permission rejectPermission = permissionRepository.findByName("LEAVE_REJECT").get();
        
        List<Long> newPermissions = managerRole.getPermissions().stream()
            .map(Permission::getId)
            .filter(id -> !id.equals(approvePermission.getId()))
            .collect(Collectors.toList());
            
        mockMvc.perform(put("/api/roles/" + managerRole.getId() + "/permissions")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(newPermissions)))
                .andExpect(status().isOk());
                
        // 3. Manager tries to approve and gets 403
        String managerToken = getAuthToken("dyn.manager@test.com", "password123");
        
        mockMvc.perform(put("/api/leave-requests/" + leaveReq1 + "/approve")
                .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isForbidden());
                
        // 4. Manager tries to reject and gets 200 (Still has LEAVE_REJECT)
        mockMvc.perform(put("/api/leave-requests/" + leaveReq1 + "/reject")
                .header("Authorization", "Bearer " + managerToken)
                .param("reason", "Because I can"))
                .andExpect(status().isOk());
                
        // 5. Admin restores LEAVE_APPROVE to MANAGER role
        mockMvc.perform(post("/api/roles/" + managerRole.getId() + "/permissions/" + approvePermission.getId())
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
                
        // 6. Manager gets new token and approves second request
        String newManagerToken = getAuthToken("dyn.manager@test.com", "password123");
        
        mockMvc.perform(put("/api/leave-requests/" + leaveReq2 + "/approve")
                .header("Authorization", "Bearer " + newManagerToken))
                .andExpect(status().isOk());
                
        // 7. Test inactive permissions deny access
        // Admin sets LEAVE_REJECT permission to inactive
        Permission rejectPermToDeactivate = permissionRepository.findByName("LEAVE_REJECT").get();
        rejectPermToDeactivate.setActive(false);
        permissionRepository.save(rejectPermToDeactivate);
        
        // Manager logs in again (token refresh happens with new claims)
        String inactiveToken = getAuthToken("dyn.manager@test.com", "password123");
        
        // Manager tries to reject another request, gets 403
        mockMvc.perform(put("/api/leave-requests/" + leaveReq2 + "/reject")
                .header("Authorization", "Bearer " + inactiveToken)
                .param("reason", "Denied because inactive"))
                .andExpect(status().isForbidden());
                
        // Cleanup
        rejectPermToDeactivate.setActive(true);
        permissionRepository.save(rejectPermToDeactivate);

        // 8. Test removing LEAVE_REJECT while keeping LEAVE_APPROVE (Bidirectional separation)
        com.leave_management_system.leave_management_system.dto.LeaveRequestDTO lr3 = new com.leave_management_system.leave_management_system.dto.LeaveRequestDTO();
        lr3.setEmployeeId(subEmpId);
        lr3.setLeaveTypeId(leaveTypeId);
        lr3.setStartDate(baseMonday.plusDays(7));
        lr3.setEndDate(baseMonday.plusDays(8));
        lr3.setReason("Test 3");
        MvcResult lr3Result = mockMvc.perform(post("/api/leave-requests")
                .header("Authorization", "Bearer " + empToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(lr3)))
                .andExpect(status().isCreated())
                .andReturn();
        Long leaveReq3 = JsonPath.parse(lr3Result.getResponse().getContentAsString()).read("$.id", Long.class);

        // Admin removes LEAVE_REJECT from MANAGER role via DELETE
        mockMvc.perform(delete("/api/roles/" + managerRole.getId() + "/permissions/" + rejectPermission.getId())
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        String managerNoRejectToken = getAuthToken("dyn.manager@test.com", "password123");

        // Rejection must be forbidden
        mockMvc.perform(put("/api/leave-requests/" + leaveReq3 + "/reject")
                .header("Authorization", "Bearer " + managerNoRejectToken)
                .param("reason", "Should fail due to missing LEAVE_REJECT"))
                .andExpect(status().isForbidden());

        // Approval must remain permitted (LEAVE_APPROVE is still present)
        mockMvc.perform(put("/api/leave-requests/" + leaveReq3 + "/approve")
                .header("Authorization", "Bearer " + managerNoRejectToken))
                .andExpect(status().isOk());

        // Restore LEAVE_REJECT to MANAGER role
        mockMvc.perform(post("/api/roles/" + managerRole.getId() + "/permissions/" + rejectPermission.getId())
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }
}
