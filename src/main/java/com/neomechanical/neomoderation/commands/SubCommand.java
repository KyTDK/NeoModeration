package com.neomechanical.neomoderation.commands;

import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.Locale;

public interface SubCommand {
    String getName();

    String getDescription();

    String getUsage();

    void execute(CommandSender sender, String label, String[] args);

    default String getPermission() {
        return "neomoderation.admin";
    }

    default List<String> getAliases() {
        return List.of();
    }

    default List<String> onTabComplete(CommandSender sender, String[] args) {
        return List.of();
    }

    /** Case-insensitive prefix filter for tab completions. */
    static List<String> filterPrefix(String typed, String... options) {
        String prefix = typed.toLowerCase(Locale.ROOT);
        return List.of(options).stream().filter(option -> option.startsWith(prefix)).toList();
    }
}
