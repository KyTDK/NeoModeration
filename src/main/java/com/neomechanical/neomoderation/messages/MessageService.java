package com.neomechanical.neomoderation.messages;

import net.md_5.bungee.api.chat.BaseComponent;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class MessageService {
    private static final String DEFAULT_LOCALE = "en_US";
    private static final Map<String, Map<String, List<String>>> LEGACY_BUNDLED_VALUES = Map.of(
            "en_US", Map.ofEntries(
                    Map.entry("error.no-api-key", List.of(
                            "{prefix} &cNo API key configured. Please run &e/nmod setup <apiKey>",
                            "{prefix} &cNo API key configured. &7Sign up at &f{url}&7, create a key, then run &e/nmod setup <apiKey>"
                    )),
                    Map.entry("help.usage.test", List.of("/nmod test <msg>")),
                    Map.entry("help.desc.test", List.of("preview the bundled rule; never acts")),
                    Map.entry("setup.usage", List.of(
                            "{prefix} &cUsage: &e/{label} setup <apiKey>&7. Cloud moderation adds zero-day threat feeds, AI evasion & map-art scanning. Get an evaluation key at &f{url}"
                    )),
                    Map.entry("setup.done", List.of(
                            "{prefix} &a&lSuccess! &7Cloud moderation is now &aactive&7. Chat is being scanned."
                    )),
                    Map.entry("trial.already-configured", List.of(
                            "{prefix} &cThis server already has an active API key configured."
                    )),
                    Map.entry("key.saved", List.of("{prefix} &aAPI key saved successfully.")),
                    Map.entry("status.cloud-no-key", List.of(
                            "&7Cloud setup: &eno key&7. Free evaluation unlocks AI evasion & map-art NSFW scanning at &f{url}&7, then run &e/nmod setup <key>&7."
                    )),
                    Map.entry("usage.error", List.of(
                            "{prefix} &cFailed to fetch API usage. Check your key or try again later."
                    )),
                    Map.entry("test.usage", List.of("{prefix} &cUsage: &e/{label} test <message>")),
                    Map.entry("test.cloud-error", List.of(
                            "{prefix} &7Cloud: &cerror &8(&7{detail}, {ms}ms&8)"
                    )),
                    Map.entry("test.cloud-skipped-key", List.of(
                            "{prefix} &7Cloud: &8skipped (no API key; local rules only)",
                            "{prefix} &7Cloud: &8skipped (no API key). &7Cloud checks can classify individual chat messages and map art: &f{url} &7-> &e/nmod setup <key>&7."
                    ))
            ),
            "es_ES", Map.ofEntries(
                    Map.entry("error.no-api-key", List.of(
                            "{prefix} &cNo hay clave API. Usa &e/nmod setup <apiKey>",
                            "{prefix} &cNo hay clave API. &7Regístrate en &f{url}&7, crea una clave y ejecuta &e/nmod setup <apiKey>"
                    )),
                    Map.entry("help.usage.test", List.of("/nmod test <msj>")),
                    Map.entry("help.desc.test", List.of("probar la regla incluida; nunca actúa")),
                    Map.entry("setup.usage", List.of(
                            "{prefix} &cUso: &e/{label} setup <apiKey>&7. La moderación en la nube añade amenazas de día cero, evasión por IA y análisis de mapas. Obtén una clave de prueba en &f{url}"
                    )),
                    Map.entry("setup.done", List.of(
                            "{prefix} &a&lListo! &7Moderación en la nube &aactiva&7."
                    )),
                    Map.entry("trial.already-configured", List.of(
                            "{prefix} &cEste servidor ya tiene una clave API activa configurada."
                    )),
                    Map.entry("key.saved", List.of("{prefix} &aClave API guardada.")),
                    Map.entry("status.cloud-no-key", List.of(
                            "&7Configuración de nube: &esin clave&7. Regístrate en &f{url}&7, crea una clave y ejecuta &e/nmod setup <key>&7."
                    )),
                    Map.entry("usage.error", List.of(
                            "{prefix} &cNo se pudo obtener el uso. Revisa la clave."
                    )),
                    Map.entry("test.usage", List.of("{prefix} &cUso: &e/{label} test <mensaje>")),
                    Map.entry("test.cloud-error", List.of(
                            "{prefix} &7Nube: &cerror &8(&7{detail}, {ms}ms&8)"
                    )),
                    Map.entry("test.cloud-skipped-key", List.of(
                            "{prefix} &7Nube: &8omitida (sin clave API; solo reglas locales)",
                            "{prefix} &7Nube: &8omitida (sin clave API). &7Regístrate en &f{url}&7, crea una clave y ejecuta &e/nmod setup <key>&7."
                    ))
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
     * Shared dashboard header line (version, mode, cloud state, detection
     * count). Every command output opens with this so the plugin reads as one
     * surface; all wording stays in the {@code help.dashboard} locale key.
     */
    public String dashboardLine(String version, String mode, String cloud, long total) {
        return format("help.dashboard", Map.of(
                "version", version,
                "mode", mode,
                "cloud", cloud,
                "total", String.valueOf(total)));
    }

    /** Sends {@link #dashboardLine} as a plain-text line. */
    public void sendDashboard(CommandSender sender, String version, String mode, String cloud, long total) {
        sender.sendMessage(dashboardLine(version, mode, cloud, total));
    }

    /** Shared closing divider; every command output ends with this. */
    public String footerLine() {
        return format("help.footer", Map.of());
    }

    /** Sends {@link #footerLine} as a plain-text line. */
    public void sendFooter(CommandSender sender) {
        sender.sendMessage(footerLine());
    }

    /**
     * Sends a pre-rendered menu page: component lines to players (clickable),
     * legacy-text lines to console and other non-player senders.
     */
    public void sendMenu(CommandSender sender, List<MenuRenderer.Line> lines) {        if (sender instanceof Player player) {
            for (MenuRenderer.Line line : lines) {
                player.spigot().sendMessage(line.player());
            }
            return;
        }
        for (MenuRenderer.Line line : lines) {
            sender.sendMessage(line.console());
        }
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

        for (Map.Entry<String, List<String>> entry
                : LEGACY_BUNDLED_VALUES.getOrDefault(locale, Map.of()).entrySet()) {
            String replacement = bundled.getString(entry.getKey());
            String current = onDisk.getString(entry.getKey());
            if (replacement != null
                    && current != null
                    && entry.getValue().contains(current)
                    && !Objects.equals(replacement, current)) {
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
