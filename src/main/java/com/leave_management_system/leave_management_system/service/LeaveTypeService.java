package com.leave_management_system.leave_management_system.service;

import com.leave_management_system.leave_management_system.aspect.AuditAction;
import com.leave_management_system.leave_management_system.dto.LeaveTypeRequestDTO;
import com.leave_management_system.leave_management_system.dto.LeaveTypeResponseDTO;
import com.leave_management_system.leave_management_system.entity.LeaveType;
import com.leave_management_system.leave_management_system.exception.DuplicateResourceException;
import com.leave_management_system.leave_management_system.exception.ResourceNotFoundException;
import com.leave_management_system.leave_management_system.repository.LeaveBalanceRepository;
import com.leave_management_system.leave_management_system.repository.LeaveRequestRepository;
import com.leave_management_system.leave_management_system.repository.LeaveTypeRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@PreAuthorize("hasAuthority('LEAVE_TYPE_MANAGE')")
public class LeaveTypeService {

    private final LeaveTypeRepository leaveTypeRepository;
    private final LeaveBalanceRepository leaveBalanceRepository;
    private final LeaveRequestRepository leaveRequestRepository;

    public LeaveTypeService(LeaveTypeRepository leaveTypeRepository,
                            LeaveBalanceRepository leaveBalanceRepository,
                            LeaveRequestRepository leaveRequestRepository) {
        this.leaveTypeRepository = leaveTypeRepository;
        this.leaveBalanceRepository = leaveBalanceRepository;
        this.leaveRequestRepository = leaveRequestRepository;
    }

    @AuditAction(action = "LEAVE_TYPE_CREATED", entityType = "LeaveType")
    public LeaveTypeResponseDTO createLeaveType(LeaveTypeRequestDTO dto) {
        if (leaveTypeRepository.findByName(dto.getName()).isPresent()) {
            throw new DuplicateResourceException("Leave type already exists");
        }

        LeaveType leaveType = new LeaveType();
        leaveType.setName(dto.getName());
        leaveType.setDescription(dto.getDescription());
        leaveType.setDefaultDays(dto.getDefaultDays());
        return LeaveTypeResponseDTO.fromEntity(leaveTypeRepository.save(leaveType));
    }

    @PreAuthorize("isAuthenticated()")
    public List<LeaveTypeResponseDTO> getAllLeaveTypes() {
        return getAllLeaveTypes(null);
    }

    @PreAuthorize("isAuthenticated()")
    public List<LeaveTypeResponseDTO> getAllLeaveTypes(Boolean active) {
        List<LeaveType> leaveTypes = (active == null)
                ? leaveTypeRepository.findAll()
                : leaveTypeRepository.findByActive(active);

        return leaveTypes.stream()
                .map(LeaveTypeResponseDTO::fromEntity)
                .collect(Collectors.toList());
    }

    @PreAuthorize("isAuthenticated()")
    public LeaveTypeResponseDTO getLeaveTypeById(Long id) {
        LeaveType leaveType = leaveTypeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Leave type not found with id: " + id));
        return LeaveTypeResponseDTO.fromEntity(leaveType);
    }

    @AuditAction(action = "LEAVE_TYPE_UPDATED", entityType = "LeaveType")
    public LeaveTypeResponseDTO updateLeaveType(Long id, LeaveTypeRequestDTO dto) {
        LeaveType existingLeaveType = leaveTypeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Leave type not found with id: " + id));

        if (!existingLeaveType.getName().equals(dto.getName())
                && leaveTypeRepository.findByName(dto.getName()).isPresent()) {
            throw new DuplicateResourceException("Leave type already exists");
        }

        existingLeaveType.setName(dto.getName());
        existingLeaveType.setDescription(dto.getDescription());
        existingLeaveType.setDefaultDays(dto.getDefaultDays());

        return LeaveTypeResponseDTO.fromEntity(leaveTypeRepository.save(existingLeaveType));
    }

    @AuditAction(action = "LEAVE_TYPE_DELETED", entityType = "LeaveType")
    public void deleteLeaveType(Long id) {
        LeaveType leaveType = leaveTypeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Leave type not found with id: " + id));

        if (leaveBalanceRepository.existsByLeaveType(leaveType) || leaveRequestRepository.existsByLeaveType(leaveType)) {
            throw new IllegalArgumentException("Cannot delete leave type with existing balances or requests");
        }

        leaveTypeRepository.delete(leaveType);
    }

    @AuditAction(action = "LEAVE_TYPE_ACTIVATED", entityType = "LeaveType")
    public LeaveTypeResponseDTO activateLeaveType(Long id) {
        LeaveType leaveType = leaveTypeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Leave type not found with id: " + id));
        leaveType.setActive(true);
        return LeaveTypeResponseDTO.fromEntity(leaveTypeRepository.save(leaveType));
    }

    @AuditAction(action = "LEAVE_TYPE_DEACTIVATED", entityType = "LeaveType")
    public LeaveTypeResponseDTO deactivateLeaveType(Long id) {
        LeaveType leaveType = leaveTypeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Leave type not found with id: " + id));
        leaveType.setActive(false);
        return LeaveTypeResponseDTO.fromEntity(leaveTypeRepository.save(leaveType));
    }
}
