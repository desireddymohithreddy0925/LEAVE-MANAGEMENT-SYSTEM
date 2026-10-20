package com.leave_management_system.leave_management_system.dto;

import com.leave_management_system.leave_management_system.security.sensitive.SensitiveType;

public class MaskResponseDTO {

    private String maskedValue;
    private SensitiveType type;
    private int originalLength;

    public MaskResponseDTO() {}

    public MaskResponseDTO(String maskedValue, SensitiveType type, int originalLength) {
        this.maskedValue = maskedValue;
        this.type = type;
        this.originalLength = originalLength;
    }

    public String getMaskedValue() {
        return maskedValue;
    }

    public void setMaskedValue(String maskedValue) {
        this.maskedValue = maskedValue;
    }

    public SensitiveType getType() {
        return type;
    }

    public void setType(SensitiveType type) {
        this.type = type;
    }

    public int getOriginalLength() {
        return originalLength;
    }

    public void setOriginalLength(int originalLength) {
        this.originalLength = originalLength;
    }
}
