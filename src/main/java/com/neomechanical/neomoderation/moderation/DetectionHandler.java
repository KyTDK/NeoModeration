package com.neomechanical.neomoderation.moderation;

import com.neomechanical.neomoderation.NeoModerationPlugin;
import com.neomechanical.neomoderation.config.ModerationAction;
import com.neomechanical.neomoderation.config.ModerationMode;
import com.neomechanical.neomoderation.config.ModerationSettings;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;

/**
 * The single end-to-end path for every detection, on every surface: counts it,
 * records a strike, executes actions/escalation (enforce only), alerts staff,
 * logs to console, and appends the case row. Callers pass the disposition they
 * want (ALLOW = alert-only, CENSOR, BLOCK); global monitor mode always
 * downgrades to alert-only.
 */
public final class DetectionHandler {
    public enum Disposition {
        ALLOW,
        CENSOR,
        BLOCK
    }

    private final NeoModerationPlugin plugin;
    private final ChatModerationActionExecutor actionExecutor;
    private final MonitorStats monitorStats;
    private final DetectionNotifier notifier;
    private final StrikeService strikes;

    public DetectionHandler(
            NeoModerationPlugin plugin,
            ChatModerationActionExecutor actionExecutor,
            MonitorStats monitorStats,
            DetectionNotifier notifier,
            StrikeService strikes
    ) {
        this.plugin = plugin;
        this.actionExecutor = actionExecutor;
        this.monitorStats = monitorStats;
        this.notifier = notifier;
        this.strikes = strikes;
    }

    /** Safe from any thread. Returns the disposition the caller must apply. */
    public Disposition handle(Player player, String surface, String reason, String message, Disposition requested) {
        ModerationSettings settings = plugin.settings();
        monitorStats.record(reason);

        boolean alertOnly = settings.mode() == ModerationMode.MONITOR || requested == Disposition.ALLOW;
        int strikeCount = 0;
        Optional<ModerationAction> escalation = Optional.empty();
        if (!alertOnly) {
            StrikeService.Result result =
                    strikes.recordStrike(player.getUniqueId(), settings.strikes(), System.currentTimeMillis());
            strikeCount = result.strikes();
            escalation = result.escalation();
        }
        Disposition effective = alertOnly ? Disposition.ALLOW : requested;
        String actionsText = actionsText(settings, effective, strikeCount, escalation);

        Optional<ModerationAction> escalationAction = escalation;
        plugin.runSync(() -> {
            if (effective == Disposition.BLOCK) {
                actionExecutor.execute(player, settings.actions());
            }
            escalationAction.ifPresent(action -> actionExecutor.execute(player, List.of(action)));
            notifier.notifyDetection(player, surface, reason, message, settings, actionsText, alertOnly);
            plugin.getLogger().info((alertOnly ? "MONITOR: " : "")
                    + surface + " content from " + player.getName()
                    + " flagged via " + reason + " -> " + actionsText);
        });
        recordCase(player, surface, reason, message, settings, actionsText, alertOnly);
        return effective;
    }

    private static String actionsText(
            ModerationSettings settings,
            Disposition effective,
            int strikeCount,
            Optional<ModerationAction> escalation
    ) {
        String escalationSuffix = escalation
                .map(action -> " + " + ModerationAction.describe(List.of(action)))
                .orElse("");
        String strikeSuffix = strikeCount > 0 ? " (strike " + strikeCount + ")" : "";
        return switch (effective) {
            case ALLOW -> ModerationAction.describe(settings.actions());
            case CENSOR -> "censored" + escalationSuffix + strikeSuffix;
            case BLOCK -> ModerationAction.describe(settings.actions()) + escalationSuffix + strikeSuffix;
        };
    }

    private void recordCase(Player player, String surface, String reason, String message,
                            ModerationSettings settings, String actionsText, boolean alertOnly) {
        if (!settings.cases().enabled()) {
            return;
        }
        long now = System.currentTimeMillis();
        String uuid = player.getUniqueId().toString();
        String name = player.getName();
        String preview = settings.cases().storeContent() ? DetectionNotifier.preview(message) : "";
        String mode = alertOnly ? "monitor" : "enforce";
        plugin.runAsync(() -> plugin.caseLog().record(now, uuid, name, surface, reason, actionsText, mode, preview));
    }
}
