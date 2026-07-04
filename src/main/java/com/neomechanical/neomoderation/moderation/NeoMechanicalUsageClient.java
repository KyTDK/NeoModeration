package com.neomechanical.neomoderation.moderation;

import com.neomechanical.neomoderation.config.ModerationApiSettings;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Fetches workspace usage and rate-limit information from NeoMechanical using the
 * configured API key. The usage endpoint is derived from the moderation endpoint so a
 * single {@code endpoint} setting keeps them in sync.
 */
public final class NeoMechanicalUsageClient {
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public UsageSummary fetchUsage(ModerationApiSettings apiSettings) throws UsageException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(usageUrl(apiSettings.endpoint())))
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + apiSettings.apiKey())
                .GET()
                .build();
        try {
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new UsageException("HTTP " + response.statusCode());
            }
            return UsageSummary.parse(response.body());
        } catch (UsageException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new UsageException("interrupted");
        } catch (Exception e) {
            throw new UsageException(e.getMessage());
        }
    }

    /**
     * Derives the workspace-usage URL from the moderation endpoint by replacing its final
     * path segment (e.g. {@code /v1/events}) with {@code /v1/workspace/usage}.
     */
    static String usageUrl(String endpoint) {
        String trimmed = endpoint == null ? "" : endpoint.trim();
        int slash = trimmed.lastIndexOf('/');
        String base = slash > "https://".length() ? trimmed.substring(0, slash) : trimmed;
        return base + "/workspace/usage";
    }

    public static final class UsageException extends Exception {
        public UsageException(String message) {
            super(message);
        }
    }
}
