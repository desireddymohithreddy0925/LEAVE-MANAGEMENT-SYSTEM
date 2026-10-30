package com.leave_management_system.leave_management_system.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.leave_management_system.leave_management_system.dto.EmployeeHierarchyDTO;
import com.leave_management_system.leave_management_system.dto.EmployeeProfileUpdateDTO;
import com.leave_management_system.leave_management_system.dto.EmployeeRequestDTO;
import com.leave_management_system.leave_management_system.dto.EmployeeResponseDTO;
import com.leave_management_system.leave_management_system.dto.PasswordChangeRequestDTO;
import com.leave_management_system.leave_management_system.exception.GlobalExceptionHandler;
import com.leave_management_system.leave_management_system.exception.ResourceNotFoundException;
import com.leave_management_system.leave_management_system.service.AuthService;
import com.leave_management_system.leave_management_system.service.EmployeeService;
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
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class EmployeeControllerTest {

    @Mock
    private EmployeeService employeeService;

    @Mock
    private AuthService authService;

    @InjectMocks
    private EmployeeController employeeController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        MappingJackson2HttpMessageConverter jacksonConverter = new MappingJackson2HttpMessageConverter();
        jacksonConverter.setObjectMapper(objectMapper);

        mockMvc = MockMvcBuilders.standaloneSetup(employeeController)
                .setMessageConverters(new StringHttpMessageConverter(), jacksonConverter)
                .setControllerAdvice(new GlobalExceptionHandler(null))
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();
    }

    private EmployeeRequestDTO createValidEmployeeRequestDTO() {
        EmployeeRequestDTO dto = new EmployeeRequestDTO();
        dto.setFirstName("John");
        dto.setLastName("Doe");
        dto.setEmail("john.doe@example.com");
        dto.setPhone("+1234567890");
        dto.setEmployeeCode("EMP001");
        dto.setStatus("ACTIVE");
        dto.setSalary(new BigDecimal("75000"));
        dto.setDepartmentId(1L);
        return dto;
    }

    @Test
    @DisplayName("POST /api/employees - Success returns 201 Created")
    void createEmployee_Success() throws Exception {
        EmployeeRequestDTO request = createValidEmployeeRequestDTO();
        EmployeeResponseDTO response = new EmployeeResponseDTO();
        response.setId(1L);
        response.setFirstName("John");
        response.setLastName("Doe");
        response.setEmail("john.doe@example.com");

        when(employeeService.createEmployee(any(EmployeeRequestDTO.class))).thenReturn(response);

        mockMvc.perform(post("/api/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.firstName").value("John"));

        verify(employeeService).createEmployee(any(EmployeeRequestDTO.class));
    }

    @Test
    @DisplayName("POST /api/employees - Validation error returns 400 Bad Request")
    void createEmployee_ValidationFailure() throws Exception {
        EmployeeRequestDTO request = new EmployeeRequestDTO(); // missing fields

        mockMvc.perform(post("/api/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Validation Failed"));

        verifyNoInteractions(employeeService);
    }

    @Test
    @DisplayName("GET /api/employees - Returns paginated list of employees")
    void getAllEmployees_Success() throws Exception {
        EmployeeResponseDTO e1 = new EmployeeResponseDTO();
        e1.setId(1L);
        e1.setFirstName("John");

        Page<EmployeeResponseDTO> page = new PageImpl<>(List.of(e1), PageRequest.of(0, 10), 1);
        when(employeeService.getAllEmployees(any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/employees")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].firstName").value("John"));

        verify(employeeService).getAllEmployees(any(Pageable.class));
    }

    @Test
    @DisplayName("GET /api/employees with search - Searches employees")
    void getAllEmployees_Search() throws Exception {
        EmployeeResponseDTO e1 = new EmployeeResponseDTO();
        e1.setId(1L);
        e1.setFirstName("John");

        Page<EmployeeResponseDTO> page = new PageImpl<>(List.of(e1), PageRequest.of(0, 10), 1);
        when(employeeService.searchEmployees(eq("John"), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/employees")
                        .param("search", "John")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].firstName").value("John"));

        verify(employeeService).searchEmployees(eq("John"), any(Pageable.class));
    }

    @Test
    @DisplayName("GET /api/employees/me - Returns current employee profile")
    void getMyProfile_Success() throws Exception {
        EmployeeResponseDTO profile = new EmployeeResponseDTO();
        profile.setId(1L);
        profile.setFirstName("John");
        UsernamePasswordAuthenticationToken principal = new UsernamePasswordAuthenticationToken("john.doe@example.com", null, Collections.emptyList());

        when(employeeService.getMyProfile(any(Authentication.class))).thenReturn(profile);

        mockMvc.perform(get("/api/employees/me").principal(principal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.firstName").value("John"));

        verify(employeeService).getMyProfile(any(Authentication.class));
    }

    @Test
    @DisplayName("PUT /api/employees/me/profile - Updates current employee profile")
    void updateMyProfile_Success() throws Exception {
        EmployeeProfileUpdateDTO updateDTO = new EmployeeProfileUpdateDTO();
        updateDTO.setFirstName("Johnny");
        updateDTO.setLastName("Doe");
        updateDTO.setPhone("+9876543210");

        EmployeeResponseDTO response = new EmployeeResponseDTO();
        response.setId(1L);
        response.setFirstName("Johnny");
        UsernamePasswordAuthenticationToken principal = new UsernamePasswordAuthenticationToken("john.doe@example.com", null, Collections.emptyList());

        when(employeeService.updateMyProfile(any(Authentication.class), any(EmployeeProfileUpdateDTO.class))).thenReturn(response);

        mockMvc.perform(put("/api/employees/me/profile")
                        .principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Johnny"));

        verify(employeeService).updateMyProfile(any(Authentication.class), any(EmployeeProfileUpdateDTO.class));
    }

    @Test
    @DisplayName("PUT /api/employees/me/password - Updates current user password")
    void changePasswordPut_Success() throws Exception {
        PasswordChangeRequestDTO request = new PasswordChangeRequestDTO("OldPass123!", "NewPass123!");
        UsernamePasswordAuthenticationToken principal = new UsernamePasswordAuthenticationToken("john.doe@example.com", null, Collections.emptyList());

        mockMvc.perform(put("/api/employees/me/password")
                        .principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().string("Password changed successfully"));

        verify(authService).changePassword(eq("john.doe@example.com"), any(PasswordChangeRequestDTO.class));
    }

    @Test
    @DisplayName("GET /api/employees/me/hierarchy - Returns current employee hierarchy")
    void getMyHierarchy_Success() throws Exception {
        EmployeeHierarchyDTO hierarchy = new EmployeeHierarchyDTO();
        UsernamePasswordAuthenticationToken principal = new UsernamePasswordAuthenticationToken("john.doe@example.com", null, Collections.emptyList());

        when(employeeService.getMyHierarchy(any(Authentication.class))).thenReturn(hierarchy);

        mockMvc.perform(get("/api/employees/me/hierarchy").principal(principal))
                .andExpect(status().isOk());

        verify(employeeService).getMyHierarchy(any(Authentication.class));
    }

    @Test
    @DisplayName("GET /api/employees/{id} - Success returns employee")
    void getEmployeeById_Success() throws Exception {
        EmployeeResponseDTO response = new EmployeeResponseDTO();
        response.setId(1L);
        response.setFirstName("John");

        when(employeeService.getEmployeeById(1L)).thenReturn(response);

        mockMvc.perform(get("/api/employees/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));

        verify(employeeService).getEmployeeById(1L);
    }

    @Test
    @DisplayName("GET /api/employees/{id} - Not found returns 404")
    void getEmployeeById_NotFound() throws Exception {
        when(employeeService.getEmployeeById(99L)).thenThrow(new ResourceNotFoundException("Employee not found with id: 99"));

        mockMvc.perform(get("/api/employees/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));

        verify(employeeService).getEmployeeById(99L);
    }

    @Test
    @DisplayName("PUT /api/employees/{id} - Updates employee")
    void updateEmployee_Success() throws Exception {
        EmployeeRequestDTO request = createValidEmployeeRequestDTO();
        EmployeeResponseDTO response = new EmployeeResponseDTO();
        response.setId(1L);
        response.setFirstName("John");

        when(employeeService.updateEmployee(eq(1L), any(EmployeeRequestDTO.class))).thenReturn(response);

        mockMvc.perform(put("/api/employees/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));

        verify(employeeService).updateEmployee(eq(1L), any(EmployeeRequestDTO.class));
    }

    @Test
    @DisplayName("PATCH /api/employees/{id}/status - Updates employee status")
    void changeStatus_Success() throws Exception {
        EmployeeResponseDTO response = new EmployeeResponseDTO();
        response.setId(1L);
        response.setStatus("INACTIVE");

        when(employeeService.changeEmployeeStatus(1L, "INACTIVE")).thenReturn(response);

        mockMvc.perform(patch("/api/employees/1/status").param("status", "INACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));

        verify(employeeService).changeEmployeeStatus(1L, "INACTIVE");
    }

    @Test
    @DisplayName("PUT /api/employees/{id}/department - Transfers employee to department")
    void transferEmployee_Success() throws Exception {
        EmployeeResponseDTO response = new EmployeeResponseDTO();
        response.setId(1L);

        when(employeeService.transferEmployee(1L, 2L)).thenReturn(response);

        mockMvc.perform(put("/api/employees/1/department").param("departmentId", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));

        verify(employeeService).transferEmployee(1L, 2L);
    }

    @Test
    @DisplayName("DELETE /api/employees/{id} - Deletes employee")
    void deleteEmployee_Success() throws Exception {
        doNothing().when(employeeService).deleteEmployee(1L);

        mockMvc.perform(delete("/api/employees/1"))
                .andExpect(status().isOk());

        verify(employeeService).deleteEmployee(1L);
    }

    @Test
    @DisplayName("PUT /api/employees/{id}/profile - Updates employee profile by id")
    void updateProfile_Success() throws Exception {
        EmployeeProfileUpdateDTO updateDTO = new EmployeeProfileUpdateDTO();
        updateDTO.setFirstName("Jack");
        updateDTO.setLastName("Doe");

        EmployeeResponseDTO response = new EmployeeResponseDTO();
        response.setId(1L);
        response.setFirstName("Jack");

        when(employeeService.updateProfile(eq(1L), any(EmployeeProfileUpdateDTO.class))).thenReturn(response);

        mockMvc.perform(put("/api/employees/1/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Jack"));

        verify(employeeService).updateProfile(eq(1L), any(EmployeeProfileUpdateDTO.class));
    }

    @Test
    @DisplayName("PATCH /api/employees/{id}/manager - Assigns manager to employee")
    void assignManager_Success() throws Exception {
        EmployeeResponseDTO response = new EmployeeResponseDTO();
        response.setId(1L);
        response.setManagerId(5L);

        when(employeeService.assignManager(1L, 5L)).thenReturn(response);

        mockMvc.perform(patch("/api/employees/1/manager").param("managerId", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.managerId").value(5));

        verify(employeeService).assignManager(1L, 5L);
    }

    @Test
    @DisplayName("PATCH /api/employees/{id}/role - Changes role of employee")
    void changeRole_Success() throws Exception {
        EmployeeResponseDTO response = new EmployeeResponseDTO();
        response.setId(1L);

        when(employeeService.changeRole(1L, "ROLE_MANAGER")).thenReturn(response);

        mockMvc.perform(patch("/api/employees/1/role").param("roleName", "ROLE_MANAGER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));

        verify(employeeService).changeRole(1L, "ROLE_MANAGER");
    }

    @Test
    @DisplayName("GET /api/employees/{id}/hierarchy - Returns employee hierarchy by id")
    void getEmployeeHierarchy_Success() throws Exception {
        EmployeeHierarchyDTO hierarchy = new EmployeeHierarchyDTO();

        when(employeeService.getEmployeeHierarchy(1L)).thenReturn(hierarchy);

        mockMvc.perform(get("/api/employees/1/hierarchy"))
                .andExpect(status().isOk());

        verify(employeeService).getEmployeeHierarchy(1L);
    }
}

