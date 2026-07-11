package com.neomechanical.neomoderation.commands.subcommands;

import com.neomechanical.neomoderation.NeoModerationPlugin;
import com.neomechanical.neomoderation.commands.RuleListEditor;
import com.neomechanical.neomoderation.commands.SubCommand;
import org.bukkit.command.CommandSender;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

public class AllowCmd implements SubCommand {
    private static final String WORDS_PATH = "moderation.offline.allowedWords";
    private static final String URLS_PATH = "moderation.offline.allowedUrls";

    private final NeoModerationPlugin plugin;

    public AllowCmd(NeoModerationPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "allow";
    }

    @Override
    public String getDescription() {
        return "Manage allowed phrases and links (exceptions).";
    }

    @Override
    public String getUsage() {
        return "/nmod allow <word|url> <add|remove|list> [value]";
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
        if (args.length < 2) {
            plugin.messages().send(sender, "allow.usage", Map.of("label", label));
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "word" -> RuleListEditor.handle(plugin, sender, label, args, 2, WORDS_PATH, "allowed word");
            case "url" -> RuleListEditor.handle(plugin, sender, label, args, 2, URLS_PATH, "allowed url");
            default -> plugin.messages().send(sender, "allow.usage", Map.of("label", label));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        if (args.length == 2) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            return Stream.of("word", "url").filter(v -> v.startsWith(prefix)).toList();
        }
        if (args.length == 3) {
            return RuleListEditor.completeActions(args[2]);
        }
        return Collections.emptyList();
    }
}
