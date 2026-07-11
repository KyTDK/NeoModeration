package com.neomechanical.neomoderation.commands.subcommands;

import com.neomechanical.neomoderation.NeoModerationPlugin;
import com.neomechanical.neomoderation.commands.SubCommand;
import org.bukkit.command.CommandSender;

public class ReloadCmd implements SubCommand {
    private final NeoModerationPlugin plugin;

    public ReloadCmd(NeoModerationPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "reload";
    }

    @Override
    public String getDescription() {
        return "Reload the plugin configuration.";
    }

    @Override
    public String getUsage() {
        return "/nmod reload";
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        plugin.reloadModerationConfig();
        plugin.messages().send(sender, "reload.done");
    }
}
