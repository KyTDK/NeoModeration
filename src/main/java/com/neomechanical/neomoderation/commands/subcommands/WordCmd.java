package com.neomechanical.neomoderation.commands.subcommands;

import com.neomechanical.neomoderation.NeoModerationPlugin;
import com.neomechanical.neomoderation.commands.InputLimits;
import com.neomechanical.neomoderation.commands.SubCommand;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class WordCmd implements SubCommand {
    private final NeoModerationPlugin plugin;
    private static final String PATH = "moderation.offline.bannedWords";

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
        handleListRule(sender, label, args, PATH, "word");
    }

    private void handleListRule(CommandSender sender, String label, String[] args, String path, String kind) {
        if (args.length < 2) {
            plugin.messages().send(sender, "rules.usage", Map.of("label", label, "kind", kind));
            return;
        }

        String action = args[1].toLowerCase(Locale.ROOT);
        if ("list".equals(action)) {
            List<String> values = plugin.getConfig().getStringList(path);
            plugin.messages().send(sender, "rules.list", Map.of(
                    "kind", kind,
                    "values", values.isEmpty() ? "(none)" : String.join(", ", values)
            ));
            return;
        }

        if (!"add".equals(action) && !"remove".equals(action)) {
            plugin.messages().send(sender, "rules.usage", Map.of("label", label, "kind", kind));
            return;
        }

        if (args.length < 3) {
            plugin.messages().send(sender, "rules.value-required");
            return;
        }

        String value = String.join(" ", Arrays.copyOfRange(args, 2, args.length)).trim();
        if (value.isEmpty()) {
            plugin.messages().send(sender, "rules.value-required");
            return;
        }
        if (!InputLimits.isRuleValueLengthValid(value)) {
            plugin.messages().send(sender, "error.rule-too-long", Map.of(
                    "max", String.valueOf(InputLimits.MAX_RULE_VALUE_LENGTH)
            ));
            return;
        }

        List<String> values = new ArrayList<>(plugin.getConfig().getStringList(path));
        boolean changed = "add".equals(action) ? addRule(values, value) : removeRule(values, value);
        plugin.getConfig().set(path, values);
        plugin.saveConfig();
        plugin.reloadModerationConfig();
        plugin.messages().send(sender, changed ? "rules.updated" : "rules.unchanged", Map.of(
                "action", action,
                "kind", kind,
                "value", value
        ));
    }

    private boolean addRule(List<String> values, String value) {
        for (String existing : values) {
            if (existing.equalsIgnoreCase(value)) {
                return false;
            }
        }
        values.add(value);
        return true;
    }

    private boolean removeRule(List<String> values, String value) {
        return values.removeIf(existing -> existing.equalsIgnoreCase(value));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        if (args.length == 2) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            return List.of("add", "remove", "list").stream().filter(v -> v.startsWith(prefix)).toList();
        }
        return Collections.emptyList();
    }
}
