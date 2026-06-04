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
        // \r\n is two control chars; each becomes a space
        assertThat(McpContentSanitizer.sanitize("line1\nline2\r\nline3"))
                .isEqualTo("line1 line2  line3");
    }

    @Test
    void sanitize_stripsControlCharacters() {
        // ESC (0x1B, ANSI injection), NUL (0x00, log truncation), BEL (0x07, terminal bell)
        final String input = "normal\u001Bmalicious text\u0000\u0007end";
        assertThat(McpContentSanitizer.sanitize(input))
                .isEqualTo("normal malicious text  end");
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
