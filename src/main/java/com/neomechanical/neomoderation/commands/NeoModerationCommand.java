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
import java.util.Map;
import java.util.Set;

public final class NeoModerationCommand implements CommandExecutor, TabCompleter {
    private static final Set<String> EDITABLE_PATHS = Set.of(
            "locale",
            "moderation.enabled",
            "moderation.api.endpoint",
            "moderation.api.apiKey",
            "moderation.api.connectTimeoutMs",
            "moderation.api.readTimeoutMs",
            "moderation.offline.enabled",
            "moderation.offline.blockAnyUrl",
            "moderation.offline.normalizeLeetspeak",
            "moderation.categories.sexual",
            "moderation.categories.hate",
            "moderation.categories.harassment",
            "moderation.categories.violence",
            "moderation.categories.scam",
            "moderation.categories.spam",
            "moderation.categories.illicit",
            "moderation.categories.selfHarm",
            "moderation.chat.scanAsyncChat",
            "moderation.chat.failOpen"
    );

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

        switch (args[0].toLowerCase()) {
            case "status" -> sendStatus(sender);
            case "reload" -> reload(sender);
            case "config" -> handleConfig(sender, label, args);
            case "rules" -> handleRules(sender, label, args);
            default -> sendHelp(sender, label);
        }
        return true;
    }

    private void sendHelp(CommandSender sender, String label) {
        plugin.messages().send(sender, "help.title");
        plugin.messages().send(sender, "help.status", Map.of("label", label));
        plugin.messages().send(sender, "help.reload", Map.of("label", label));
        plugin.messages().send(sender, "help.config-get", Map.of("label", label));
        plugin.messages().send(sender, "help.config-set", Map.of("label", label));
        plugin.messages().send(sender, "help.config-clear-key", Map.of("label", label));
        plugin.messages().send(sender, "help.rules", Map.of("label", label));
        plugin.messages().send(sender, "help.setup", Map.of("label", label));
    }

    private void sendStatus(CommandSender sender) {
        ModerationSettings settings = plugin.settings();
        plugin.messages().send(sender, "status.title");
        plugin.messages().send(sender, "status.enabled", Map.of("value", String.valueOf(settings.enabled())));
        plugin.messages().send(sender, "status.api-key", Map.of("value", String.valueOf(!settings.api().apiKey().isBlank())));
        plugin.messages().send(sender, "status.endpoint", Map.of("value", settings.api().endpoint()));
        plugin.messages().send(sender, "status.offline", Map.of(
                "value", String.valueOf(settings.offline().enabled()),
                "words", String.valueOf(settings.offline().bannedWords().size()),
                "urls", String.valueOf(settings.offline().bannedUrls().size())
        ));
        plugin.messages().send(sender, "status.circuit", Map.of("value", String.valueOf(plugin.coordinator().isRemoteCallAllowed())));
    }

    private void reload(CommandSender sender) {
        plugin.reloadModerationConfig();
        plugin.messages().send(sender, "reload.done");
    }

    private void handleConfig(CommandSender sender, String label, String[] args) {
        if (args.length < 3) {
            plugin.messages().send(sender, "config.usage", Map.of("label", label));
            return;
        }

        String operation = args[1].toLowerCase();
        String path = args[2];
        if (!EDITABLE_PATHS.contains(path)) {
            plugin.messages().send(sender, "config.not-editable", Map.of("path", path));
            return;
        }

        if ("get".equals(operation)) {
            Object value = plugin.getConfig().get(path);
            plugin.messages().send(sender, "config.value", Map.of("path", path, "value", displayValue(path, value)));
            return;
        }

        if ("clear".equals(operation)) {
            if (!"moderation.api.apiKey".equals(path)) {
                plugin.messages().send(sender, "config.clear-key-only");
                return;
            }
            plugin.getConfig().set(path, "");
            plugin.saveConfig();
            plugin.reloadModerationConfig();
            plugin.messages().send(sender, "config.cleared", Map.of("path", path));
            return;
        }

        if (!"set".equals(operation)) {
            plugin.messages().send(sender, "config.unknown-operation", Map.of("operation", operation));
            return;
        }

        if (args.length < 4) {
            plugin.messages().send(sender, "config.set-usage", Map.of("label", label));
            return;
        }

        Object existing = plugin.getConfig().get(path);
        if (!ConfigValueParser.isEditableScalar(existing)) {
            plugin.messages().send(sender, "config.not-scalar", Map.of("path", path));
            return;
        }

        String rawValue = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
        try {
            Object parsed = ConfigValueParser.parseLikeExisting(existing, rawValue);
            plugin.getConfig().set(path, parsed);
            plugin.saveConfig();
            plugin.reloadModerationConfig();
            plugin.messages().send(sender, "config.updated", Map.of("path", path, "value", displayValue(path, parsed)));
        } catch (RuntimeException ex) {
            plugin.messages().send(sender, "config.invalid-value", Map.of("path", path, "value", rawValue));
        }
    }

    private void handleRules(CommandSender sender, String label, String[] args) {
        if (args.length < 2) {
            plugin.messages().send(sender, "rules.usage", Map.of("label", label));
            return;
        }

        switch (args[1].toLowerCase()) {
            case "list" -> sendRules(sender);
            case "add-word" -> updateRule(sender, args, "moderation.offline.bannedWords", true);
            case "remove-word" -> updateRule(sender, args, "moderation.offline.bannedWords", false);
            case "add-url" -> updateRule(sender, args, "moderation.offline.bannedUrls", true);
            case "remove-url" -> updateRule(sender, args, "moderation.offline.bannedUrls", false);
            default -> plugin.messages().send(sender, "rules.usage", Map.of("label", label));
        }
    }

    private void sendRules(CommandSender sender) {
        List<String> words = plugin.getConfig().getStringList("moderation.offline.bannedWords");
        List<String> urls = plugin.getConfig().getStringList("moderation.offline.bannedUrls");
        plugin.messages().send(sender, "rules.list-words", Map.of("values", String.join(", ", words)));
        plugin.messages().send(sender, "rules.list-urls", Map.of("values", String.join(", ", urls)));
    }

    private void updateRule(CommandSender sender, String[] args, String path, boolean add) {
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
        boolean changed = add ? addRule(values, value) : removeRule(values, value);
        plugin.getConfig().set(path, values);
        plugin.saveConfig();
        plugin.reloadModerationConfig();
        plugin.messages().send(sender, changed ? "rules.updated" : "rules.unchanged", Map.of("path", path, "value", value));
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

    private String displayValue(String path, Object value) {
        if (path.toLowerCase().contains("apikey")) {
            String text = value == null ? "" : String.valueOf(value);
            return text.isBlank() ? "(empty)" : "********";
        }
        return value == null ? "(unset)" : String.valueOf(value);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("neomoderation.admin")) {
            return List.of();
        }
        if (args.length == 1) {
            return filter(List.of("help", "status", "reload", "config", "rules"), args[0]);
        }
        if (args.length == 2 && "config".equalsIgnoreCase(args[0])) {
            return filter(List.of("get", "set", "clear"), args[1]);
        }
        if (args.length == 3 && "config".equalsIgnoreCase(args[0])) {
            return filter(new ArrayList<>(EDITABLE_PATHS), args[2]);
        }
        if (args.length == 2 && "rules".equalsIgnoreCase(args[0])) {
            return filter(List.of("list", "add-word", "remove-word", "add-url", "remove-url"), args[1]);
        }
        return List.of();
    }

    private List<String> filter(List<String> values, String prefix) {
        String normalized = prefix.toLowerCase();
        return values.stream().filter(value -> value.toLowerCase().startsWith(normalized)).sorted().toList();
    }
}
