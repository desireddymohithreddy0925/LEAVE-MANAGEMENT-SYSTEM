package com.leave_management_system.leave_management_system.dto;

import jakarta.validation.constraints.NotBlank;

public class TokenRefreshRequestDTO {

    @NotBlank(message = "Refresh token is required")
    private String refreshToken;

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }
}
