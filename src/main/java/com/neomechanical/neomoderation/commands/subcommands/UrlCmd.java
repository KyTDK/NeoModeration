package com.neomechanical.neomoderation.commands.subcommands;

import com.neomechanical.neomoderation.NeoModerationPlugin;
import com.neomechanical.neomoderation.commands.RuleListEditor;
import com.neomechanical.neomoderation.commands.SubCommand;
import org.bukkit.command.CommandSender;

import java.util.Collections;
import java.util.List;

public class UrlCmd implements SubCommand {
    private static final String PATH = "moderation.offline.bannedUrls";

    private final NeoModerationPlugin plugin;

    public UrlCmd(NeoModerationPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "url";
    }

    @Override
    public String getDescription() {
        return "Manage offline banned URLs.";
    }

    @Override
    public String getUsage() {
        return "/nmod url <add|remove|list> [url]";
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
        RuleListEditor.handle(plugin, sender, label, args, 1, PATH, "url");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        if (args.length == 2) {
            return RuleListEditor.completeActions(args[1]);
        }
        return Collections.emptyList();
    }
}
