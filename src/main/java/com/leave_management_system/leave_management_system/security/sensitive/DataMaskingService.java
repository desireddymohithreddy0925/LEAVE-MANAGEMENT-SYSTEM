package com.leave_management_system.leave_management_system.security.sensitive;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class DataMaskingService {

    private static final Pattern SENSITIVE_JSON_PATTERN = Pattern.compile(
            "(\"(?:password|currentPassword|newPassword|confirmPassword|confirmationPassword|token|refreshToken|rawRefreshToken|secret|credentials|authorization|salary|phone|ssn|creditCard|cardNumber|cvv|pin|apiKey|secretKey)\"\\s*:\\s*\")[^\"]*(\")",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern SENSITIVE_KV_PATTERN = Pattern.compile(
            "(?i)(password|token|secret|refreshToken|credentials|salary|phone|ssn|creditCard|cardNumber|cvv|pin|apiKey|secretKey)\\s*=\\s*[^,\\s}]+",
            Pattern.CASE_INSENSITIVE
    );

    private static final Set<String> SENSITIVE_KEY_NAMES = Set.of(
            "password", "currentpassword", "newpassword", "confirmpassword",
            "token", "refreshtoken", "rawrefreshtoken", "secret", "credentials",
            "authorization", "salary", "phone", "ssn", "creditcard", "cardnumber",
            "cvv", "pin", "apikey", "secretkey"
    );

    /**
     * Masks a phone number, preserving the last 4 digits while redacting preceding digits.
     * Example: "+1 555-123-4567" -> "+1 ***-***-4567" or "******4567"
     */
    public String maskPhone(String phone) {
        if (phone == null || phone.trim().isEmpty()) {
            return phone;
        }
        String trimmed = phone.trim();
        String digitsOnly = trimmed.replaceAll("[^0-9]", "");
        if (digitsOnly.length() <= 4) {
            return "****";
        }

        String last4 = digitsOnly.substring(digitsOnly.length() - 4);
        int maskedCount = Math.max(4, digitsOnly.length() - 4);
        String prefix = trimmed.startsWith("+") ? "+* " : "";
        return prefix + "*".repeat(maskedCount) + last4;
    }

    /**
     * Masks an email address: first character + '***' + last character of local part + '@domain'.
     * Example: "john.doe@example.com" -> "j***e@example.com"
     */
    public String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return email != null ? "***" : null;
        }
        int atIndex = email.indexOf('@');
        String local = email.substring(0, atIndex);
        String domain = email.substring(atIndex);

        if (local.length() <= 1) {
            return "* " + domain;
        } else if (local.length() == 2) {
            return local.charAt(0) + "*" + domain;
        } else {
            return local.charAt(0) + "***" + local.charAt(local.length() - 1) + domain;
        }
    }

    /**
     * Masks salary compensation. Returns null to conceal the financial value.
     */
    public BigDecimal maskSalary(BigDecimal salary) {
        return null;
    }

    /**
     * Formatted string display for masked salary.
     */
    public String maskSalaryDisplay(BigDecimal salary) {
        return "[CONFIDENTIAL]";
    }

    /**
     * Masks security authentication tokens, retaining initial segment for debugging.
     */
    public String maskToken(String token) {
        if (token == null || token.length() < 10) {
            return "[REDACTED]";
        }
        return token.substring(0, 6) + "...[REDACTED]";
    }

    /**
     * Masks a password or credential.
     */
    public String maskPassword(String password) {
        return "********";
    }

    /**
     * Masks payment card number, preserving only the last 4 digits.
     */
    public String maskCreditCard(String cardNumber) {
        if (cardNumber == null || cardNumber.trim().isEmpty()) {
            return cardNumber;
        }
        String digits = cardNumber.replaceAll("[^0-9]", "");
        if (digits.length() <= 4) {
            return "****";
        }
        String last4 = digits.substring(digits.length() - 4);
        return "*".repeat(digits.length() - 4) + last4;
    }

    /**
     * Masks national ID / SSN, formatting as '***-**-XXXX'.
     */
    public String maskNationalId(String ssn) {
        if (ssn == null || ssn.trim().isEmpty()) {
            return ssn;
        }
        String digits = ssn.replaceAll("[^0-9]", "");
        if (digits.length() <= 4) {
            return "***-**-****";
        }
        String last4 = digits.substring(digits.length() - 4);
        return "***-**-" + last4;
    }

    /**
     * Generic string masking preserving a specified number of leading and trailing characters.
     */
    public String maskGeneric(String value, int visiblePrefix, int visibleSuffix, char maskChar) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        int len = value.length();
        if (len <= (visiblePrefix + visibleSuffix)) {
            return String.valueOf(maskChar).repeat(len);
        }
        String prefix = value.substring(0, visiblePrefix);
        String suffix = value.substring(len - visibleSuffix);
        String mask = String.valueOf(maskChar).repeat(len - visiblePrefix - visibleSuffix);
        return prefix + mask + suffix;
    }

    /**
     * Sanitizes JSON strings by replacing sensitive field values with [REDACTED].
     */
    public String sanitizeJson(String json) {
        if (json == null || json.isEmpty()) {
            return json;
        }
        String sanitized = SENSITIVE_JSON_PATTERN.matcher(json).replaceAll("$1[REDACTED]$2");
        return SENSITIVE_KV_PATTERN.matcher(sanitized).replaceAll("$1=[REDACTED]");
    }

    /**
     * Recursively sanitizes a map by redacting sensitive keys.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> sanitizeMap(Map<String, Object> input) {
        if (input == null) {
            return null;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : input.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if (isSensitiveKey(key)) {
                result.put(key, "[REDACTED]");
            } else if (value instanceof Map) {
                result.put(key, sanitizeMap((Map<String, Object>) value));
            } else if (value instanceof List<?> list) {
                result.put(key, sanitizeList(list));
            } else if (value instanceof String strVal) {
                result.put(key, sanitizeJson(strVal));
            } else {
                result.put(key, value);
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<Object> sanitizeList(List<?> list) {
        List<Object> sanitizedList = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map) {
                sanitizedList.add(sanitizeMap((Map<String, Object>) item));
            } else if (item instanceof List<?> subList) {
                sanitizedList.add(sanitizeList(subList));
            } else if (item instanceof String strItem) {
                sanitizedList.add(sanitizeJson(strItem));
            } else {
                sanitizedList.add(item);
            }
        }
        return sanitizedList;
    }

    private boolean isSensitiveKey(String key) {
        if (key == null) return false;
        String normalized = key.toLowerCase().replaceAll("[^a-z]", "");
        return SENSITIVE_KEY_NAMES.contains(normalized);
    }
}
