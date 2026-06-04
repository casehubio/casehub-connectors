package io.casehub.connectors.mcp;

/** Sanitizes content before passing it to {@link io.casehub.connectors.ConnectorMeshBridge}. */
final class McpContentSanitizer {

    private static final int MAX_LENGTH = 500;

    private McpContentSanitizer() {}

    /**
     * Strips control characters that enable log injection and truncates to 500 chars.
     * All ASCII control characters (0x00–0x1F and DEL 0x7F) are replaced with spaces,
     * covering ESC/ANSI injection, NUL log-truncation, and newline injection in one pass.
     */
    static String sanitize(final String content) {
        if (content == null) return "";
        final StringBuilder sb = new StringBuilder(Math.min(content.length(), MAX_LENGTH));
        for (int i = 0; i < content.length() && sb.length() < MAX_LENGTH; i++) {
            final char c = content.charAt(i);
            sb.append((c < 0x20 || c == 0x7F) ? ' ' : c);
        }
        return sb.toString();
    }
}
