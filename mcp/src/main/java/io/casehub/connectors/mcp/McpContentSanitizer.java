package io.casehub.connectors.mcp;

/** Sanitizes content before passing it to {@link io.casehub.connectors.ConnectorMeshBridge}. */
final class McpContentSanitizer {

    private static final int MAX_LENGTH = 500;

    private McpContentSanitizer() {}

    /**
     * Strips control characters that enable log injection and truncates to 500 chars.
     * Newlines, carriage returns, and tabs are replaced with spaces.
     */
    static String sanitize(final String content) {
        if (content == null) return "";
        final String stripped = content
                .replace('\n', ' ')
                .replace('\r', ' ')
                .replace('\t', ' ');
        return stripped.length() > MAX_LENGTH ? stripped.substring(0, MAX_LENGTH) : stripped;
    }
}
