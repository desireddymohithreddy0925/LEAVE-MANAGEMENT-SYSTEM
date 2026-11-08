package com.leave_management_system.leave_management_system.security.sanitization;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("OWASP A03: XSS Sanitizer & Injection Defense Unit Tests")
class XssSanitizerTest {

    @Test
    @DisplayName("Should return null or empty when input is null or empty")
    void testNullAndEmptyInputs() {
        assertNull(XssSanitizer.sanitize(null));
        assertNull(XssSanitizer.escapeHtml(null));
        assertNull(XssSanitizer.stripDangerousTags(null));
        assertFalse(XssSanitizer.containsXssPayload(null));
        assertFalse(XssSanitizer.containsXssPayload(""));
        assertFalse(XssSanitizer.containsXssPayload("   "));
    }

    @Test
    @DisplayName("Should pass safe alphanumeric and punctuation text untouched")
    void testSafeTextPassesUntouched() {
        String safe = "Taking annual vacation with family from Monday to Friday.";
        assertEquals(safe, XssSanitizer.stripDangerousTags(safe));
        assertEquals(safe, XssSanitizer.escapeHtml(safe));
        assertEquals(safe, XssSanitizer.sanitize(safe));
        assertFalse(XssSanitizer.containsXssPayload(safe));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "<script>alert('XSS')</script>",
            "<SCRIPT SRC=\"http://malicious.com/xss.js\"></SCRIPT>",
            "<script type=\"text/javascript\">document.cookie;</script>",
            "<script\n>alert(1)</script>",
            "<img src=\"x\" onerror=\"alert('XSS')\" />",
            "<svg onload=\"alert('XSS')\" />",
            "<body onload=\"alert('XSS')\">",
            "<a href=\"javascript:alert(1)\">click me</a>",
            "<iframe src=\"http://attacker.com\"></iframe>",
            "<embed src=\"malicious.swf\"></embed>",
            "<object data=\"malicious.class\"></object>"
    })
    @DisplayName("Should accurately detect dangerous XSS payloads")
    void testDetectsXssPayloads(String payload) {
        assertTrue(XssSanitizer.containsXssPayload(payload),
                "Should have detected XSS payload: " + payload);
    }

    @Test
    @DisplayName("Should strip script tags and preserve legitimate text")
    void testStripScriptTags() {
        String input = "Vacation reason: <script>alert('pwned')</script>Trip to Hawaii";
        String stripped = XssSanitizer.stripDangerousTags(input);
        assertEquals("Vacation reason: Trip to Hawaii", stripped);
        assertFalse(stripped.toLowerCase().contains("<script"));
    }

    @Test
    @DisplayName("Should neutralize event handlers in HTML tags")
    void testNeutralizeEventHandlers() {
        String input = "<img src=\"avatar.png\" onerror=\"alert('xss')\" alt=\"profile\">";
        String stripped = XssSanitizer.stripDangerousTags(input);
        assertFalse(stripped.toLowerCase().contains("onerror"));
        assertFalse(XssSanitizer.containsXssPayload(stripped));
    }

    @Test
    @DisplayName("Should block javascript: pseudo-protocols")
    void testBlockJavascriptPseudoProtocol() {
        String input = "<a href=\"javascript:evil()\">link</a>";
        String stripped = XssSanitizer.stripDangerousTags(input);
        assertFalse(stripped.toLowerCase().contains("javascript:"));
        assertTrue(stripped.contains("blocked:"));
    }

    @Test
    @DisplayName("Should strip iframe, embed, and object tags")
    void testStripEmbeddedTags() {
        String input = "Welcome <iframe src=\"http://malicious.com\"></iframe> and <object data=\"test\"></object>";
        String stripped = XssSanitizer.stripDangerousTags(input);
        assertFalse(stripped.toLowerCase().contains("<iframe"));
        assertFalse(stripped.toLowerCase().contains("<object"));
    }

    @Test
    @DisplayName("Should escape HTML special characters into entities")
    void testEscapeHtml() {
        String input = "<b>Bold</b> & \"Quoted\" & 'Single'";
        String escaped = XssSanitizer.escapeHtml(input);
        assertEquals("&lt;b&gt;Bold&lt;/b&gt; &amp; &quot;Quoted&quot; &amp; &#39;Single&#39;", escaped);
    }

    @Test
    @DisplayName("Sanitize should strip dangerous tags and escape remaining entities")
    void testSanitizeCombined() {
        String input = "Sick leave <script>alert(1)</script> & recovering at home";
        String sanitized = XssSanitizer.sanitize(input);
        assertEquals("Sick leave  &amp; recovering at home", sanitized);
        assertFalse(XssSanitizer.containsXssPayload(sanitized));
    }
}
