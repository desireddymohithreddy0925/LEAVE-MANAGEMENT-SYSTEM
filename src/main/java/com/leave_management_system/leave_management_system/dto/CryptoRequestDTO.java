package com.leave_management_system.leave_management_system.dto;

import jakarta.validation.constraints.NotBlank;

public class CryptoRequestDTO {

    @NotBlank(message = "Data cannot be blank")
    private String data;

    public CryptoRequestDTO() {}

    public CryptoRequestDTO(String data) {
        this.data = data;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }
}
