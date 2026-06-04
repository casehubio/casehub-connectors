package io.casehub.connectors.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class McpContentSanitizerTest {

    @Test
    void sanitize_null_returnsEmpty() {
        assertThat(McpContentSanitizer.sanitize(null)).isEmpty();
    }

    @Test
    void sanitize_stripsNewlines() {
        assertThat(McpContentSanitizer.sanitize("line1\nline2\r\nline3"))
                .isEqualTo("line1 line2  line3");
    }

    @Test
    void sanitize_stripsTabs() {
        assertThat(McpContentSanitizer.sanitize("col1\tcol2")).isEqualTo("col1 col2");
    }

    @Test
    void sanitize_truncatesAt500() {
        String input = "x".repeat(600);
        assertThat(McpContentSanitizer.sanitize(input)).hasSize(500);
    }

    @Test
    void sanitize_under500_noTruncation() {
        assertThat(McpContentSanitizer.sanitize("hello")).isEqualTo("hello");
    }
}
