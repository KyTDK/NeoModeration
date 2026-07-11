package com.neomechanical.neomoderation.commands.subcommands;

import com.neomechanical.neomoderation.NeoModerationPlugin;
import com.neomechanical.neomoderation.commands.RuleListEditor;
import com.neomechanical.neomoderation.commands.SubCommand;
import org.bukkit.command.CommandSender;

import java.util.Collections;
import java.util.List;

public class WordCmd implements SubCommand {
    private static final String PATH = "moderation.offline.bannedWords";

    private final NeoModerationPlugin plugin;

    public WordCmd(NeoModerationPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "word";
    }

    @Override
    public String getDescription() {
        return "Manage offline banned words.";
    }

    @Override
    public String getUsage() {
        return "/nmod word <add|remove|list> [word]";
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
        RuleListEditor.handle(plugin, sender, label, args, 1, PATH, "word");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        if (args.length == 2) {
            return RuleListEditor.completeActions(args[1]);
        }
        return Collections.emptyList();
    }
}
