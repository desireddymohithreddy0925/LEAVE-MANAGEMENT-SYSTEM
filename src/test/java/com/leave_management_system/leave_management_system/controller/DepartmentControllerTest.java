package com.leave_management_system.leave_management_system.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.leave_management_system.leave_management_system.dto.DepartmentHierarchyDTO;
import com.leave_management_system.leave_management_system.dto.DepartmentRequestDTO;
import com.leave_management_system.leave_management_system.dto.DepartmentResponseDTO;
import com.leave_management_system.leave_management_system.dto.EmployeeResponseDTO;
import com.leave_management_system.leave_management_system.exception.DuplicateResourceException;
import com.leave_management_system.leave_management_system.exception.GlobalExceptionHandler;
import com.leave_management_system.leave_management_system.exception.ResourceNotFoundException;
import com.leave_management_system.leave_management_system.service.DepartmentService;
import com.leave_management_system.leave_management_system.service.EmployeeService;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class DepartmentControllerTest {

    @Mock
    private DepartmentService departmentService;

    @Mock
    private EmployeeService employeeService;

    @InjectMocks
    private DepartmentController departmentController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        MappingJackson2HttpMessageConverter jacksonConverter = new MappingJackson2HttpMessageConverter();
        jacksonConverter.setObjectMapper(objectMapper);

        mockMvc = MockMvcBuilders.standaloneSetup(departmentController)
                .setMessageConverters(new StringHttpMessageConverter(), jacksonConverter)
                .setControllerAdvice(new GlobalExceptionHandler(null))
                .build();
    }

    @Test
    @DisplayName("POST /api/departments - Success returns 201 Created")
    void createDepartment_Success() throws Exception {
        DepartmentRequestDTO request = new DepartmentRequestDTO();
        request.setName("Engineering");

        DepartmentResponseDTO response = new DepartmentResponseDTO(1L, "Engineering");

        when(departmentService.createDepartment(any(DepartmentRequestDTO.class))).thenReturn(response);

        mockMvc.perform(post("/api/departments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Engineering"));

        verify(departmentService).createDepartment(any(DepartmentRequestDTO.class));
    }

    @Test
    @DisplayName("POST /api/departments - Validation error returns 400 Bad Request")
    void createDepartment_ValidationFailure() throws Exception {
        DepartmentRequestDTO request = new DepartmentRequestDTO(); // blank name

        mockMvc.perform(post("/api/departments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Validation Failed"));

        verifyNoInteractions(departmentService);
    }

    @Test
    @DisplayName("POST /api/departments - Duplicate name returns 409 Conflict")
    void createDepartment_DuplicateName() throws Exception {
        DepartmentRequestDTO request = new DepartmentRequestDTO();
        request.setName("Engineering");

        when(departmentService.createDepartment(any(DepartmentRequestDTO.class)))
                .thenThrow(new DuplicateResourceException("Department with name Engineering already exists"));

        mockMvc.perform(post("/api/departments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Department with name Engineering already exists"));
    }

    @Test
    @DisplayName("GET /api/departments - Returns list of departments")
    void getAllDepartments_Success() throws Exception {
        DepartmentResponseDTO d1 = new DepartmentResponseDTO(1L, "Engineering");
        DepartmentResponseDTO d2 = new DepartmentResponseDTO(2L, "HR");

        when(departmentService.getAllDepartments()).thenReturn(List.of(d1, d2));

        mockMvc.perform(get("/api/departments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Engineering"))
                .andExpect(jsonPath("$[1].name").value("HR"));

        verify(departmentService).getAllDepartments();
    }

    @Test
    @DisplayName("GET /api/departments/{id} - Success returns department")
    void getDepartmentById_Success() throws Exception {
        DepartmentResponseDTO response = new DepartmentResponseDTO(1L, "Engineering");

        when(departmentService.getDepartmentById(1L)).thenReturn(response);

        mockMvc.perform(get("/api/departments/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Engineering"));

        verify(departmentService).getDepartmentById(1L);
    }

    @Test
    @DisplayName("GET /api/departments/{id} - Not found returns 404")
    void getDepartmentById_NotFound() throws Exception {
        when(departmentService.getDepartmentById(99L))
                .thenThrow(new ResourceNotFoundException("Department not found with id: 99"));

        mockMvc.perform(get("/api/departments/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Department not found with id: 99"));
    }

    @Test
    @DisplayName("GET /api/departments/{id}/employees - Returns employees list")
    void getDepartmentEmployees_Success() throws Exception {
        EmployeeResponseDTO e1 = new EmployeeResponseDTO();
        e1.setId(10L);
        e1.setFirstName("Alice");
        e1.setLastName("Smith");

        when(employeeService.getEmployeesByDepartmentId(1L)).thenReturn(List.of(e1));

        mockMvc.perform(get("/api/departments/1/employees"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].firstName").value("Alice"));

        verify(employeeService).getEmployeesByDepartmentId(1L);
    }

    @Test
    @DisplayName("PUT /api/departments/{id} - Updates department")
    void updateDepartment_Success() throws Exception {
        DepartmentRequestDTO request = new DepartmentRequestDTO();
        request.setName("Product Engineering");

        DepartmentResponseDTO response = new DepartmentResponseDTO(1L, "Product Engineering");

        when(departmentService.updateDepartment(eq(1L), any(DepartmentRequestDTO.class))).thenReturn(response);

        mockMvc.perform(put("/api/departments/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Product Engineering"));

        verify(departmentService).updateDepartment(eq(1L), any(DepartmentRequestDTO.class));
    }

    @Test
    @DisplayName("DELETE /api/departments/{id} - Success returns 204 No Content")
    void deleteDepartment_Success() throws Exception {
        doNothing().when(departmentService).deleteDepartment(1L);

        mockMvc.perform(delete("/api/departments/1"))
                .andExpect(status().isNoContent());

        verify(departmentService).deleteDepartment(1L);
    }

    @Test
    @DisplayName("PATCH /api/departments/{id}/manager - Assigns manager")
    void assignManager_Success() throws Exception {
        DepartmentResponseDTO response = new DepartmentResponseDTO(1L, "Engineering", 5L, "Bob Jones");

        when(departmentService.assignManager(1L, 5L)).thenReturn(response);

        mockMvc.perform(patch("/api/departments/1/manager")
                        .param("managerId", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.managerId").value(5))
                .andExpect(jsonPath("$.managerName").value("Bob Jones"));

        verify(departmentService).assignManager(1L, 5L);
    }

    @Test
    @DisplayName("GET /api/departments/{id}/hierarchy - Returns department hierarchy")
    void getDepartmentHierarchy_Success() throws Exception {
        DepartmentHierarchyDTO hierarchy = new DepartmentHierarchyDTO(1L, "Engineering", null, new ArrayList<>(), new ArrayList<>(), 0);

        when(departmentService.getDepartmentHierarchy(1L)).thenReturn(hierarchy);

        mockMvc.perform(get("/api/departments/1/hierarchy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.departmentId").value(1))
                .andExpect(jsonPath("$.departmentName").value("Engineering"));

        verify(departmentService).getDepartmentHierarchy(1L);
    }

    @Test
    @DisplayName("GET /api/departments/hierarchy - Returns all department hierarchies")
    void getAllDepartmentHierarchies_Success() throws Exception {
        DepartmentHierarchyDTO hierarchy = new DepartmentHierarchyDTO(1L, "Engineering", null, new ArrayList<>(), new ArrayList<>(), 0);

        when(departmentService.getAllDepartmentHierarchies()).thenReturn(List.of(hierarchy));

        mockMvc.perform(get("/api/departments/hierarchy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].departmentId").value(1));

        verify(departmentService).getAllDepartmentHierarchies();
    }
}

