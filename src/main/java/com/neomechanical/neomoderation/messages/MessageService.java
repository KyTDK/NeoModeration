package com.neomechanical.neomoderation.messages;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Map;

public final class MessageService {
    private static final String DEFAULT_LOCALE = "en_US";

    private final YamlConfiguration fallback;
    private final YamlConfiguration active;

    private MessageService(YamlConfiguration fallback, YamlConfiguration active) {
        this.fallback = fallback;
        this.active = active;
    }

    public static MessageService load(JavaPlugin plugin, String locale) {
        ensureLocale(plugin, DEFAULT_LOCALE);
        ensureLocale(plugin, "es_ES");
        File localeDir = new File(plugin.getDataFolder(), "locale");
        YamlConfiguration fallback = YamlConfiguration.loadConfiguration(new File(localeDir, DEFAULT_LOCALE + ".yml"));
        String requested = locale == null || locale.isBlank() ? DEFAULT_LOCALE : locale;
        File requestedFile = new File(localeDir, requested + ".yml");
        YamlConfiguration active = requestedFile.exists()
                ? YamlConfiguration.loadConfiguration(requestedFile)
                : fallback;
        return new MessageService(fallback, active);
    }

    public void send(CommandSender sender, String key) {
        send(sender, key, Map.of());
    }

    public void send(CommandSender sender, String key, Map<String, String> placeholders) {
        sender.sendMessage(format(key, placeholders));
    }

    public String format(String key, Map<String, String> placeholders) {
        String message = active.getString(key, fallback.getString(key, key));
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            message = message.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    private static void ensureLocale(JavaPlugin plugin, String locale) {
        plugin.saveResource("locale/" + locale + ".yml", true);
    }
}
