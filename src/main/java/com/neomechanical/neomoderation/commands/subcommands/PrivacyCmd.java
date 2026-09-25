package com.neomechanical.neomoderation.commands.subcommands;

import com.neomechanical.neomoderation.NeoModerationPlugin;
import com.neomechanical.neomoderation.commands.SubCommand;
import com.neomechanical.neomoderation.config.ModerationSettings;
import com.neomechanical.neomoderation.moderation.ModerationCoverage;
import org.bukkit.command.CommandSender;

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
    public void execute(CommandSender sender, String label, String[] args) {
        ModerationSettings settings = plugin.settings();
        ModerationCoverage coverage = ModerationCoverage.from(settings);

        plugin.messages().send(sender, "privacy.title");
        plugin.messages().send(sender, "privacy.headline", Map.of(
                "value", coverage.hasLocalChecks()
                        ? (coverage.hasCloudChecks() ? "Local + cloud" : "Local only")
                        : (coverage.hasCloudChecks() ? "Cloud only" : "No checks armed")
        ));
        plugin.messages().send(sender, "privacy.local");
        if (coverage.hasCloudChecks()) {
            String data = coverage.cloudText()
                    ? (coverage.mapArt() ? "individual chat messages and filled map images" : "individual chat messages")
                    : "filled map images";
            plugin.messages().send(sender, "privacy.cloud", Map.of(
                    "endpoint", settings.api().endpoint(), "data", data));
            plugin.messages().send(sender, "privacy.retention");
            plugin.messages().send(sender, "privacy.timeout", Map.of(
                    "connect", String.valueOf(settings.api().connectTimeoutMs()),
                    "read", String.valueOf(settings.api().readTimeoutMs()),
                    "policy", settings.failOpen() ? "fail-open (chat passes)" : "fail-closed (chat blocks)"
            ));
        } else if (settings.api().apiKey().isBlank()) {
            plugin.messages().send(sender, "privacy.no-cloud");
        } else {
            plugin.messages().send(sender, "privacy.cloud-off");
        }
        plugin.messages().send(sender, "privacy.mode", Map.of("value",
                "local " + (coverage.hasLocalChecks()
                        ? settings.mode().name().toLowerCase(java.util.Locale.ROOT) : "off")
                        + ", cloud " + (coverage.hasCloudChecks()
                        ? settings.cloudMode().name().toLowerCase(java.util.Locale.ROOT)
                        : "off")));
        plugin.messages().send(sender, "privacy.metrics");
    }
}
