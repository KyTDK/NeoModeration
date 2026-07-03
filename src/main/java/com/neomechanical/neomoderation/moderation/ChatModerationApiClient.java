package com.neomechanical.neomoderation.moderation;

import com.neomechanical.neomoderation.config.ModerationApiSettings;
import com.neomechanical.neomoderation.config.ModerationCategorySettings;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public final class ChatModerationApiClient {
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .build();

    public ModerationApiResult moderate(
            String playerName,
            String playerUuid,
            String message,
            ModerationApiSettings apiSettings,
            ModerationCategorySettings categorySettings
    ) {
        long connectMs = Math.max(1, apiSettings.connectTimeoutMs());
        long readMs = Math.max(1, apiSettings.readTimeoutMs());
        long totalMs = Math.min(60_000L, connectMs + readMs + 500L);
        String body = ChatModerationPayloadBuilder.build(playerName, playerUuid, message, categorySettings);

        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(URI.create(apiSettings.endpoint()))
                    .timeout(Duration.ofMillis(totalMs))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiSettings.apiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
        } catch (IllegalArgumentException e) {
            return ModerationApiResult.transientTransport();
        }

        try {
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int status = response.statusCode();
            if (status == 401 || status == 403) {
                return ModerationApiResult.clientAuth();
            }
            if (status == 429 || status >= 500) {
                return ModerationApiResult.transientTransport();
            }
            if (status >= 400) {
                return ModerationApiResult.clientAuth();
            }
            if (status < 200 || status >= 300) {
                return ModerationApiResult.transientTransport();
            }
            return ChatModerationResponseParser.matchesPositiveSignal(response.body(), categorySettings)
                    ? ModerationApiResult.flagged()
                    : ModerationApiResult.clear();
        } catch (IOException e) {
            return ModerationApiResult.transientTransport();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ModerationApiResult.transientTransport();
        }
    }
}
