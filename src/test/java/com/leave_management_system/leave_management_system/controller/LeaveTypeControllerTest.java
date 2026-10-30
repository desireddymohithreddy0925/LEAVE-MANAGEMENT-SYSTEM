package com.leave_management_system.leave_management_system.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.leave_management_system.leave_management_system.dto.LeaveTypeRequestDTO;
import com.leave_management_system.leave_management_system.dto.LeaveTypeResponseDTO;
import com.leave_management_system.leave_management_system.exception.DuplicateResourceException;
import com.leave_management_system.leave_management_system.exception.GlobalExceptionHandler;
import com.leave_management_system.leave_management_system.exception.ResourceNotFoundException;
import com.leave_management_system.leave_management_system.service.LeaveTypeService;
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

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class LeaveTypeControllerTest {

    @Mock
    private LeaveTypeService leaveTypeService;

    @InjectMocks
    private LeaveTypeController leaveTypeController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        MappingJackson2HttpMessageConverter jacksonConverter = new MappingJackson2HttpMessageConverter();
        jacksonConverter.setObjectMapper(objectMapper);

        mockMvc = MockMvcBuilders.standaloneSetup(leaveTypeController)
                .setMessageConverters(new StringHttpMessageConverter(), jacksonConverter)
                .setControllerAdvice(new GlobalExceptionHandler(null))
                .build();
    }

    @Test
    @DisplayName("POST /api/leave-types - Success returns 201 Created")
    void createLeaveType_Success() throws Exception {
        LeaveTypeRequestDTO request = new LeaveTypeRequestDTO("Sick Leave", "Medical leave", 10);
        LeaveTypeResponseDTO response = new LeaveTypeResponseDTO(1L, "Sick Leave", "Medical leave", 10, true);

        when(leaveTypeService.createLeaveType(any(LeaveTypeRequestDTO.class))).thenReturn(response);

        mockMvc.perform(post("/api/leave-types")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Sick Leave"))
                .andExpect(jsonPath("$.defaultDays").value(10))
                .andExpect(jsonPath("$.active").value(true));

        verify(leaveTypeService).createLeaveType(any(LeaveTypeRequestDTO.class));
    }

    @Test
    @DisplayName("POST /api/leave-types - Validation error returns 400 Bad Request")
    void createLeaveType_ValidationError() throws Exception {
        LeaveTypeRequestDTO request = new LeaveTypeRequestDTO("", "No name", 0);

        mockMvc.perform(post("/api/leave-types")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Validation Failed"));

        verifyNoInteractions(leaveTypeService);
    }

    @Test
    @DisplayName("POST /api/leave-types - Duplicate name returns 409 Conflict")
    void createLeaveType_Duplicate() throws Exception {
        LeaveTypeRequestDTO request = new LeaveTypeRequestDTO("Annual Leave", "Vacation", 15);

        when(leaveTypeService.createLeaveType(any(LeaveTypeRequestDTO.class)))
                .thenThrow(new DuplicateResourceException("Leave type with name Annual Leave already exists"));

        mockMvc.perform(post("/api/leave-types")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Leave type with name Annual Leave already exists"));
    }

    @Test
    @DisplayName("GET /api/leave-types - Returns list")
    void getAllLeaveTypes_Success() throws Exception {
        LeaveTypeResponseDTO lt1 = new LeaveTypeResponseDTO(1L, "Annual", "Vacation", 20, true);
        LeaveTypeResponseDTO lt2 = new LeaveTypeResponseDTO(2L, "Sick", "Medical", 10, true);

        when(leaveTypeService.getAllLeaveTypes(null)).thenReturn(List.of(lt1, lt2));

        mockMvc.perform(get("/api/leave-types"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Annual"))
                .andExpect(jsonPath("$[1].name").value("Sick"));

        verify(leaveTypeService).getAllLeaveTypes(null);
    }

    @Test
    @DisplayName("GET /api/leave-types with ?active=true - Returns active leave types")
    void getAllLeaveTypes_WithActiveFilter() throws Exception {
        LeaveTypeResponseDTO lt1 = new LeaveTypeResponseDTO(1L, "Annual", "Vacation", 20, true);

        when(leaveTypeService.getAllLeaveTypes(true)).thenReturn(List.of(lt1));

        mockMvc.perform(get("/api/leave-types").param("active", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Annual"));

        verify(leaveTypeService).getAllLeaveTypes(true);
    }

    @Test
    @DisplayName("GET /api/leave-types/{id} - Success returns leave type")
    void getLeaveTypeById_Success() throws Exception {
        LeaveTypeResponseDTO response = new LeaveTypeResponseDTO(1L, "Annual", "Vacation", 20, true);

        when(leaveTypeService.getLeaveTypeById(1L)).thenReturn(response);

        mockMvc.perform(get("/api/leave-types/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Annual"));

        verify(leaveTypeService).getLeaveTypeById(1L);
    }

    @Test
    @DisplayName("GET /api/leave-types/{id} - Not found returns 404")
    void getLeaveTypeById_NotFound() throws Exception {
        when(leaveTypeService.getLeaveTypeById(99L))
                .thenThrow(new ResourceNotFoundException("Leave type not found with id: 99"));

        mockMvc.perform(get("/api/leave-types/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));

        verify(leaveTypeService).getLeaveTypeById(99L);
    }

    @Test
    @DisplayName("PUT /api/leave-types/{id} - Updates leave type")
    void updateLeaveType_Success() throws Exception {
        LeaveTypeRequestDTO request = new LeaveTypeRequestDTO("Casual Leave", "Casual", 12);
        LeaveTypeResponseDTO response = new LeaveTypeResponseDTO(1L, "Casual Leave", "Casual", 12, true);

        when(leaveTypeService.updateLeaveType(eq(1L), any(LeaveTypeRequestDTO.class))).thenReturn(response);

        mockMvc.perform(put("/api/leave-types/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Casual Leave"))
                .andExpect(jsonPath("$.defaultDays").value(12));

        verify(leaveTypeService).updateLeaveType(eq(1L), any(LeaveTypeRequestDTO.class));
    }

    @Test
    @DisplayName("DELETE /api/leave-types/{id} - Returns 204 No Content")
    void deleteLeaveType_Success() throws Exception {
        doNothing().when(leaveTypeService).deleteLeaveType(1L);

        mockMvc.perform(delete("/api/leave-types/1"))
                .andExpect(status().isNoContent());

        verify(leaveTypeService).deleteLeaveType(1L);
    }

    @Test
    @DisplayName("PUT /api/leave-types/{id}/activate - Activates leave type")
    void activateLeaveType_Success() throws Exception {
        LeaveTypeResponseDTO response = new LeaveTypeResponseDTO(1L, "Sabbatical", "Leave", 30, true);

        when(leaveTypeService.activateLeaveType(1L)).thenReturn(response);

        mockMvc.perform(put("/api/leave-types/1/activate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));

        verify(leaveTypeService).activateLeaveType(1L);
    }

    @Test
    @DisplayName("PUT /api/leave-types/{id}/deactivate - Deactivates leave type")
    void deactivateLeaveType_Success() throws Exception {
        LeaveTypeResponseDTO response = new LeaveTypeResponseDTO(1L, "Sabbatical", "Leave", 30, false);

        when(leaveTypeService.deactivateLeaveType(1L)).thenReturn(response);

        mockMvc.perform(put("/api/leave-types/1/deactivate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        verify(leaveTypeService).deactivateLeaveType(1L);
    }
}

