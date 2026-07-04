package com.neomechanical.neomoderation.commands.subcommands;

import com.neomechanical.neomoderation.NeoModerationPlugin;
import com.neomechanical.neomoderation.commands.SubCommand;
import org.bukkit.command.CommandSender;

import java.util.Collections;
import java.util.List;

public class EnableCmd implements SubCommand {
    private final NeoModerationPlugin plugin;
    private static final String ENABLED_PATH = "moderation.enabled";

    public EnableCmd(NeoModerationPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "on";
    }

    @Override
    public String getDescription() {
        return "Enable NeoModeration checks.";
    }

    @Override
    public String getUsage() {
        return "/nmod on";
    }

    @Override
    public String getPermission() {
        return "neomoderation.admin";
    }

    @Override
    public List<String> getAliases() {
        return List.of("enable");
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        plugin.getConfig().set(ENABLED_PATH, true);
        plugin.saveConfig();
        plugin.reloadModerationConfig();
        plugin.messages().send(sender, "toggle.on");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        return Collections.emptyList();
    }
}
