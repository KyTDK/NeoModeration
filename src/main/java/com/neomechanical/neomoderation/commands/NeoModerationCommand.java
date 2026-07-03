package com.neomechanical.neomoderation.commands;

import com.neomechanical.neomoderation.NeoModerationPlugin;
import com.neomechanical.neomoderation.config.ModerationSettings;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

public final class NeoModerationCommand implements CommandExecutor, TabCompleter {
    private static final Set<String> EDITABLE_PATHS = Set.of(
            "moderation.enabled",
            "moderation.api.endpoint",
            "moderation.api.apiKey",
            "moderation.api.connectTimeoutMs",
            "moderation.api.readTimeoutMs",
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
            sender.sendMessage(ChatColor.RED + "You do not have permission to use NeoModeration.");
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
            default -> sendHelp(sender, label);
        }
        return true;
    }

    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage(ChatColor.AQUA + "NeoModeration");
        sender.sendMessage(ChatColor.GRAY + "/" + label + " status");
        sender.sendMessage(ChatColor.GRAY + "/" + label + " reload");
        sender.sendMessage(ChatColor.GRAY + "/" + label + " config get <path>");
        sender.sendMessage(ChatColor.GRAY + "/" + label + " config set <path> <value>");
        sender.sendMessage(ChatColor.GRAY + "/" + label + " config clear moderation.api.apiKey");
        sender.sendMessage(ChatColor.YELLOW + "Setup: /" + label + " config set moderation.api.apiKey YOUR_KEY");
    }

    private void sendStatus(CommandSender sender) {
        ModerationSettings settings = plugin.settings();
        sender.sendMessage(ChatColor.AQUA + "NeoModeration status");
        sender.sendMessage(ChatColor.GRAY + "Enabled: " + ChatColor.WHITE + settings.enabled());
        sender.sendMessage(ChatColor.GRAY + "API key configured: " + ChatColor.WHITE + !settings.api().apiKey().isBlank());
        sender.sendMessage(ChatColor.GRAY + "Endpoint: " + ChatColor.WHITE + settings.api().endpoint());
        sender.sendMessage(ChatColor.GRAY + "Circuit open: " + ChatColor.WHITE + plugin.coordinator().isRemoteCallAllowed());
    }

    private void reload(CommandSender sender) {
        plugin.reloadModerationConfig();
        sender.sendMessage(ChatColor.GREEN + "NeoModeration config reloaded.");
    }

    private void handleConfig(CommandSender sender, String label, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /" + label + " config get <path>, set <path> <value>, or clear moderation.api.apiKey");
            return;
        }

        String operation = args[1].toLowerCase();
        String path = args[2];
        if (!EDITABLE_PATHS.contains(path)) {
            sender.sendMessage(ChatColor.RED + "Path is not editable: " + path);
            return;
        }

        if ("get".equals(operation)) {
            Object value = plugin.getConfig().get(path);
            sender.sendMessage(ChatColor.GRAY + path + ": " + ChatColor.WHITE + displayValue(path, value));
            return;
        }

        if ("clear".equals(operation)) {
            if (!"moderation.api.apiKey".equals(path)) {
                sender.sendMessage(ChatColor.RED + "Only moderation.api.apiKey can be cleared.");
                return;
            }
            plugin.getConfig().set(path, "");
            plugin.saveConfig();
            plugin.reloadModerationConfig();
            sender.sendMessage(ChatColor.GREEN + "Cleared " + path + ".");
            return;
        }

        if (!"set".equals(operation)) {
            sender.sendMessage(ChatColor.RED + "Unknown config operation: " + operation);
            return;
        }

        if (args.length < 4) {
            sender.sendMessage(ChatColor.RED + "Usage: /" + label + " config set <path> <value>");
            return;
        }

        Object existing = plugin.getConfig().get(path);
        if (!ConfigValueParser.isEditableScalar(existing)) {
            sender.sendMessage(ChatColor.RED + "Path is not a scalar value: " + path);
            return;
        }

        String rawValue = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
        try {
            Object parsed = ConfigValueParser.parseLikeExisting(existing, rawValue);
            plugin.getConfig().set(path, parsed);
            plugin.saveConfig();
            plugin.reloadModerationConfig();
            sender.sendMessage(ChatColor.GREEN + "Updated " + path + " to " + displayValue(path, parsed));
        } catch (RuntimeException ex) {
            sender.sendMessage(ChatColor.RED + "Invalid value for " + path + ": " + rawValue);
        }
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
            return filter(List.of("help", "status", "reload", "config"), args[0]);
        }
        if (args.length == 2 && "config".equalsIgnoreCase(args[0])) {
            return filter(List.of("get", "set", "clear"), args[1]);
        }
        if (args.length == 3 && "config".equalsIgnoreCase(args[0])) {
            return filter(new ArrayList<>(EDITABLE_PATHS), args[2]);
        }
        return List.of();
    }

    private List<String> filter(List<String> values, String prefix) {
        String normalized = prefix.toLowerCase();
        return values.stream().filter(value -> value.toLowerCase().startsWith(normalized)).sorted().toList();
    }
}
