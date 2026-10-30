package com.leave_management_system.leave_management_system.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.leave_management_system.leave_management_system.dto.UserResponseDTO;
import com.leave_management_system.leave_management_system.exception.GlobalExceptionHandler;
import com.leave_management_system.leave_management_system.exception.ResourceNotFoundException;
import com.leave_management_system.leave_management_system.service.UserService;
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

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock
    private UserService userService;

    @InjectMocks
    private UserController userController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        MappingJackson2HttpMessageConverter jacksonConverter = new MappingJackson2HttpMessageConverter();
        jacksonConverter.setObjectMapper(objectMapper);

        mockMvc = MockMvcBuilders.standaloneSetup(userController)
                .setMessageConverters(new StringHttpMessageConverter(), jacksonConverter)
                .setControllerAdvice(new GlobalExceptionHandler(null))
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();
    }

    @Test
    @DisplayName("GET /api/users - Returns paginated list of users")
    void getAllUsers_Success() throws Exception {
        UserResponseDTO user = new UserResponseDTO();
        user.setId(1L);
        user.setEmail("admin@example.com");

        Page<UserResponseDTO> page = new PageImpl<>(List.of(user), PageRequest.of(0, 10), 1);

        when(userService.getAllUsers(eq(null), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/users")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.content[0].email").value("admin@example.com"));

        verify(userService).getAllUsers(eq(null), any(Pageable.class));
    }

    @Test
    @DisplayName("GET /api/users with locked filter - Returns filtered users")
    void getAllUsers_WithFilter() throws Exception {
        UserResponseDTO user = new UserResponseDTO();
        user.setId(2L);
        user.setEmail("locked@example.com");
        user.setAccountNonLocked(false);

        Page<UserResponseDTO> page = new PageImpl<>(List.of(user), PageRequest.of(0, 10), 1);

        when(userService.getAllUsers(eq(true), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/users")
                        .param("locked", "true")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(2))
                .andExpect(jsonPath("$.content[0].accountNonLocked").value(false));

        verify(userService).getAllUsers(eq(true), any(Pageable.class));
    }

    @Test
    @DisplayName("GET /api/users/{id} - Success returns user")
    void getUserById_Success() throws Exception {
        UserResponseDTO user = new UserResponseDTO();
        user.setId(1L);
        user.setEmail("admin@example.com");

        when(userService.getUserById(1L)).thenReturn(user);

        mockMvc.perform(get("/api/users/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.email").value("admin@example.com"));

        verify(userService).getUserById(1L);
    }

    @Test
    @DisplayName("GET /api/users/{id} - Not found returns 404")
    void getUserById_NotFound() throws Exception {
        when(userService.getUserById(99L)).thenThrow(new ResourceNotFoundException("User not found with id: 99"));

        mockMvc.perform(get("/api/users/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));

        verify(userService).getUserById(99L);
    }

    @Test
    @DisplayName("PUT /api/users/{id}/unlock - Unlocks user account")
    void unlockUser_Success() throws Exception {
        UserResponseDTO user = new UserResponseDTO();
        user.setId(1L);
        user.setAccountNonLocked(true);

        when(userService.unlockUser(1L)).thenReturn(user);

        mockMvc.perform(put("/api/users/1/unlock"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.accountNonLocked").value(true));

        verify(userService).unlockUser(1L);
    }

    @Test
    @DisplayName("PUT /api/users/{id}/lock - Locks user account with reason")
    void lockUser_Success() throws Exception {
        UserResponseDTO user = new UserResponseDTO();
        user.setId(1L);
        user.setAccountNonLocked(false);

        when(userService.lockUser(1L, "Security threat")).thenReturn(user);

        mockMvc.perform(put("/api/users/1/lock")
                        .param("reason", "Security threat"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.accountNonLocked").value(false));

        verify(userService).lockUser(1L, "Security threat");
    }
}

