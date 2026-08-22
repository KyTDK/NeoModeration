package com.neomechanical.neomoderation.moderation;

import com.neomechanical.neomoderation.NeoModerationPlugin;
import com.neomechanical.neomoderation.config.ModerationSettings;
import org.bukkit.entity.Player;

import java.util.Map;

/**
 * Sends in-game detection alerts to staff holding {@code neomoderation.notify}.
 * Console logging stays with the caller so alerts and log lines are never doubled.
 */
public final class DetectionNotifier {
    public static final String NOTIFY_PERMISSION = "neomoderation.notify";
    private static final int MAX_MESSAGE_PREVIEW = 60;

    private final NeoModerationPlugin plugin;

    public DetectionNotifier(NeoModerationPlugin plugin) {
        this.plugin = plugin;
    }

    /** Must run on the main server thread. */
    public void notifyDetection(Player offender, String surface, String reason, String message,
                                ModerationSettings settings, String actionsText, boolean monitorStyle) {
        if (!settings.alerts().enabled()) {
            return;
        }
        String key = monitorStyle ? "alert.monitor" : "alert.enforce";
        String line = plugin.messages().format(key, Map.of(
                "player", offender.getName(),
                "surface", surface,
                "reason", reason,
                "message", settings.alerts().includeMessage() ? preview(message) : "(hidden)",
                "actions", actionsText
        ));
        for (Player online : plugin.getServer().getOnlinePlayers()) {
            if (online.hasPermission(NOTIFY_PERMISSION)) {
                online.sendMessage(line);
            }
        }
    }

    static String preview(String message) {
        if (message == null || message.isBlank()) {
            return "(no text)";
        }
        String flattened = message.replace('\n', ' ').replace('\r', ' ').trim();
        return flattened.length() <= MAX_MESSAGE_PREVIEW
                ? flattened
                : flattened.substring(0, MAX_MESSAGE_PREVIEW) + "...";
    }
}
