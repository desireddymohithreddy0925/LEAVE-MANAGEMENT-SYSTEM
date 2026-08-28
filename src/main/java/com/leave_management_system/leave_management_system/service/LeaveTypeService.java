package com.leave_management_system.leave_management_system.service;

import com.leave_management_system.leave_management_system.entity.LeaveType;
import com.leave_management_system.leave_management_system.exception.ResourceNotFoundException;
import com.leave_management_system.leave_management_system.repository.LeaveTypeRepository;
import org.springframework.stereotype.Service;
import com.leave_management_system.leave_management_system.exception.DuplicateResourceException;

import java.util.List;

@Service
public class LeaveTypeService {

    private final LeaveTypeRepository leaveTypeRepository;

    public LeaveTypeService(LeaveTypeRepository leaveTypeRepository) {
        this.leaveTypeRepository = leaveTypeRepository;
    }

    public LeaveType createLeaveType(LeaveType leaveType) {

        if (leaveTypeRepository.findByName(leaveType.getName()).isPresent()) {
            throw new DuplicateResourceException("Leave type already exists");
        }

        return leaveTypeRepository.save(leaveType);
    }

    public List<LeaveType> getAllLeaveTypes() {
        return leaveTypeRepository.findAll();
    }

    public LeaveType getLeaveTypeById(Long id) {

        return leaveTypeRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Leave type not found with id: " + id
                        )
                );
    }

    public LeaveType updateLeaveType(Long id, LeaveType updatedLeaveType) {

        LeaveType existingLeaveType = getLeaveTypeById(id);

        if (!existingLeaveType.getName()
                .equals(updatedLeaveType.getName())
                && leaveTypeRepository.findByName(updatedLeaveType.getName()).isPresent()) {

            throw new DuplicateResourceException("Leave type already exists");
        }

        existingLeaveType.setName(updatedLeaveType.getName());
        existingLeaveType.setDescription(updatedLeaveType.getDescription());
        existingLeaveType.setDefaultDays(updatedLeaveType.getDefaultDays());

        return leaveTypeRepository.save(existingLeaveType);
    }

    public void deleteLeaveType(Long id) {

        LeaveType leaveType = getLeaveTypeById(id);

        leaveTypeRepository.delete(leaveType);
    }
}
