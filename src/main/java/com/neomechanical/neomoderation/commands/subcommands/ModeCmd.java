package com.neomechanical.neomoderation.commands.subcommands;

import com.neomechanical.neomoderation.NeoModerationPlugin;
import com.neomechanical.neomoderation.commands.SubCommand;
import com.neomechanical.neomoderation.config.ModerationMode;
import org.bukkit.command.CommandSender;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

public class ModeCmd implements SubCommand {
    private final NeoModerationPlugin plugin;

    public ModeCmd(NeoModerationPlugin plugin) {
        this.plugin = plugin;
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
    public String getPermission() {
        return "neomoderation.admin";
    }

    @Override
    public List<String> getAliases() {
        return Collections.emptyList();
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        if (args.length < 2) {
            showStatus(sender);
            return;
        }
        String requested = args[1].toLowerCase(Locale.ROOT);
        if (!"monitor".equals(requested) && !"enforce".equals(requested)) {
            plugin.messages().send(sender, "mode.usage", Map.of("label", label));
            return;
        }
        plugin.getConfig().set("moderation.mode", requested);
        plugin.saveConfig();
        plugin.reloadModerationConfig();
        plugin.messages().send(sender, "monitor".equals(requested) ? "mode.set-monitor" : "mode.set-enforce");
    }

    private void showStatus(CommandSender sender) {
        boolean monitor = plugin.settings().mode() == ModerationMode.MONITOR;
        plugin.messages().send(sender, "mode.current", Map.of(
                "value", monitor ? "MONITOR (observe only)" : "ENFORCE"
        ));
        long total = plugin.monitorStats().total();
        plugin.messages().send(sender, "mode.stats", Map.of(
                "total", String.valueOf(total),
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

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        if (args.length == 2) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            return Stream.of("monitor", "enforce").filter(v -> v.startsWith(prefix)).toList();
        }
        return Collections.emptyList();
    }
}
