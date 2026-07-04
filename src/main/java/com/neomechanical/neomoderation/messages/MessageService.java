package com.neomechanical.neomoderation.messages;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;

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

    /**
     * Copy bundled locale only when missing, then merge any new default keys without
     * overwriting operator customizations.
     */
    private static void ensureLocale(JavaPlugin plugin, String locale) {
        File target = new File(plugin.getDataFolder(), "locale/" + locale + ".yml");
        if (!target.exists()) {
            plugin.saveResource("locale/" + locale + ".yml", false);
            return;
        }
        YamlConfiguration onDisk = YamlConfiguration.loadConfiguration(target);
        YamlConfiguration bundled = loadBundled(plugin, locale);
        if (bundled == null) {
            return;
        }
        boolean changed = false;
        Set<String> keys = bundled.getKeys(true);
        for (String key : keys) {
            if (bundled.isConfigurationSection(key)) {
                continue;
            }
            if (!onDisk.contains(key)) {
                onDisk.set(key, bundled.get(key));
                changed = true;
            }
        }
        if (changed) {
            try {
                onDisk.save(target);
            } catch (IOException ignored) {
                // Keep running with in-memory defaults if disk write fails.
            }
        }
    }

    private static YamlConfiguration loadBundled(JavaPlugin plugin, String locale) {
        try (InputStream stream = plugin.getResource("locale/" + locale + ".yml")) {
            if (stream == null) {
                return null;
            }
            return YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
        } catch (IOException ignored) {
            return null;
        }
    }
}
