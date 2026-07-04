package com.neomechanical.neomoderation.commands;

import com.neomechanical.neomoderation.NeoModerationPlugin;
import com.neomechanical.neomoderation.config.ModerationSettings;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class NeoModerationCommand implements CommandExecutor, TabCompleter {
    private static final String KEY_PATH = "moderation.api.apiKey";
    private static final String ENABLED_PATH = "moderation.enabled";
    private static final String WORDS_PATH = "moderation.offline.bannedWords";
    private static final String URLS_PATH = "moderation.offline.bannedUrls";

    private final NeoModerationPlugin plugin;

    public NeoModerationCommand(NeoModerationPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("neomoderation.admin")) {
            plugin.messages().send(sender, "error.no-permission");
            return true;
        }

        if (args.length == 0 || "help".equalsIgnoreCase(args[0])) {
            sendHelp(sender, label);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "setup" -> setup(sender, label, args);
            case "on" -> setEnabled(sender, true);
            case "off" -> setEnabled(sender, false);
            case "key" -> handleKey(sender, label, args);
            case "status" -> sendStatus(sender);
            case "reload" -> reload(sender);
            case "word" -> handleListRule(sender, label, args, WORDS_PATH, "word");
            case "url" -> handleListRule(sender, label, args, URLS_PATH, "url");
            default -> sendHelp(sender, label);
        }
        return true;
    }

    private void sendHelp(CommandSender sender, String label) {
        plugin.messages().send(sender, "help.title");
        plugin.messages().send(sender, "help.setup", Map.of("label", label));
        plugin.messages().send(sender, "help.on-off", Map.of("label", label));
        plugin.messages().send(sender, "help.key", Map.of("label", label));
        plugin.messages().send(sender, "help.word", Map.of("label", label));
        plugin.messages().send(sender, "help.url", Map.of("label", label));
        plugin.messages().send(sender, "help.status", Map.of("label", label));
        plugin.messages().send(sender, "help.reload", Map.of("label", label));
    }

    private void setup(CommandSender sender, String label, String[] args) {
        if (args.length < 2) {
            plugin.messages().send(sender, "setup.usage", Map.of("label", label));
            return;
        }
        String apiKey = String.join(" ", Arrays.copyOfRange(args, 1, args.length)).trim();
        if (apiKey.isEmpty()) {
            plugin.messages().send(sender, "setup.usage", Map.of("label", label));
            return;
        }
        plugin.getConfig().set(ENABLED_PATH, true);
        plugin.getConfig().set(KEY_PATH, apiKey);
        plugin.saveConfig();
        plugin.reloadModerationConfig();
        plugin.messages().send(sender, "setup.done");
    }

    private void setEnabled(CommandSender sender, boolean enabled) {
        plugin.getConfig().set(ENABLED_PATH, enabled);
        plugin.saveConfig();
        plugin.reloadModerationConfig();
        plugin.messages().send(sender, enabled ? "toggle.on" : "toggle.off");
    }

    private void handleKey(CommandSender sender, String label, String[] args) {
        if (args.length < 2) {
            plugin.messages().send(sender, "key.usage", Map.of("label", label));
            return;
        }
        if ("clear".equalsIgnoreCase(args[1])) {
            plugin.getConfig().set(KEY_PATH, "");
            plugin.saveConfig();
            plugin.reloadModerationConfig();
            plugin.messages().send(sender, "key.cleared");
            return;
        }
        String apiKey = String.join(" ", Arrays.copyOfRange(args, 1, args.length)).trim();
        if (apiKey.isEmpty()) {
            plugin.messages().send(sender, "key.usage", Map.of("label", label));
            return;
        }
        plugin.getConfig().set(KEY_PATH, apiKey);
        plugin.saveConfig();
        plugin.reloadModerationConfig();
        plugin.messages().send(sender, "key.saved");
    }

    private void sendStatus(CommandSender sender) {
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
    }

    private void reload(CommandSender sender) {
        plugin.reloadModerationConfig();
        plugin.messages().send(sender, "reload.done");
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
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("neomoderation.admin")) {
            return List.of();
        }
        if (args.length == 1) {
            return filter(List.of("help", "setup", "on", "off", "key", "status", "reload", "word", "url"), args[0]);
        }
        if (args.length == 2 && "key".equalsIgnoreCase(args[0])) {
            return filter(List.of("clear"), args[1]);
        }
        if (args.length == 2 && ("word".equalsIgnoreCase(args[0]) || "url".equalsIgnoreCase(args[0]))) {
            return filter(List.of("add", "remove", "list"), args[1]);
        }
        return List.of();
    }

    private List<String> filter(List<String> values, String prefix) {
        String normalized = prefix.toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(normalized)).sorted().toList();
    }
}
