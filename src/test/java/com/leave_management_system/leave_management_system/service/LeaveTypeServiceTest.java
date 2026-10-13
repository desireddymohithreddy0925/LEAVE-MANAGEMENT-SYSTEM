package com.leave_management_system.leave_management_system.service;

import com.leave_management_system.leave_management_system.dto.LeaveTypeRequestDTO;
import com.leave_management_system.leave_management_system.dto.LeaveTypeResponseDTO;
import com.leave_management_system.leave_management_system.entity.LeaveType;
import com.leave_management_system.leave_management_system.exception.DuplicateResourceException;
import com.leave_management_system.leave_management_system.exception.ResourceNotFoundException;
import com.leave_management_system.leave_management_system.repository.LeaveBalanceRepository;
import com.leave_management_system.leave_management_system.repository.LeaveRequestRepository;
import com.leave_management_system.leave_management_system.repository.LeaveTypeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class LeaveTypeServiceTest {

    @Mock
    private LeaveTypeRepository leaveTypeRepository;

    @Mock
    private LeaveBalanceRepository leaveBalanceRepository;

    @Mock
    private LeaveRequestRepository leaveRequestRepository;

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
        verify(leaveTypeRepository).save(any(LeaveType.class));
    }

    @Test
    void createLeaveType_DuplicateName_ThrowsException() {
        when(leaveTypeRepository.findByName(requestDTO.getName())).thenReturn(Optional.of(leaveType));

        assertThrows(DuplicateResourceException.class, () -> leaveTypeService.createLeaveType(requestDTO));
        verify(leaveTypeRepository, never()).save(any(LeaveType.class));
    }

    @Test
    void getAllLeaveTypes_All_Success() {
        LeaveType second = new LeaveType("Sick Leave", "Sick", 10);
        second.setId(2L);
        when(leaveTypeRepository.findAll()).thenReturn(List.of(leaveType, second));

        List<LeaveTypeResponseDTO> result = leaveTypeService.getAllLeaveTypes();

        assertEquals(2, result.size());
        verify(leaveTypeRepository).findAll();
    }

    @Test
    void getAllLeaveTypes_FilteredByActive_Success() {
        when(leaveTypeRepository.findByActive(true)).thenReturn(List.of(leaveType));

        List<LeaveTypeResponseDTO> result = leaveTypeService.getAllLeaveTypes(true);

        assertEquals(1, result.size());
        assertTrue(result.get(0).isActive());
        verify(leaveTypeRepository).findByActive(true);
    }

    @Test
    void getLeaveTypeById_Success() {
        when(leaveTypeRepository.findById(1L)).thenReturn(Optional.of(leaveType));

        LeaveTypeResponseDTO foundLeaveType = leaveTypeService.getLeaveTypeById(1L);

        assertNotNull(foundLeaveType);
        assertEquals(1L, foundLeaveType.getId());
    }

    @Test
    void getLeaveTypeById_NotFound_ThrowsException() {
        when(leaveTypeRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> leaveTypeService.getLeaveTypeById(99L));
    }

    @Test
    void updateLeaveType_Success() {
        when(leaveTypeRepository.findById(1L)).thenReturn(Optional.of(leaveType));
        when(leaveTypeRepository.save(any(LeaveType.class))).thenReturn(leaveType);

        LeaveTypeRequestDTO updateDTO = new LeaveTypeRequestDTO("Updated Leave", "New desc", 25);
        when(leaveTypeRepository.findByName("Updated Leave")).thenReturn(Optional.empty());

        LeaveTypeResponseDTO result = leaveTypeService.updateLeaveType(1L, updateDTO);

        assertNotNull(result);
        verify(leaveTypeRepository).save(leaveType);
    }

    @Test
    void updateLeaveType_NotFound_ThrowsException() {
        when(leaveTypeRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> leaveTypeService.updateLeaveType(99L, requestDTO));
    }

    @Test
    void updateLeaveType_DuplicateName_ThrowsException() {
        when(leaveTypeRepository.findById(1L)).thenReturn(Optional.of(leaveType));
        LeaveType other = new LeaveType("Existing Name", "Other", 15);
        other.setId(2L);
        when(leaveTypeRepository.findByName("Existing Name")).thenReturn(Optional.of(other));

        LeaveTypeRequestDTO conflictDTO = new LeaveTypeRequestDTO("Existing Name", "Desc", 15);

        assertThrows(DuplicateResourceException.class, () -> leaveTypeService.updateLeaveType(1L, conflictDTO));
        verify(leaveTypeRepository, never()).save(any(LeaveType.class));
    }

    @Test
    void deleteLeaveType_Success() {
        when(leaveTypeRepository.findById(1L)).thenReturn(Optional.of(leaveType));
        when(leaveBalanceRepository.existsByLeaveType(leaveType)).thenReturn(false);
        when(leaveRequestRepository.existsByLeaveType(leaveType)).thenReturn(false);

        leaveTypeService.deleteLeaveType(1L);

        verify(leaveTypeRepository).delete(leaveType);
    }

    @Test
    void deleteLeaveType_NotFound_ThrowsException() {
        when(leaveTypeRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> leaveTypeService.deleteLeaveType(99L));
        verify(leaveTypeRepository, never()).delete(any(LeaveType.class));
    }

    @Test
    void deleteLeaveType_HasLeaveBalances_ThrowsException() {
        when(leaveTypeRepository.findById(1L)).thenReturn(Optional.of(leaveType));
        when(leaveBalanceRepository.existsByLeaveType(leaveType)).thenReturn(true);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> leaveTypeService.deleteLeaveType(1L));

        assertEquals("Cannot delete leave type with existing balances or requests", ex.getMessage());
        verify(leaveTypeRepository, never()).delete(any(LeaveType.class));
    }

    @Test
    void deleteLeaveType_HasLeaveRequests_ThrowsException() {
        when(leaveTypeRepository.findById(1L)).thenReturn(Optional.of(leaveType));
        when(leaveBalanceRepository.existsByLeaveType(leaveType)).thenReturn(false);
        when(leaveRequestRepository.existsByLeaveType(leaveType)).thenReturn(true);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> leaveTypeService.deleteLeaveType(1L));

        assertEquals("Cannot delete leave type with existing balances or requests", ex.getMessage());
        verify(leaveTypeRepository, never()).delete(any(LeaveType.class));
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
    void activateLeaveType_NotFound_ThrowsException() {
        when(leaveTypeRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> leaveTypeService.activateLeaveType(99L));
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

    @Test
    void deactivateLeaveType_NotFound_ThrowsException() {
        when(leaveTypeRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> leaveTypeService.deactivateLeaveType(99L));
    }
}
