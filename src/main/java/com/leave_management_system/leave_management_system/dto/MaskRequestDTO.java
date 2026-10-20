package com.leave_management_system.leave_management_system.dto;

import com.leave_management_system.leave_management_system.security.sensitive.SensitiveType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class MaskRequestDTO {

    @NotBlank(message = "Value to mask cannot be blank")
    private String value;

    @NotNull(message = "Sensitive type is required")
    private SensitiveType type;

    public MaskRequestDTO() {}

    public MaskRequestDTO(String value, SensitiveType type) {
        this.value = value;
        this.type = type;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public SensitiveType getType() {
        return type;
    }

    public void setType(SensitiveType type) {
        this.type = type;
    }
}
