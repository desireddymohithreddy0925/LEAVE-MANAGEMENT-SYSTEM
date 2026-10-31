package com.leave_management_system.leave_management_system.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.leave_management_system.leave_management_system.dto.DepartmentRequestDTO;
import com.leave_management_system.leave_management_system.dto.EmployeeRequestDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@WithMockUser(authorities = {
        "ROLE_ADMIN", "DEPARTMENT_MANAGE", "DEPARTMENT_VIEW",
        "EMPLOYEE_CREATE", "EMPLOYEE_VIEW", "EMPLOYEE_UPDATE", "EMPLOYEE_DEACTIVATE"
})
public class DepartmentControllerIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    private ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    public void setup() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(this.context)
                .apply(springSecurity())
                .build();
    }

    private Long createDepartmentHelper(String name) throws Exception {
        DepartmentRequestDTO dto = new DepartmentRequestDTO();
        dto.setName(name);

        MvcResult result = mockMvc.perform(post("/api/departments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andReturn();

        return JsonPath.parse(result.getResponse().getContentAsString()).read("$.id", Long.class);
    }

    private Long createEmployeeHelper(Long deptId, String email) throws Exception {
        EmployeeRequestDTO dto = new EmployeeRequestDTO();
        dto.setFirstName("Dept");
        dto.setLastName("Member");
        dto.setEmail(email);
        dto.setPhone("1234567890");
        dto.setDepartmentId(deptId);
        dto.setEmployeeCode("EMP-" + System.currentTimeMillis() + "-" + (int)(Math.random() * 1000));
        dto.setStatus("ACTIVE");
        dto.setSalary(new BigDecimal("60000"));

        MvcResult result = mockMvc.perform(post("/api/employees")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andReturn();

        return JsonPath.parse(result.getResponse().getContentAsString()).read("$.id", Long.class);
    }

    @Test
    void createDepartment_Success() throws Exception {
        DepartmentRequestDTO dto = new DepartmentRequestDTO();
        dto.setName("Integration IT " + System.currentTimeMillis());

        mockMvc.perform(post("/api/departments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value(dto.getName()));
    }

    @Test
    void createDepartment_DuplicateName() throws Exception {
        String deptName = "Integration HR " + System.currentTimeMillis();
        DepartmentRequestDTO dto = new DepartmentRequestDTO();
        dto.setName(deptName);

        // First insert
        mockMvc.perform(post("/api/departments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated());

        // Second insert - should fail with conflict
        mockMvc.perform(post("/api/departments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isConflict());
    }

    @Test
    void createDepartment_ValidationFails() throws Exception {
        DepartmentRequestDTO dto = new DepartmentRequestDTO();
        dto.setName(""); // Blank name violates @NotBlank / validation

        mockMvc.perform(post("/api/departments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getAllDepartments_Success() throws Exception {
        String deptName = "GetAll Dept " + System.currentTimeMillis();
        createDepartmentHelper(deptName);

        mockMvc.perform(get("/api/departments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$[*].name", hasItem(deptName)));
    }

    @Test
    void getDepartmentById_Success() throws Exception {
        String deptName = "FindById Dept " + System.currentTimeMillis();
        Long deptId = createDepartmentHelper(deptName);

        mockMvc.perform(get("/api/departments/" + deptId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(deptId))
                .andExpect(jsonPath("$.name").value(deptName));
    }

    @Test
    void getDepartmentById_NotFound() throws Exception {
        mockMvc.perform(get("/api/departments/999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateDepartment_Success() throws Exception {
        Long deptId = createDepartmentHelper("Dept Before Update " + System.currentTimeMillis());
        String updatedName = "Dept After Update " + System.currentTimeMillis();

        DepartmentRequestDTO updateDto = new DepartmentRequestDTO();
        updateDto.setName(updatedName);

        mockMvc.perform(put("/api/departments/" + deptId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(deptId))
                .andExpect(jsonPath("$.name").value(updatedName));
    }

    @Test
    void updateDepartment_Conflict() throws Exception {
        String name1 = "Dept Conflict 1 " + System.currentTimeMillis();
        String name2 = "Dept Conflict 2 " + System.currentTimeMillis();
        createDepartmentHelper(name1);
        Long deptId2 = createDepartmentHelper(name2);

        // Try renaming dept2 to name1
        DepartmentRequestDTO updateDto = new DepartmentRequestDTO();
        updateDto.setName(name1);

        mockMvc.perform(put("/api/departments/" + deptId2)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateDto)))
                .andExpect(status().isConflict());
    }

    @Test
    void updateDepartment_NotFound() throws Exception {
        DepartmentRequestDTO updateDto = new DepartmentRequestDTO();
        updateDto.setName("Non Existent Dept");

        mockMvc.perform(put("/api/departments/999999")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateDto)))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteDepartment_Success() throws Exception {
        Long deptId = createDepartmentHelper("Dept To Delete " + System.currentTimeMillis());

        mockMvc.perform(delete("/api/departments/" + deptId))
                .andExpect(status().isNoContent());

        // Verify it is gone
        mockMvc.perform(get("/api/departments/" + deptId))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteDepartment_NotFound() throws Exception {
        mockMvc.perform(delete("/api/departments/999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteDepartment_WithActiveEmployees_Conflict() throws Exception {
        Long deptId = createDepartmentHelper("Dept With Employees " + System.currentTimeMillis());
        createEmployeeHelper(deptId, "deptemp." + System.currentTimeMillis() + "@test.com");

        // Attempt deletion while employee assigned
        mockMvc.perform(delete("/api/departments/" + deptId))
                .andExpect(status().isConflict());
    }

    @Test
    void assignManager_Success() throws Exception {
        Long deptId = createDepartmentHelper("Dept Assign Mgr " + System.currentTimeMillis());
        Long empId = createEmployeeHelper(deptId, "deptmgr." + System.currentTimeMillis() + "@test.com");

        mockMvc.perform(patch("/api/departments/" + deptId + "/manager")
                .param("managerId", String.valueOf(empId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(deptId))
                .andExpect(jsonPath("$.managerId").value(empId));
    }

    @Test
    void assignManager_RemoveManager() throws Exception {
        Long deptId = createDepartmentHelper("Dept Remove Mgr " + System.currentTimeMillis());
        Long empId = createEmployeeHelper(deptId, "removemgr." + System.currentTimeMillis() + "@test.com");

        // First assign
        mockMvc.perform(patch("/api/departments/" + deptId + "/manager")
                .param("managerId", String.valueOf(empId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.managerId").value(empId));

        // Then remove manager by passing no managerId param
        mockMvc.perform(patch("/api/departments/" + deptId + "/manager"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.managerId").value(nullValue()));
    }

    @Test
    void assignManager_DepartmentNotFound() throws Exception {
        mockMvc.perform(patch("/api/departments/999999/manager")
                .param("managerId", "1"))
                .andExpect(status().isNotFound());
    }

    @Test
    void assignManager_ManagerNotFound() throws Exception {
        Long deptId = createDepartmentHelper("Dept Mgr NotFound " + System.currentTimeMillis());

        mockMvc.perform(patch("/api/departments/" + deptId + "/manager")
                .param("managerId", "999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getDepartmentEmployees_Success() throws Exception {
        Long deptId = createDepartmentHelper("Dept Employees List " + System.currentTimeMillis());
        String empEmail = "emp.list." + System.currentTimeMillis() + "@test.com";
        createEmployeeHelper(deptId, empEmail);

        mockMvc.perform(get("/api/departments/" + deptId + "/employees"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$[0].email").value(empEmail));
    }

    @Test
    void getDepartmentEmployees_NonExistentDepartment_ReturnsEmptyList() throws Exception {
        mockMvc.perform(get("/api/departments/999999/employees"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void getDepartmentHierarchy_Success() throws Exception {
        Long deptId = createDepartmentHelper("Dept Hierarchy " + System.currentTimeMillis());
        Long empId = createEmployeeHelper(deptId, "dept.hier." + System.currentTimeMillis() + "@test.com");

        // Assign manager
        mockMvc.perform(patch("/api/departments/" + deptId + "/manager")
                .param("managerId", String.valueOf(empId)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/departments/" + deptId + "/hierarchy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.departmentId").value(deptId))
                .andExpect(jsonPath("$.manager").isNotEmpty())
                .andExpect(jsonPath("$.employees", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test
    void getDepartmentHierarchy_NotFound() throws Exception {
        mockMvc.perform(get("/api/departments/999999/hierarchy"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getAllDepartmentHierarchies_Success() throws Exception {
        createDepartmentHelper("All Dept Hier " + System.currentTimeMillis());

        mockMvc.perform(get("/api/departments/hierarchy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test
    @WithMockUser(authorities = {"ROLE_USER"})
    void accessDenied_WhenMissingPermissions() throws Exception {
        DepartmentRequestDTO dto = new DepartmentRequestDTO();
        dto.setName("Unauthorized Dept " + System.currentTimeMillis());

        // Lacking DEPARTMENT_MANAGE -> 403 Forbidden
        mockMvc.perform(post("/api/departments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isForbidden());

        // Lacking DEPARTMENT_VIEW -> 403 Forbidden
        mockMvc.perform(get("/api/departments"))
                .andExpect(status().isForbidden());
    }
}
