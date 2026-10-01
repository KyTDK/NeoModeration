package com.neomechanical.neomoderation.commands.subcommands;

import com.neomechanical.neomoderation.NeoModerationPlugin;
import com.neomechanical.neomoderation.commands.SubCommand;
import com.neomechanical.neomoderation.moderation.CloudRecovery;
import com.neomechanical.neomoderation.moderation.TrialClient;
import com.neomechanical.neomoderation.platform.InstallIdentity;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.Map;

/**
 * Activates a 14-day evaluation trial for this Minecraft server installation.
 */
public class TrialCmd implements SubCommand {
    private static final String KEY_PATH = "moderation.api.apiKey";
    private static final String ENABLED_PATH = "moderation.enabled";

    private final NeoModerationPlugin plugin;
    private final TrialClient trialClient;

    public TrialCmd(NeoModerationPlugin plugin) {
        this(plugin, new TrialClient());
    }

    public TrialCmd(NeoModerationPlugin plugin, TrialClient trialClient) {
        this.plugin = plugin;
        this.trialClient = trialClient;
    }

    @Override
    public String getName() {
        return "trial";
    }

    @Override
    public String getDescription() {
        return "Activate a free 14-day cloud evaluation trial for this server.";
    }

    @Override
    public String getUsage() {
        return "/nmod trial";
    }

    @Override
    public List<String> getAliases() {
        return List.of();
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        String existingKey = plugin.settings().api().apiKey();
        if (existingKey != null && !existingKey.isBlank()) {
            plugin.messages().send(sender, "trial.already-configured");
            return;
        }

        plugin.messages().send(sender, "trial.requesting");

        plugin.scheduler().runAsync(() -> {
            try {
                String installId = InstallIdentity.getOrCreate(plugin);
                String platformInfo = resolvePlatformInfo();
                TrialClient.TrialResult result = trialClient.activateTrial(
                        plugin.settings().api(),
                        installId,
                        platformInfo
                );

                plugin.getConfig().set(ENABLED_PATH, true);
                plugin.getConfig().set(KEY_PATH, result.apiKey());
                plugin.saveAndReload();

                plugin.messages().send(sender, "trial.activated", Map.of(
                        "expires", result.expiresAt(),
                        "days", String.valueOf(result.daysRemaining())
                ));
            } catch (TrialClient.TrialException e) {
                switch (e.error()) {
                    case ALREADY_CLAIMED -> plugin.messages().send(sender, "trial.already-claimed", Map.of(
                            "url", CloudRecovery.BILLING_URL
                    ));
                    case RATE_LIMITED -> plugin.messages().send(sender, "trial.rate-limited");
                    default -> plugin.messages().send(sender, "trial.failed", Map.of(
                            "error", e.getMessage()
                    ));
                }
            }
        });
    }

    private static String resolvePlatformInfo() {
        try {
            if (Bukkit.getServer() != null) {
                return Bukkit.getName() + " " + Bukkit.getVersion();
            }
        } catch (Exception ignored) {
        }
        return "Minecraft";
    }
}
