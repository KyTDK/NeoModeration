package com.neomechanical.neomoderation.commands.subcommands;

import com.neomechanical.neomoderation.NeoModerationPlugin;
import com.neomechanical.neomoderation.commands.SubCommand;
import org.bukkit.command.CommandSender;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sectioned help menu. Layout lives here; all text (headers, per-command usage
 * and descriptions) comes from the locale so translators control every word.
 */
public class HelpCmd implements SubCommand {
    /** Display order: section locale key -> subcommand names in that section. */
    private static final Map<String, List<String>> SECTIONS = new LinkedHashMap<>();

    static {
        SECTIONS.put("start", List.of("setup", "status", "mode", "preset"));
        SECTIONS.put("rules", List.of("word", "url", "allow", "action"));
        SECTIONS.put("tools", List.of("test", "doctor", "usage", "privacy"));
        SECTIONS.put("admin", List.of("key", "reload", "on", "off"));
    }

    private final NeoModerationPlugin plugin;
    private final Map<String, SubCommand> commands;

    public HelpCmd(NeoModerationPlugin plugin, Map<String, SubCommand> commands) {
        this.plugin = plugin;
        this.commands = commands;
    }

    @Override
    public String getName() {
        return "help";
    }

    @Override
    public String getDescription() {
        return "Shows the help menu.";
    }

    @Override
    public String getUsage() {
        return "/nmod help";
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        plugin.messages().send(sender, "help.title");
        for (Map.Entry<String, List<String>> section : SECTIONS.entrySet()) {
            List<String> visible = section.getValue().stream()
                    .filter(name -> {
                        SubCommand cmd = commands.get(name);
                        return cmd != null && sender.hasPermission(cmd.getPermission());
                    })
                    .toList();
            if (visible.isEmpty()) {
                continue;
            }
            plugin.messages().send(sender, "help.section." + section.getKey());
            for (String name : visible) {
                SubCommand cmd = commands.get(name);
                plugin.messages().send(sender, "help.entry", Map.of(
                        "usage", usageFor(cmd, label),
                        "desc", descriptionFor(cmd)
                ));
            }
        }
        plugin.messages().send(sender, "help.footer");
    }

    private String usageFor(SubCommand cmd, String label) {
        String key = "help.usage." + cmd.getName();
        String usage = plugin.messages().format(key, Map.of());
        if (usage.equals(key) || usage.isBlank()) {
            usage = cmd.getUsage();
        }
        return usage.replace("/nmod", "/" + label);
    }

    private String descriptionFor(SubCommand cmd) {
        String key = "help.desc." + cmd.getName();
        String desc = plugin.messages().format(key, Map.of());
        return desc.equals(key) || desc.isBlank() ? cmd.getDescription() : desc;
    }
}
