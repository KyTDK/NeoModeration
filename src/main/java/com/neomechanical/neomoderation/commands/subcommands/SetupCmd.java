package com.neomechanical.neomoderation.commands.subcommands;

import com.neomechanical.neomoderation.NeoModerationPlugin;
import com.neomechanical.neomoderation.commands.InputLimits;
import com.neomechanical.neomoderation.commands.SubCommand;
import org.bukkit.command.CommandSender;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class SetupCmd implements SubCommand {
    private final NeoModerationPlugin plugin;
    private static final String KEY_PATH = "moderation.api.apiKey";
    private static final String ENABLED_PATH = "moderation.enabled";

    public SetupCmd(NeoModerationPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "setup";
    }

    @Override
    public String getDescription() {
        return "Setup NeoModeration with an API key.";
    }

    @Override
    public String getUsage() {
        return "/nmod setup <apiKey>";
    }

    @Override
    public String getPermission() {
        return "neomoderation.admin";
    }

    @Override
    public List<String> getAliases() {
        return Collections.emptyList();
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        if (args.length < 2) {
            plugin.messages().send(sender, "setup.usage", Map.of("label", label));
            return;
        }
        String apiKey = String.join(" ", Arrays.copyOfRange(args, 1, args.length)).trim();
        if (apiKey.isEmpty()) {
            plugin.messages().send(sender, "setup.usage", Map.of("label", label));
            return;
        }
        if (!InputLimits.isApiKeyLengthValid(apiKey)) {
            plugin.messages().send(sender, "error.api-key-too-long", Map.of(
                    "max", String.valueOf(InputLimits.MAX_API_KEY_LENGTH)
            ));
            return;
        }
        plugin.getConfig().set(ENABLED_PATH, true);
        plugin.getConfig().set(KEY_PATH, apiKey);
        plugin.saveConfig();
        plugin.reloadModerationConfig();
        plugin.messages().send(sender, "setup.done");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        return Collections.emptyList();
    }
}
