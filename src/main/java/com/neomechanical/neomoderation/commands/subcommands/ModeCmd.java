package com.neomechanical.neomoderation.commands.subcommands;

import com.neomechanical.neomoderation.NeoModerationPlugin;
import com.neomechanical.neomoderation.commands.ModeSubCommand;
import com.neomechanical.neomoderation.config.ModerationMode;
import org.bukkit.command.CommandSender;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

public class ModeCmd extends ModeSubCommand {
    public ModeCmd(NeoModerationPlugin plugin) {
        super(plugin, "moderation.mode", "mode");
    }

    @Override
    public String getName() {
        return "mode";
    }

    @Override
    public String getDescription() {
        return "Switch between monitor (observe only) and enforce.";
    }

    @Override
    public String getUsage() {
        return "/nmod mode [monitor|enforce]";
    }

    @Override
    protected void showCurrent(CommandSender sender) {
        boolean monitor = plugin.settings().mode() == ModerationMode.MONITOR;
        plugin.messages().send(sender, "mode.current", Map.of(
                "value", monitor ? "MONITOR (observe only)" : "ENFORCE"
        ));
        plugin.messages().send(sender, "mode.stats", Map.of(
                "total", String.valueOf(plugin.monitorStats().total()),
                "hours", String.valueOf(hoursSince(plugin.monitorStats().since()))
        ));
        for (Map.Entry<String, Long> entry : plugin.monitorStats().byReason().entrySet()) {
            plugin.messages().send(sender, "mode.stats-line", Map.of(
                    "reason", entry.getKey(),
                    "count", String.valueOf(entry.getValue())
            ));
        }
    }

    private static long hoursSince(Instant since) {
        return Math.max(0, Duration.between(since, Instant.now()).toHours());
    }
}
