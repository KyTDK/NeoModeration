package com.neomechanical.neomoderation.commands;

import com.neomechanical.neomoderation.NeoModerationPlugin;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Shared add/remove/list handler for the string-list rules in config.yml
 * (banned/allowed words and URLs). {@code actionIndex} is the position of the
 * add|remove|list token in {@code args}; everything after it joins into the value.
 */
public final class RuleListEditor {
    private RuleListEditor() {
    }

    public static void handle(
            NeoModerationPlugin plugin,
            CommandSender sender,
            String label,
            String[] args,
            int actionIndex,
            String path,
            String kind
    ) {
        if (args.length <= actionIndex) {
            plugin.messages().send(sender, "rules.usage", Map.of("label", label, "kind", kind));
            return;
        }

        String action = args[actionIndex].toLowerCase(Locale.ROOT);
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

        if (args.length <= actionIndex + 1) {
            plugin.messages().send(sender, "rules.value-required");
            return;
        }

        String value = String.join(" ", Arrays.copyOfRange(args, actionIndex + 1, args.length)).trim();
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

    public static List<String> completeActions(String prefix) {
        return List.of("add", "remove", "list").stream()
                .filter(v -> v.startsWith(prefix.toLowerCase(Locale.ROOT)))
                .toList();
    }

    private static boolean addRule(List<String> values, String value) {
        for (String existing : values) {
            if (existing.equalsIgnoreCase(value)) {
                return false;
            }
        }
        values.add(value);
        return true;
    }

    private static boolean removeRule(List<String> values, String value) {
        return values.removeIf(existing -> existing.equalsIgnoreCase(value));
    }
}
