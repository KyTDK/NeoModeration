package com.neomechanical.neomoderation.commands.subcommands;

import com.neomechanical.neomoderation.NeoModerationPlugin;
import com.neomechanical.neomoderation.commands.SubCommand;
import com.neomechanical.neomoderation.config.ModerationAction;
import com.neomechanical.neomoderation.config.ModerationCategorySettings;
import org.bukkit.command.CommandSender;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * One-command policy bundles. A preset sets cloud category thresholds and the
 * action list; it never touches the mode, the API key, or the local word/URL
 * rules, and every value can still be overridden afterwards.
 */
public class PresetCmd implements SubCommand {
    private record Preset(String name, String summary, double defaultThreshold,
                          Set<String> disabledCategories, List<Map<String, Object>> actions) {
    }

    private static final List<Preset> PRESETS = List.of(
            new Preset(
                    "family",
                    "strictest: all categories at 0.55, clear + 10m mute",
                    0.55D,
                    Set.of(),
                    List.of(
                            Map.of("type", "CLEAR_CHAT"),
                            Map.of("type", "MUTE", "durationSeconds", 600, "reason", "Inappropriate chat message")
                    )
            ),
            new Preset(
                    "community",
                    "balanced default: all categories at 0.7, clear + 5m mute",
                    0.7D,
                    Set.of(),
                    List.of(
                            Map.of("type", "CLEAR_CHAT"),
                            Map.of("type", "MUTE", "durationSeconds", 300, "reason", "Inappropriate chat message")
                    )
            ),
            new Preset(
                    "minimal",
                    "severe content only at 0.8 (harassment/scam/spam off), clear only",
                    0.8D,
                    Set.of("harassment", "scam", "spam"),
                    List.of(Map.of("type", "CLEAR_CHAT"))
            )
    );

    private final NeoModerationPlugin plugin;

    public PresetCmd(NeoModerationPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "preset";
    }

    @Override
    public String getDescription() {
        return "Apply a policy preset (family, community, minimal).";
    }

    @Override
    public String getUsage() {
        return "/nmod preset <family|community|minimal>";
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        if (args.length < 2 || "list".equalsIgnoreCase(args[1])) {
            plugin.messages().send(sender, "preset.list-title");
            for (Preset preset : PRESETS) {
                plugin.messages().send(sender, "preset.list-item", Map.of(
                        "name", preset.name(),
                        "summary", preset.summary()
                ));
            }
            return;
        }

        String requested = args[1].toLowerCase(Locale.ROOT);
        Preset preset = PRESETS.stream().filter(p -> p.name().equals(requested)).findFirst().orElse(null);
        if (preset == null) {
            plugin.messages().send(sender, "preset.usage", Map.of("label", label));
            return;
        }

        for (String category : ModerationCategorySettings.categoryKeys()) {
            Object value = preset.disabledCategories().contains(category)
                    ? Boolean.FALSE
                    : preset.defaultThreshold();
            plugin.getConfig().set("moderation.categories." + category, value);
        }
        plugin.getConfig().set("moderation.actions", toConfigMaps(preset.actions()));
        plugin.saveAndReload();

        plugin.messages().send(sender, "preset.applied", Map.of(
                "name", preset.name(),
                "categories", String.valueOf(plugin.settings().categories().enabledCount()),
                "actions", ModerationAction.describe(plugin.settings().actions())
        ));
    }

    /** Bukkit serializes LinkedHashMaps cleanly and keeps key order stable in config.yml. */
    private static List<Map<String, Object>> toConfigMaps(List<Map<String, Object>> actions) {
        return actions.stream()
                .map(action -> (Map<String, Object>) new LinkedHashMap<>(action))
                .toList();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        if (args.length == 2) {
            return SubCommand.filterPrefix(args[1], "community", "family", "list", "minimal");
        }
        return List.of();
    }
}
