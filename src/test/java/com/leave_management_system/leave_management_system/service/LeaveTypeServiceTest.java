package com.leave_management_system.leave_management_system.service;

import com.leave_management_system.leave_management_system.dto.LeaveTypeRequestDTO;
import com.leave_management_system.leave_management_system.dto.LeaveTypeResponseDTO;
import com.leave_management_system.leave_management_system.entity.LeaveType;
import com.leave_management_system.leave_management_system.exception.DuplicateResourceException;
import com.leave_management_system.leave_management_system.exception.ResourceNotFoundException;
import com.leave_management_system.leave_management_system.repository.LeaveTypeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class LeaveTypeServiceTest {

    @Mock
    private LeaveTypeRepository leaveTypeRepository;

    @InjectMocks
    private LeaveTypeService leaveTypeService;

    private LeaveType leaveType;
    private LeaveTypeRequestDTO requestDTO;

    @BeforeEach
    void setUp() {
        leaveType = new LeaveType("Annual Leave", "Annual leave desc", 20);
        leaveType.setId(1L);

        requestDTO = new LeaveTypeRequestDTO();
        requestDTO.setName("Annual Leave");
        requestDTO.setDescription("Annual leave desc");
        requestDTO.setDefaultDays(20);
    }

    @Test
    void createLeaveType_Success() {
        when(leaveTypeRepository.findByName(requestDTO.getName())).thenReturn(Optional.empty());
        when(leaveTypeRepository.save(any(LeaveType.class))).thenReturn(leaveType);

        LeaveTypeResponseDTO savedLeaveType = leaveTypeService.createLeaveType(requestDTO);

        assertNotNull(savedLeaveType);
        assertEquals("Annual Leave", savedLeaveType.getName());
    }

    @Test
    void createLeaveType_DuplicateName_ThrowsException() {
        when(leaveTypeRepository.findByName(requestDTO.getName())).thenReturn(Optional.of(leaveType));

        assertThrows(DuplicateResourceException.class, () -> leaveTypeService.createLeaveType(requestDTO));
    }

    @Test
    void getLeaveTypeById_Success() {
        when(leaveTypeRepository.findById(1L)).thenReturn(Optional.of(leaveType));

        LeaveTypeResponseDTO foundLeaveType = leaveTypeService.getLeaveTypeById(1L);

        assertNotNull(foundLeaveType);
        assertEquals(1L, foundLeaveType.getId());
    }

    @Test
    void activateLeaveType_Success() {
        leaveType.setActive(false);
        when(leaveTypeRepository.findById(1L)).thenReturn(Optional.of(leaveType));
        when(leaveTypeRepository.save(any(LeaveType.class))).thenReturn(leaveType);

        LeaveTypeResponseDTO activated = leaveTypeService.activateLeaveType(1L);

        assertTrue(activated.isActive());
        verify(leaveTypeRepository).save(leaveType);
    }

    @Test
    void deactivateLeaveType_Success() {
        leaveType.setActive(true);
        when(leaveTypeRepository.findById(1L)).thenReturn(Optional.of(leaveType));
        when(leaveTypeRepository.save(any(LeaveType.class))).thenReturn(leaveType);

        LeaveTypeResponseDTO deactivated = leaveTypeService.deactivateLeaveType(1L);

        assertFalse(deactivated.isActive());
        verify(leaveTypeRepository).save(leaveType);
    }
}
