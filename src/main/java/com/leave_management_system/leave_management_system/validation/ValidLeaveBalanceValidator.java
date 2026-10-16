package com.leave_management_system.leave_management_system.validation;

import com.leave_management_system.leave_management_system.dto.LeaveBalanceRequestDTO;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ValidLeaveBalanceValidator implements ConstraintValidator<ValidLeaveBalance, LeaveBalanceRequestDTO> {

    @Override
    public boolean isValid(LeaveBalanceRequestDTO dto, ConstraintValidatorContext context) {
        if (dto == null) {
            return true;
        }

        if (dto.getAvailable() != null && dto.getTotal() != null && dto.getAvailable() >= 0 && dto.getTotal() >= 0) {
            if (dto.getAvailable() > dto.getTotal()) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate("Available days cannot exceed total days")
                        .addPropertyNode("available")
                        .addConstraintViolation();
                return false;
            }
        }

        return true;
    }
}
