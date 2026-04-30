package io.casehub.connectors.http;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HexFormat;
import java.util.logging.Logger;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Shared HTTP utilities for connector implementations.
 *
 * <p>
 * Uses {@link java.net.http.HttpClient} — no external HTTP client dependency.
 */
public final class HttpHelper {

    private static final Logger LOG = Logger.getLogger(HttpHelper.class.getName());

    /** Shared client — thread-safe, reused across all connectors. */
    public static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private HttpHelper() {}

    /**
     * POST JSON to a URL. Logs and returns {@code false} on non-2xx or exception.
     *
     * @param url     the target URL
     * @param json    the JSON body
     * @param headers alternating header name/value pairs
     * @return {@code true} if the server returned a 2xx status
     */
    public static boolean postJson(final String url, final String json, final String... headers) {
        try {
            final HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8));

            for (int i = 0; i + 1 < headers.length; i += 2) {
                builder.header(headers[i], headers[i + 1]);
            }

            final HttpResponse<String> response = CLIENT.send(
                    builder.build(), HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                LOG.warning("HTTP connector POST failed: " + url
                        + " status=" + response.statusCode() + " body=" + response.body());
                return false;
            }
            return true;
        } catch (final Exception e) {
            LOG.warning("HTTP connector POST error: " + url + " — " + e.getMessage());
            return false;
        }
    }

    /**
     * Compute HMAC-SHA256 of {@code payload} with {@code secret}.
     *
     * @return {@code "sha256=" + hexDigest}
     */
    public static String hmacSha256Hex(final String payload, final String secret) {
        try {
            final Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return "sha256=" + HexFormat.of().formatHex(
                    mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (final Exception e) {
            throw new IllegalStateException("HMAC-SHA256 failed", e);
        }
    }

    /**
     * Escape a string for inclusion in a JSON string value.
     * Handles backslash, double-quote, and basic control characters.
     */
    public static String jsonEscape(final String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /** Wrap a value in JSON double-quotes, or return {@code null} if value is null. */
    public static String jsonQuote(final String value) {
        return value == null ? "null" : "\"" + jsonEscape(value) + "\"";
    }
}
