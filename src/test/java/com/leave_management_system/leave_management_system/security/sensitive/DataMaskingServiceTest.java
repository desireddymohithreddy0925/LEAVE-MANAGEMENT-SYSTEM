package com.leave_management_system.leave_management_system.security.sensitive;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class DataMaskingServiceTest {

    private DataMaskingService maskingService;

    @BeforeEach
    void setUp() {
        maskingService = new DataMaskingService();
    }

    @Test
    @DisplayName("maskPhone: preserves last 4 digits while redacting preceding digits")
    void maskPhone_StandardPhone() {
        String masked = maskingService.maskPhone("+1 555-123-4567");
        assertTrue(masked.endsWith("4567"));
        assertTrue(masked.contains("*"));

        assertEquals("****", maskingService.maskPhone("123"));
        assertNull(maskingService.maskPhone(null));
        assertEquals("", maskingService.maskPhone(""));
    }

    @Test
    @DisplayName("maskEmail: masks local part while preserving domain")
    void maskEmail_StandardEmail() {
        assertEquals("j***e@example.com", maskingService.maskEmail("john.doe@example.com"));
        assertEquals("a*@b.com", maskingService.maskEmail("ab@b.com"));
        assertEquals("* @b.com", maskingService.maskEmail("a@b.com"));
        assertNull(maskingService.maskEmail(null));
        assertEquals("***", maskingService.maskEmail("invalid-email-string"));
    }

    @Test
    @DisplayName("maskSalary and maskSalaryDisplay: redacts salary value")
    void maskSalary_RedactsFinancialValue() {
        assertNull(maskingService.maskSalary(new BigDecimal("125000.00")));
        assertEquals("[CONFIDENTIAL]", maskingService.maskSalaryDisplay(new BigDecimal("125000.00")));
    }

    @Test
    @DisplayName("maskPassword: returns redacted asterisks")
    void maskPassword_RedactsValue() {
        assertEquals("********", maskingService.maskPassword("SuperSecret123"));
    }

    @Test
    @DisplayName("maskCreditCard: preserves last 4 digits of card number")
    void maskCreditCard_PreservesLast4Digits() {
        String masked = maskingService.maskCreditCard("4111-2222-3333-4444");
        assertTrue(masked.endsWith("4444"));
        assertTrue(masked.startsWith("************"));
    }

    @Test
    @DisplayName("maskNationalId: formats as ***-**-XXXX")
    void maskNationalId_FormatsCorrectly() {
        assertEquals("***-**-6789", maskingService.maskNationalId("123-45-6789"));
        assertEquals("***-**-****", maskingService.maskNationalId("12"));
    }

    @Test
    @DisplayName("maskGeneric: preserves specified visible prefix and suffix")
    void maskGeneric_CustomVisibleLengths() {
        assertEquals("AB*******YZ", maskingService.maskGeneric("ABCDEFGHXYZ", 2, 2, '*'));
    }

    @Test
    @DisplayName("sanitizeJson: masks sensitive JSON fields with [REDACTED]")
    void sanitizeJson_RedactsKeys() {
        String json = "{\"username\":\"admin\",\"password\":\"secretPass\",\"token\":\"jwt123\"}";
        String masked = maskingService.sanitizeJson(json);

        assertFalse(masked.contains("secretPass"));
        assertFalse(masked.contains("jwt123"));
        assertTrue(masked.contains("[REDACTED]"));
        assertTrue(masked.contains("\"username\":\"admin\""));
    }

    @Test
    @DisplayName("sanitizeMap: replaces sensitive keys in map with [REDACTED]")
    void sanitizeMap_RedactsSensitiveEntries() {
        Map<String, Object> map = new HashMap<>();
        map.put("name", "Alice");
        map.put("password", "p@ssword123");
        map.put("salary", 95000);

        Map<String, Object> masked = maskingService.sanitizeMap(map);

        assertEquals("Alice", masked.get("name"));
        assertEquals("[REDACTED]", masked.get("password"));
        assertEquals("[REDACTED]", masked.get("salary"));
    }
}
