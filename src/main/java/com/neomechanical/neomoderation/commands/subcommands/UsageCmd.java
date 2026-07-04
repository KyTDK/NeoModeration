package com.neomechanical.neomoderation.commands.subcommands;

import com.neomechanical.neomoderation.NeoModerationPlugin;
import com.neomechanical.neomoderation.commands.SubCommand;
import com.neomechanical.neomoderation.moderation.NeoMechanicalUsageClient;
import com.neomechanical.neomoderation.moderation.UsageSummary;
import org.bukkit.command.CommandSender;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class UsageCmd implements SubCommand {
    private final NeoModerationPlugin plugin;
    private final NeoMechanicalUsageClient usageClient = new NeoMechanicalUsageClient();

    public UsageCmd(NeoModerationPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "usage";
    }

    @Override
    public String getDescription() {
        return "Check API usage and rate limits.";
    }

    @Override
    public String getUsage() {
        return "/nmod usage";
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
        if (plugin.settings().api().apiKey().isBlank()) {
            plugin.messages().send(sender, "error.no-api-key");
            return;
        }

        plugin.messages().send(sender, "usage.fetching");

        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    UsageSummary summary = usageClient.fetchUsage(plugin.settings().api());
                    runSync(() -> render(sender, summary));
                } catch (NeoMechanicalUsageClient.UsageException e) {
                    runSync(() -> plugin.messages().send(sender, "usage.error"));
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    private void render(CommandSender sender, UsageSummary s) {
        plugin.messages().send(sender, "usage.title");
        plugin.messages().send(sender, "usage.workspace", Map.of("value", s.workspace()));
        plugin.messages().send(sender, "usage.plan", Map.of(
                "value", s.tier(),
                "rpm", String.valueOf(s.requestsPerMinuteLimit())
        ));
        plugin.messages().send(sender, "usage.credits", Map.of("value", String.valueOf(s.creditsRemaining())));
        plugin.messages().send(sender, "usage.requests", Map.of(
                "today", String.valueOf(s.requestsToday()),
                "week", String.valueOf(s.requestsLast7Days())
        ));
        plugin.messages().send(sender, "usage.nsfw", Map.of(
                "images", nsfw(s.nsfwImagesRemaining(), s.nsfwImagesCap()),
                "text", nsfw(s.nsfwTextUnitsRemaining(), s.nsfwTextUnitsCap()),
                "videos", nsfw(s.nsfwVideosRemaining(), s.nsfwVideosCap())
        ));
    }

    private static String nsfw(long remaining, long cap) {
        return remaining + "/" + cap;
    }

    private void runSync(Runnable runnable) {
        new BukkitRunnable() {
            @Override
            public void run() {
                runnable.run();
            }
        }.runTask(plugin);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        return Collections.emptyList();
    }
}
