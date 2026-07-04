package com.neomechanical.neomoderation.commands.subcommands;

import com.neomechanical.neomoderation.NeoModerationPlugin;
import com.neomechanical.neomoderation.commands.SubCommand;
import org.bukkit.command.CommandSender;

import java.util.Collections;
import java.util.List;

public class DisableCmd implements SubCommand {
    private final NeoModerationPlugin plugin;
    private static final String ENABLED_PATH = "moderation.enabled";

    public DisableCmd(NeoModerationPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "off";
    }

    @Override
    public String getDescription() {
        return "Disable NeoModeration checks.";
    }

    @Override
    public String getUsage() {
        return "/nmod off";
    }

    @Override
    public String getPermission() {
        return "neomoderation.admin";
    }

    @Override
    public List<String> getAliases() {
        return List.of("disable");
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        plugin.getConfig().set(ENABLED_PATH, false);
        plugin.saveConfig();
        plugin.reloadModerationConfig();
        plugin.messages().send(sender, "toggle.off");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        return Collections.emptyList();
    }
}
