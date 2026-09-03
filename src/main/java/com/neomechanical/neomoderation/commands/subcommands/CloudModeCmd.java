package com.neomechanical.neomoderation.commands.subcommands;

import com.neomechanical.neomoderation.NeoModerationPlugin;
import com.neomechanical.neomoderation.commands.SubCommand;
import com.neomechanical.neomoderation.config.ModerationMode;
import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Enforcement level for cloud decisions only.
 *
 * <p>Local rules and cloud decisions deserve different levels of trust: the
 * operator wrote the word list, but the cloud categories are a model's
 * judgement they have never seen. A new install therefore enforces local rules
 * and only alerts on cloud ones, and this is how the operator promotes the
 * cloud once they have watched it decide.
 */
public class CloudModeCmd implements SubCommand {
    private final NeoModerationPlugin plugin;

    public CloudModeCmd(NeoModerationPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "cloudmode";
    }

    @Override
    public String getDescription() {
        return "Set whether cloud decisions block, or only alert.";
    }

    @Override
    public String getUsage() {
        return "/nmod cloudmode [monitor|enforce]";
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        if (args.length < 2) {
            plugin.messages().send(sender, "cloudmode.current", Map.of(
                    "value", plugin.settings().cloudMode() == ModerationMode.MONITOR
                            ? "MONITOR (alert only)" : "ENFORCE"
            ));
            return;
        }
        String requested = args[1].toLowerCase(Locale.ROOT);
        if (!"monitor".equals(requested) && !"enforce".equals(requested)) {
            plugin.messages().send(sender, "cloudmode.usage", Map.of("label", label));
            return;
        }
        plugin.getConfig().set("moderation.cloudMode", requested);
        plugin.saveAndReload();
        plugin.messages().send(sender,
                "monitor".equals(requested) ? "cloudmode.set-monitor" : "cloudmode.set-enforce");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        if (args.length == 2) {
            return SubCommand.filterPrefix(args[1], "monitor", "enforce");
        }
        return List.of();
    }
}
