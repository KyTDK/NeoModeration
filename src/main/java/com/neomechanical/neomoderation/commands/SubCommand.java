package com.neomechanical.neomoderation.commands;

import org.bukkit.command.CommandSender;

import java.util.List;

public interface SubCommand {
    String getName();

    String getDescription();

    String getUsage();

    String getPermission();

    List<String> getAliases();

    void execute(CommandSender sender, String label, String[] args);

    List<String> onTabComplete(CommandSender sender, String[] args);
}
