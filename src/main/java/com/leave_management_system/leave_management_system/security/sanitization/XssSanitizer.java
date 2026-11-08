package com.leave_management_system.leave_management_system.security.sanitization;

import org.springframework.web.util.HtmlUtils;

import java.util.regex.Pattern;

/**
 * Enterprise-grade XSS Sanitizer adhering to OWASP Top 10 Injection (A03) guidance.
 * Provides defensive methods to detect, neutralize, and escape untrusted user input
 * before rendering or processing.
 */
public final class XssSanitizer {

    private static final Pattern SCRIPT_TAG_PATTERN = Pattern.compile(
            "<\\s*script[^>]*>.*?<\\s*/\\s*script\\s*>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    private static final Pattern LONE_SCRIPT_TAG_PATTERN = Pattern.compile(
            "<\\s*/?\\s*script[^>]*>",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern JAVASCRIPT_PSEUDO_PROTOCOL = Pattern.compile(
            "javascript\\s*:",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern VBSCRIPT_PSEUDO_PROTOCOL = Pattern.compile(
            "vbscript\\s*:",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern DATA_HTML_PROTOCOL = Pattern.compile(
            "data\\s*:\\s*text/html",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern EVENT_HANDLER_PATTERN = Pattern.compile(
            "\\b(on[a-z]+)\\s*=",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern EMBEDDED_TAGS_PATTERN = Pattern.compile(
            "<\\s*/?\\s*(iframe|embed|object|meta|link|base)[^>]*>",
            Pattern.CASE_INSENSITIVE
    );

    private XssSanitizer() {
        // Utility class
    }

    /**
     * Checks whether the input string contains any active XSS payloads or malicious tags.
     *
     * @param input the input string to inspect
     * @return true if an XSS signature is detected; false otherwise
     */
    public static boolean containsXssPayload(String input) {
        if (input == null || input.isBlank()) {
            return false;
        }

        return SCRIPT_TAG_PATTERN.matcher(input).find()
                || LONE_SCRIPT_TAG_PATTERN.matcher(input).find()
                || JAVASCRIPT_PSEUDO_PROTOCOL.matcher(input).find()
                || VBSCRIPT_PSEUDO_PROTOCOL.matcher(input).find()
                || DATA_HTML_PROTOCOL.matcher(input).find()
                || EVENT_HANDLER_PATTERN.matcher(input).find()
                || EMBEDDED_TAGS_PATTERN.matcher(input).find();
    }

    /**
     * Strips dangerous executable script tags, event handlers, and pseudo-protocols
     * from the input string.
     *
     * @param input the raw input string
     * @return the sanitized string without executable markup
     */
    public static String stripDangerousTags(String input) {
        if (input == null) {
            return null;
        }

        String sanitized = SCRIPT_TAG_PATTERN.matcher(input).replaceAll("");
        sanitized = LONE_SCRIPT_TAG_PATTERN.matcher(sanitized).replaceAll("");
        sanitized = JAVASCRIPT_PSEUDO_PROTOCOL.matcher(sanitized).replaceAll("blocked:");
        sanitized = VBSCRIPT_PSEUDO_PROTOCOL.matcher(sanitized).replaceAll("blocked:");
        sanitized = DATA_HTML_PROTOCOL.matcher(sanitized).replaceAll("blocked:text/html");
        sanitized = EVENT_HANDLER_PATTERN.matcher(sanitized).replaceAll("");
        sanitized = EMBEDDED_TAGS_PATTERN.matcher(sanitized).replaceAll("");

        return sanitized.trim();
    }

    /**
     * Escapes HTML control characters (&, <, >, ", ') into standard HTML entities.
     *
     * @param input the input string
     * @return the HTML entity-encoded string safe for browser DOM insertion
     */
    public static String escapeHtml(String input) {
        if (input == null) {
            return null;
        }
        return HtmlUtils.htmlEscape(input);
    }

    /**
     * Complete defensive sanitization: strips dangerous executable markup and encodes
     * remaining HTML entities.
     *
     * @param input the untrusted input
     * @return safely neutralized string
     */
    public static String sanitize(String input) {
        if (input == null) {
            return null;
        }
        String stripped = stripDangerousTags(input);
        return escapeHtml(stripped);
    }
}
