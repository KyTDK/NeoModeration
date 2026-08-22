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
import java.util.Objects;
import java.util.Set;

public final class MessageService {
    private static final String DEFAULT_LOCALE = "en_US";
    private static final Map<String, Map<String, String>> LEGACY_BUNDLED_VALUES = Map.of(
            "en_US", Map.ofEntries(
                    Map.entry("error.no-api-key",
                            "{prefix} &cNo API key configured. Please run &e/nmod setup <apiKey>"),
                    Map.entry("help.usage.test", "/nmod test <msg>"),
                    Map.entry("help.desc.test", "preview a decision"),
                    Map.entry("setup.done",
                            "{prefix} &a&lSuccess! &7Cloud moderation is now &aactive&7. Chat is being scanned."),
                    Map.entry("key.saved", "{prefix} &aAPI key saved successfully."),
                    Map.entry("usage.error",
                            "{prefix} &cFailed to fetch API usage. Check your key or try again later."),
                    Map.entry("test.usage", "{prefix} &cUsage: &e/{label} test <message>"),
                    Map.entry("test.cloud-error",
                            "{prefix} &7Cloud: &cerror &8(&7{detail}, {ms}ms&8)"),
                    Map.entry("test.cloud-skipped-key",
                            "{prefix} &7Cloud: &8skipped (no API key; local rules only)")
            ),
            "es_ES", Map.ofEntries(
                    Map.entry("error.no-api-key",
                            "{prefix} &cNo hay clave API. Usa &e/nmod setup <apiKey>"),
                    Map.entry("help.usage.test", "/nmod test <msj>"),
                    Map.entry("help.desc.test", "previsualizar una decisión"),
                    Map.entry("setup.done",
                            "{prefix} &a&lListo! &7Moderación en la nube &aactiva&7."),
                    Map.entry("key.saved", "{prefix} &aClave API guardada."),
                    Map.entry("usage.error",
                            "{prefix} &cNo se pudo obtener el uso. Revisa la clave."),
                    Map.entry("test.usage", "{prefix} &cUso: &e/{label} test <mensaje>"),
                    Map.entry("test.cloud-error",
                            "{prefix} &7Nube: &cerror &8(&7{detail}, {ms}ms&8)"),
                    Map.entry("test.cloud-skipped-key",
                            "{prefix} &7Nube: &8omitida (sin clave API; solo reglas locales)")
            )
    );

    private final YamlConfiguration fallback;
    private final YamlConfiguration active;

    private MessageService(YamlConfiguration fallback, YamlConfiguration active) {
        this.fallback = fallback;
        this.active = active;
    }

    public static MessageService load(JavaPlugin plugin, String locale) {
        YamlConfiguration fallback = ensureLocale(plugin, DEFAULT_LOCALE);
        YamlConfiguration spanish = ensureLocale(plugin, "es_ES");
        File localeDir = new File(plugin.getDataFolder(), "locale");
        String requested = locale == null || locale.isBlank() ? DEFAULT_LOCALE : locale;
        File requestedFile = new File(localeDir, requested + ".yml");
        YamlConfiguration active;
        if (DEFAULT_LOCALE.equals(requested)) {
            active = fallback;
        } else if ("es_ES".equals(requested)) {
            active = spanish;
        } else {
            active = requestedFile.exists()
                    ? YamlConfiguration.loadConfiguration(requestedFile)
                    : fallback;
        }
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
        // The shared chat prefix is a locale entry so every line stays consistent
        // and rebrandable from one place.
        message = message.replace("{prefix}", active.getString("prefix", fallback.getString("prefix", "")));
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    /**
     * Copy bundled locale only when missing, then merge any new default keys without
     * overwriting operator customizations.
     */
    private static YamlConfiguration ensureLocale(JavaPlugin plugin, String locale) {
        File target = new File(plugin.getDataFolder(), "locale/" + locale + ".yml");
        if (!target.exists()) {
            plugin.saveResource("locale/" + locale + ".yml", false);
            return YamlConfiguration.loadConfiguration(target);
        }
        YamlConfiguration onDisk = YamlConfiguration.loadConfiguration(target);
        YamlConfiguration bundled = loadBundled(plugin, locale);
        if (bundled == null) {
            plugin.getLogger().warning("Could not load bundled locale " + locale
                    + "; keeping the existing locale values.");
            return onDisk;
        }
        boolean changed = mergeBundledLocale(locale, onDisk, bundled);
        if (changed) {
            try {
                onDisk.save(target);
            } catch (IOException e) {
                plugin.getLogger().warning("Could not persist locale " + locale
                        + " migration to " + target + ": " + e.getMessage()
                        + ". The merged values remain active until restart.");
            }
        }
        return onDisk;
    }

    /**
     * Adds new keys and migrates only exact, known bundled values from the previous
     * release. Any operator-edited value remains untouched.
     */
    static boolean mergeBundledLocale(
            String locale,
            YamlConfiguration onDisk,
            YamlConfiguration bundled
    ) {
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

        for (Map.Entry<String, String> entry
                : LEGACY_BUNDLED_VALUES.getOrDefault(locale, Map.of()).entrySet()) {
            String replacement = bundled.getString(entry.getKey());
            if (replacement != null
                    && Objects.equals(onDisk.getString(entry.getKey()), entry.getValue())
                    && !Objects.equals(replacement, entry.getValue())) {
                onDisk.set(entry.getKey(), replacement);
                changed = true;
            }
        }
        return changed;
    }

    private static YamlConfiguration loadBundled(JavaPlugin plugin, String locale) {
        try (InputStream stream = plugin.getResource("locale/" + locale + ".yml")) {
            if (stream == null) {
                return null;
            }
            return YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
        } catch (IOException e) {
            plugin.getLogger().warning("Could not read bundled locale " + locale + ": " + e.getMessage());
            return null;
        }
    }
}
