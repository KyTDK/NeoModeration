package com.neomechanical.neomoderation.commands.subcommands;

import com.neomechanical.neomoderation.NeoModerationPlugin;
import com.neomechanical.neomoderation.commands.SubCommand;
import com.neomechanical.neomoderation.config.ModerationAction;
import com.neomechanical.neomoderation.config.ModerationSettings;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.neomechanical.neomoderation.commands.DurationParser;

public class StatusCmd implements SubCommand {
    private final NeoModerationPlugin plugin;

    public StatusCmd(NeoModerationPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "status";
    }

    @Override
    public String getDescription() {
        return "View moderation settings and status.";
    }

    @Override
    public String getUsage() {
        return "/nmod status";
    }

    @Override
    public String getPermission() {
        return "neomoderation.admin";
    }

    @Override
    public List<String> getAliases() {
        return List.of("info");
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        ModerationSettings settings = plugin.settings();
        boolean hasKey = !settings.api().apiKey().isBlank();
        plugin.messages().send(sender, "status.title");
        plugin.messages().send(sender, "status.enabled", Map.of(
                "value", settings.enabled() ? "ON" : "OFF"
        ));
        plugin.messages().send(sender, "status.cloud", Map.of(
                "value", hasKey ? "yes" : "no (local rules only)"
        ));
        plugin.messages().send(sender, "status.rules", Map.of(
                "words", String.valueOf(settings.offline().bannedWords().size()),
                "urls", String.valueOf(settings.offline().bannedUrls().size())
        ));
        plugin.messages().send(sender, "status.actions", Map.of(
                "value", formatActions(settings.actions())
        ));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        return Collections.emptyList();
    }

    private String formatActions(List<ModerationAction> actions) {
        if (actions.isEmpty()) {
            return "(none)";
        }
        List<String> parts = new ArrayList<>();
        for (ModerationAction action : actions) {
            parts.add(describeAction(action));
        }
        return String.join(", ", parts);
    }

    private String describeAction(ModerationAction action) {
        return switch (action.type()) {
            case CLEAR_CHAT -> "clear";
            case MUTE -> "mute " + DurationParser.format(action.durationSeconds());
            case KICK -> "kick";
            case BAN -> "ban";
            default -> action.type().name().toLowerCase(Locale.ROOT);
        };
    }
}
