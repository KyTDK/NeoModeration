package com.neomechanical.neomoderation.commands.subcommands;

import com.neomechanical.neomoderation.NeoModerationPlugin;
import com.neomechanical.neomoderation.commands.SubCommand;
import com.neomechanical.neomoderation.moderation.CloudRecovery;
import com.neomechanical.neomoderation.moderation.TrialClient;
import com.neomechanical.neomoderation.platform.InstallIdentity;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

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
        return "/nmod trial [status]";
    }

    @Override
    public List<String> getAliases() {
        return List.of();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        if (args.length == 2) {
            return SubCommand.filterPrefix(args[1], "status");
        }
        return List.of();
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        String existingKey = plugin.settings().api().apiKey();
        if (args.length >= 2 && "status".equalsIgnoreCase(args[1])) {
            executeStatus(sender, existingKey);
            return;
        }

        if (existingKey != null && !existingKey.isBlank()) {
            plugin.messages().send(sender, "trial.already-configured");
            return;
        }
        if (!plugin.tryStartTrialActivation()) {
            plugin.messages().send(sender, "trial.pending");
            return;
        }

        plugin.messages().send(sender, "trial.requesting");

        var api = plugin.settings().api();
        String installId = InstallIdentity.getOrCreate(plugin);
        String platformInfo = resolvePlatformInfo();
        plugin.scheduler().runAsync(() -> {
            try {
                TrialClient.TrialResult result = trialClient.activateTrial(
                        api,
                        installId,
                        platformInfo
                );

                plugin.runSync(() -> {
                    try {
                        if (!plugin.settings().api().apiKey().isBlank()
                                || !plugin.settings().api().endpoint().equals(api.endpoint())) {
                            reply(sender, () -> plugin.messages().send(sender, "trial.configuration-changed"));
                            return;
                        }
                        plugin.getConfig().set(ENABLED_PATH, true);
                        plugin.getConfig().set("moderation.cloudMode", "monitor");
                        plugin.getConfig().set(KEY_PATH, result.apiKey());
                        plugin.saveAndReload();
                        reply(sender, () -> plugin.messages().send(sender, "trial.activated", Map.of(
                                "expires", result.expiresAt(),
                                "days", String.valueOf(result.daysRemaining()),
                                "url", result.claimUrl()
                        )));
                    } finally {
                        plugin.finishTrialActivation();
                    }
                });
            } catch (TrialClient.TrialException e) {
                plugin.finishTrialActivation();
                reply(sender, () -> {
                    switch (e.error()) {
                    case ALREADY_CLAIMED -> plugin.messages().send(sender, "trial.already-claimed", Map.of(
                            "url", CloudRecovery.BILLING_URL
                    ));
                    case RATE_LIMITED -> plugin.messages().send(sender, "trial.rate-limited");
                    default -> plugin.messages().send(sender, "trial.failed", Map.of(
                            "error", e.getMessage()
                    ));
                    }
                });
            } catch (RuntimeException e) {
                plugin.finishTrialActivation();
                throw e;
            }
        });
    }

    private void executeStatus(CommandSender sender, String existingKey) {
        if (existingKey == null || existingKey.isBlank()) {
            plugin.messages().send(sender, "trial.status-no-key", Map.of(
                    "url", CloudRecovery.SIGNUP_URL
            ));
            return;
        }

        plugin.messages().send(sender, "trial.status-checking");
        var api = plugin.settings().api();
        plugin.scheduler().runAsync(() -> {
            try {
                TrialClient.TrialStatusResult status = trialClient.fetchTrialStatus(api);
                reply(sender, () -> {
                if (!status.isTrial()) {
                    plugin.messages().send(sender, "trial.status-standard");
                } else if (status.isExpired()) {
                    plugin.messages().send(sender, "trial.status-expired", Map.of(
                            "expires", status.expiresAt(),
                            "url", status.upgradeUrl()
                    ));
                } else {
                    plugin.messages().send(sender, "trial.status-active", Map.of(
                            "days", String.valueOf(status.daysRemaining()),
                            "expires", status.expiresAt(),
                            "url", status.upgradeUrl()
                    ));
                }
                });
            } catch (TrialClient.TrialException e) {
                reply(sender, () -> plugin.messages().send(sender, "trial.failed", Map.of(
                        "error", e.getMessage()
                )));
            }
        });
    }

    private void reply(CommandSender sender, Runnable task) {
        if (sender instanceof Player player) {
            plugin.runForEntity(player, task);
        } else {
            plugin.runSync(task);
        }
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
