package com.leave_management_system.leave_management_system.service;

import com.leave_management_system.leave_management_system.dto.LeaveTypeRequestDTO;
import com.leave_management_system.leave_management_system.dto.LeaveTypeResponseDTO;
import com.leave_management_system.leave_management_system.entity.LeaveType;
import com.leave_management_system.leave_management_system.exception.ResourceNotFoundException;
import com.leave_management_system.leave_management_system.repository.LeaveTypeRepository;
import org.springframework.stereotype.Service;
import com.leave_management_system.leave_management_system.exception.DuplicateResourceException;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class LeaveTypeService {

    private final LeaveTypeRepository leaveTypeRepository;

    public LeaveTypeService(LeaveTypeRepository leaveTypeRepository) {
        this.leaveTypeRepository = leaveTypeRepository;
    }

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

    public List<LeaveTypeResponseDTO> getAllLeaveTypes() {
        return leaveTypeRepository.findAll().stream()
                .map(LeaveTypeResponseDTO::fromEntity)
                .collect(Collectors.toList());
    }

    public LeaveTypeResponseDTO getLeaveTypeById(Long id) {
        LeaveType leaveType = leaveTypeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Leave type not found with id: " + id));
        return LeaveTypeResponseDTO.fromEntity(leaveType);
    }

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

    public void deleteLeaveType(Long id) {
        LeaveType leaveType = leaveTypeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Leave type not found with id: " + id));

        leaveTypeRepository.delete(leaveType);
    }

    public LeaveTypeResponseDTO activateLeaveType(Long id) {
        LeaveType leaveType = leaveTypeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Leave type not found with id: " + id));
        leaveType.setActive(true);
        return LeaveTypeResponseDTO.fromEntity(leaveTypeRepository.save(leaveType));
    }

    public LeaveTypeResponseDTO deactivateLeaveType(Long id) {
        LeaveType leaveType = leaveTypeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Leave type not found with id: " + id));
        leaveType.setActive(false);
        return LeaveTypeResponseDTO.fromEntity(leaveTypeRepository.save(leaveType));
    }
}
