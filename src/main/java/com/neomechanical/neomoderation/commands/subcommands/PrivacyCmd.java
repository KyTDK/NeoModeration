package com.neomechanical.neomoderation.commands.subcommands;

import com.neomechanical.neomoderation.NeoModerationPlugin;
import com.neomechanical.neomoderation.commands.SubCommand;
import com.neomechanical.neomoderation.config.ModerationMode;
import com.neomechanical.neomoderation.config.ModerationSettings;
import org.bukkit.command.CommandSender;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/** One-screen explanation of exactly what data stays local, what leaves, and how. */
public class PrivacyCmd implements SubCommand {
    private final NeoModerationPlugin plugin;

    public PrivacyCmd(NeoModerationPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "privacy";
    }

    @Override
    public String getDescription() {
        return "Show what data stays local and what the cloud receives.";
    }

    @Override
    public String getUsage() {
        return "/nmod privacy";
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
        ModerationSettings settings = plugin.settings();
        boolean cloud = !settings.api().apiKey().isBlank();

        plugin.messages().send(sender, "privacy.title");
        plugin.messages().send(sender, "privacy.headline", Map.of(
                "value", cloud ? "Local + cloud" : "Local only"
        ));
        plugin.messages().send(sender, "privacy.local");
        if (cloud) {
            plugin.messages().send(sender, "privacy.cloud", Map.of("endpoint", settings.api().endpoint()));
            plugin.messages().send(sender, "privacy.retention");
            plugin.messages().send(sender, "privacy.timeout", Map.of(
                    "connect", String.valueOf(settings.api().connectTimeoutMs()),
                    "read", String.valueOf(settings.api().readTimeoutMs()),
                    "policy", settings.failOpen() ? "fail-open (chat passes)" : "fail-closed (chat blocks)"
            ));
        } else {
            plugin.messages().send(sender, "privacy.no-cloud");
        }
        plugin.messages().send(sender, "privacy.mode", Map.of(
                "value", settings.mode() == ModerationMode.MONITOR ? "monitor (observe only)" : "enforce"
        ));
        plugin.messages().send(sender, "privacy.metrics");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        return Collections.emptyList();
    }
}
