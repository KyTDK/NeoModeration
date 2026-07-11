package com.neomechanical.neomoderation.moderation;

import com.neomechanical.neomoderation.config.ModerationApiSettings;
import com.neomechanical.neomoderation.config.ModerationCategorySettings;
import com.neomechanical.neomoderation.config.MapArtSettings;
import com.neomechanical.neomoderation.config.ModerationMode;
import com.neomechanical.neomoderation.config.ModerationSettings;
import com.neomechanical.neomoderation.config.OfflineModerationSettings;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChatModerationCoordinatorTest {
    @Test
    void transientFailureFollowsFailOpenSetting() throws Exception {
        assertFalse(decisionFor(503, "unavailable", true));
        assertTrue(decisionFor(503, "unavailable", false));
    }

    @Test
    void authenticationFailureFollowsFailOpenSetting() throws Exception {
        assertFalse(decisionFor(401, "invalid key", true));
        assertTrue(decisionFor(401, "invalid key", false));
    }

    @Test
    void openCircuitFollowsFailOpenSetting() {
        ModerationCircuitBreaker breaker = new ModerationCircuitBreaker(Logger.getLogger("test"));
        breaker.record(ModerationApiResult.transientTransport());
        breaker.record(ModerationApiResult.transientTransport());
        breaker.record(ModerationApiResult.transientTransport());

        try (ChatModerationCoordinator coordinator = new ChatModerationCoordinator(
                breaker, new ModerationApiClient())) {
            assertTrue(coordinator.isMessageFlagged(
                    player(), "hello", settings("http://127.0.0.1:1", false)));
        }
    }

    @Test
    void clearAndFlaggedResponsesKeepTheirMeaning() throws Exception {
        assertFalse(decisionFor(200, "{}", false));
        assertTrue(decisionFor(200, "{\"status\":\"blocked\"}", true));
    }

    private boolean decisionFor(int status, String body, boolean failOpen) throws Exception {
        HttpServer server = localServer(status, body);
        try (ChatModerationCoordinator coordinator = new ChatModerationCoordinator(
                new ModerationCircuitBreaker(Logger.getLogger("test")), new ModerationApiClient())) {
            String endpoint = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1/events";
            return coordinator.isMessageFlagged(player(), "hello", settings(endpoint, failOpen));
        } finally {
            server.stop(0);
        }
    }

    private HttpServer localServer(int status, String body) throws IOException {
        byte[] response = body.getBytes(StandardCharsets.UTF_8);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/events", exchange -> {
            exchange.sendResponseHeaders(status, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        return server;
    }

    private Player player() {
        Player player = mock(Player.class);
        when(player.getName()).thenReturn("Tester");
        when(player.getUniqueId()).thenReturn(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        return player;
    }

    private ModerationSettings settings(String endpoint, boolean failOpen) {
        return new ModerationSettings(
                true,
                ModerationMode.ENFORCE,
                new ModerationApiSettings(endpoint, "test-key", 100, 100),
                new OfflineModerationSettings(true, false, true, List.of(), List.of(), List.of(), List.of()),
                new ModerationCategorySettings(Map.of()),
                new MapArtSettings(true, true, true, true, 1000),
                List.of(),
                true,
                failOpen,
                new ModerationSettings.AlertSettings(true, true)
        );
    }
}
