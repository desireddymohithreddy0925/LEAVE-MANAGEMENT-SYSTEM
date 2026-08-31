package com.leave_management_system.leave_management_system.service;

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

    @BeforeEach
    void setUp() {
        leaveType = new LeaveType("Annual Leave", "Annual leave desc", 20);
        leaveType.setId(1L);
    }

    @Test
    void createLeaveType_Success() {
        when(leaveTypeRepository.findByName(leaveType.getName())).thenReturn(Optional.empty());
        when(leaveTypeRepository.save(any(LeaveType.class))).thenReturn(leaveType);

        LeaveType savedLeaveType = leaveTypeService.createLeaveType(leaveType);

        assertNotNull(savedLeaveType);
        assertEquals("Annual Leave", savedLeaveType.getName());
    }

    @Test
    void createLeaveType_DuplicateName_ThrowsException() {
        when(leaveTypeRepository.findByName(leaveType.getName())).thenReturn(Optional.of(leaveType));

        assertThrows(DuplicateResourceException.class, () -> leaveTypeService.createLeaveType(leaveType));
    }

    @Test
    void getLeaveTypeById_Success() {
        when(leaveTypeRepository.findById(1L)).thenReturn(Optional.of(leaveType));

        LeaveType foundLeaveType = leaveTypeService.getLeaveTypeById(1L);

        assertNotNull(foundLeaveType);
        assertEquals(1L, foundLeaveType.getId());
    }

    @Test
    void activateLeaveType_Success() {
        leaveType.setActive(false);
        when(leaveTypeRepository.findById(1L)).thenReturn(Optional.of(leaveType));
        when(leaveTypeRepository.save(any(LeaveType.class))).thenReturn(leaveType);

        LeaveType activated = leaveTypeService.activateLeaveType(1L);

        assertTrue(activated.isActive());
        verify(leaveTypeRepository).save(leaveType);
    }

    @Test
    void deactivateLeaveType_Success() {
        leaveType.setActive(true);
        when(leaveTypeRepository.findById(1L)).thenReturn(Optional.of(leaveType));
        when(leaveTypeRepository.save(any(LeaveType.class))).thenReturn(leaveType);

        LeaveType deactivated = leaveTypeService.deactivateLeaveType(1L);

        assertFalse(deactivated.isActive());
        verify(leaveTypeRepository).save(leaveType);
    }
}
