package com.leave_management_system.leave_management_system.dto;

public class CryptoResponseDTO {

    private String result;
    private boolean encrypted;

    public CryptoResponseDTO() {}

    public CryptoResponseDTO(String result, boolean encrypted) {
        this.result = result;
        this.encrypted = encrypted;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public boolean isEncrypted() {
        return encrypted;
    }

    public void setEncrypted(boolean encrypted) {
        this.encrypted = encrypted;
    }
}
