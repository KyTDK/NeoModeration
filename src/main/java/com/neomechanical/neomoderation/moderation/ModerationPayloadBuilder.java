package com.neomechanical.neomoderation.moderation;

import com.neomechanical.neomoderation.config.ModerationCategorySettings;

import java.util.Map;

public final class ModerationPayloadBuilder {
    private static final String ADAPTER = "neomoderation";
    private static final String SOURCE = "minecraft";

    private ModerationPayloadBuilder() {
    }

    public static String buildText(
            String playerName,
            String playerUuid,
            String message,
            ModerationCategorySettings categorySettings
    ) {
        return buildPayload(playerName, playerUuid, message, null, categorySettings);
    }

    public static String buildImage(
            String playerName,
            String playerUuid,
            String base64Image,
            ModerationCategorySettings categorySettings
    ) {
        return buildPayload(playerName, playerUuid, "", base64Image, categorySettings);
    }

    private static String buildPayload(
            String playerName,
            String playerUuid,
            String text,
            String base64Image,
            ModerationCategorySettings categorySettings
    ) {
        StringBuilder thresholds = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, Double> entry : categorySettings.thresholds().entrySet()) {
            if (!first) {
                thresholds.append(',');
            }
            first = false;
            thresholds.append('"')
                    .append(escapeJsonString(platformKey(entry.getKey())))
                    .append("\":")
                    .append((double) entry.getValue());
        }

        String escapedPlayerName = escapeJsonString(playerName);
        String eventType = base64Image != null ? "map_art" : "chat_message";
        String attachmentsStr = base64Image != null
                ? "[{\"kind\":\"image\",\"inlineDataBase64\":\"" + base64Image + "\"}]"
                : "[]";

        return "{"
                + "\"mode\":\"sync\","
                + "\"event\":{"
                + "\"source\":\"" + SOURCE + "\","
                + "\"adapter\":\"" + ADAPTER + "\","
                + "\"eventType\":\"" + eventType + "\","
                + "\"actor\":{"
                + "\"externalId\":\"" + escapeJsonString(playerUuid) + "\","
                + "\"username\":\"" + escapedPlayerName + "\","
                + "\"displayName\":\"" + escapedPlayerName + "\""
                + "},"
                + "\"context\":{"
                + "\"scopeType\":\"minecraft_server\","
                + "\"tags\":[\"chat\"]"
                + "},"
                + "\"content\":{"
                + "\"text\":\"" + escapeJsonString(text) + "\","
                + "\"attachments\":" + attachmentsStr
                + "},"
                + "\"metadata\":{"
                + "\"platformPolicy\":{"
                + "\"thresholds\":{" + thresholds + "}"
                + "},"
                + "\"customInfo\":{"
                + "\"plugin\":\"NeoModeration\""
                + "}"
                + "}"
                + "},"
                + "\"options\":{"
                + "\"persistence\":\"no_store\","
                + "\"includeAnalysisDetails\":false,"
                + "\"learning\":{\"enabled\":false,\"mode\":\"off\"}"
                + "}"
                + "}";
    }

    private static String platformKey(String configKey) {
        return switch (configKey) {
            case "selfHarm" -> "self-harm";
            default -> configKey;
        };
    }

    public static String escapeJsonString(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder escaped = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            switch (character) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (character < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) character));
                    } else {
                        escaped.append(character);
                    }
                }
            }
        }
        return escaped.toString();
    }
}
