package com.neomechanical.neomoderation.moderation;

import com.neomechanical.neomoderation.config.ModerationApiSettings;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles communication with the NeoMechanical 14-day trial activation service.
 *
 * <p>Enforces plugin exclusivity with User-Agent, product identity headers,
 * timestamp freshness, and timing-safe HMAC-SHA256 signatures.
 */
public final class TrialClient {
    private static final String PLUGIN_SECRET = "nm_trial_sec_2026_eval_sign";

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private static final Pattern API_KEY_PATTERN = Pattern.compile("\"apiKey\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern EXPIRES_AT_PATTERN = Pattern.compile("\"expiresAt\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern DAYS_PATTERN = Pattern.compile("\"daysRemaining\"\\s*:\\s*(\\d+)");

    public static String computeHmac(String installId, long timestampSeconds) {
        try {
            String message = (installId == null ? "" : installId.trim().toLowerCase()) + ":" + timestampSeconds;
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(PLUGIN_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);
            byte[] rawHmac = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(rawHmac.length * 2);
            for (byte b : rawHmac) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    public TrialResult activateTrial(
            ModerationApiSettings apiSettings,
            String installId,
            String platformInfo
    ) throws TrialException {
        String endpoint = trialUrl(apiSettings.endpoint());
        String body = "{\"installId\":\"" + escapeJson(installId) + "\",\"platform\":\"" + escapeJson(platformInfo) + "\"}";
        long timestamp = Instant.now().getEpochSecond();
        String hmac = computeHmac(installId, timestamp);

        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(10))
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .header("User-Agent", ClientIdentity.userAgent())
                    .header("x-plugin-product", "NeoModeration")
                    .header("x-plugin-timestamp", String.valueOf(timestamp))
                    .header("x-plugin-hmac", hmac)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
        } catch (IllegalArgumentException e) {
            throw new TrialException(TrialError.INVALID_ENDPOINT, "Invalid trial endpoint URL");
        }

        try {
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status == 201 || status == 200) {
                return parseSuccess(response.body());
            }
            if (status == 409) {
                throw new TrialException(TrialError.ALREADY_CLAIMED, "Trial has already been claimed for this server");
            }
            if (status == 429) {
                throw new TrialException(TrialError.RATE_LIMITED, "Trial rate limit reached for this network");
            }
            if (status == 403) {
                throw new TrialException(TrialError.FORBIDDEN, "Trial is only accessible to verified NeoModeration installations");
            }
            throw new TrialException(TrialError.SERVER_ERROR, "HTTP " + status);
        } catch (TrialException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TrialException(TrialError.TRANSPORT_ERROR, "Trial request interrupted");
        } catch (Exception e) {
            throw new TrialException(TrialError.TRANSPORT_ERROR, "Trial request failed");
        }
    }

    static String trialUrl(String endpoint) {
        String trimmed = endpoint == null ? "" : endpoint.trim();
        int slash = trimmed.indexOf('/', "https://".length());
        String base = slash > 0 ? trimmed.substring(0, slash) : trimmed;
        return base + "/v1/plugins/neomoderation/trial";
    }

    private static TrialResult parseSuccess(String responseBody) throws TrialException {
        Matcher keyMatcher = API_KEY_PATTERN.matcher(responseBody);
        if (!keyMatcher.find()) {
            throw new TrialException(TrialError.INVALID_RESPONSE, "Missing apiKey in trial response");
        }
        String apiKey = keyMatcher.group(1);

        Matcher expiresMatcher = EXPIRES_AT_PATTERN.matcher(responseBody);
        String expiresAt = expiresMatcher.find() ? expiresMatcher.group(1) : "14 days";

        Matcher daysMatcher = DAYS_PATTERN.matcher(responseBody);
        int days = daysMatcher.find() ? Integer.parseInt(daysMatcher.group(1)) : 14;

        return new TrialResult(apiKey, expiresAt, days);
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public enum TrialError {
        ALREADY_CLAIMED,
        RATE_LIMITED,
        FORBIDDEN,
        INVALID_ENDPOINT,
        INVALID_RESPONSE,
        TRANSPORT_ERROR,
        SERVER_ERROR
    }

    public static final class TrialException extends Exception {
        private final TrialError error;

        public TrialException(TrialError error, String message) {
            super(message);
            this.error = error;
        }

        public TrialError error() {
            return error;
        }
    }

    public record TrialResult(String apiKey, String expiresAt, int daysRemaining) {
    }
}
