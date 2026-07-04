package com.neomechanical.neomoderation.commands.subcommands;

import com.neomechanical.neomoderation.NeoModerationPlugin;
import com.neomechanical.neomoderation.commands.SubCommand;
import org.bukkit.command.CommandSender;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class HelpCmd implements SubCommand {
    private final NeoModerationPlugin plugin;
    private final Map<String, SubCommand> commands;

    public HelpCmd(NeoModerationPlugin plugin, Map<String, SubCommand> commands) {
        this.plugin = plugin;
        this.commands = commands;
    }

    @Override
    public String getName() {
        return "help";
    }

    @Override
    public String getDescription() {
        return "Shows the help menu.";
    }

    @Override
    public String getUsage() {
        return "/nmod help";
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
        plugin.messages().send(sender, "help.title");
        List<SubCommand> uniqueCommands = commands.values().stream().distinct().toList();
        for (SubCommand cmd : uniqueCommands) {
            if (!sender.hasPermission(cmd.getPermission())) {
                continue;
            }
            String usage = cmd.getUsage().replace("/nmod", "/" + label);
            String descKey = "help.desc." + cmd.getName();
            String desc = plugin.messages().format(descKey, Map.of());
            if (desc.equals(descKey) || desc.isBlank()) {
                desc = cmd.getDescription();
            }
            plugin.messages().send(sender, "help.command", Map.of(
                    "usage", usage,
                    "desc", desc
            ));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        return Collections.emptyList();
    }
}
