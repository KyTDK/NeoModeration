package com.neomechanical.neomoderation;

import com.neomechanical.neomoderation.commands.NeoModerationCommand;
import com.neomechanical.neomoderation.config.ModerationSettings;
import com.neomechanical.neomoderation.listener.ChatModerationListener;
import com.neomechanical.neomoderation.listener.PaperAsyncChatBridge;
import com.neomechanical.neomoderation.moderation.ChatModerationActionExecutor;
import com.neomechanical.neomoderation.moderation.ChatModerationCoordinator;
import com.neomechanical.neomoderation.moderation.ChatModerationProcessor;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class NeoModerationPlugin extends JavaPlugin {
    private ChatModerationCoordinator coordinator;
    private ModerationSettings settings;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadModerationConfig();
        coordinator = new ChatModerationCoordinator(getLogger());
        ChatModerationProcessor processor = new ChatModerationProcessor(
                this,
                coordinator,
                new ChatModerationActionExecutor("NeoModeration")
        );
        getServer().getPluginManager().registerEvents(
                new ChatModerationListener(this, processor),
                this
        );
        PaperAsyncChatBridge.registerIfAvailable(this, processor);
        PluginCommand command = getCommand("neomod");
        if (command != null) {
            NeoModerationCommand executor = new NeoModerationCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }
        getLogger().info("NeoModeration enabled.");
    }

    @Override
    public void onDisable() {
        if (coordinator != null) {
            coordinator.close();
        }
    }

    public void reloadModerationConfig() {
        reloadConfig();
        settings = ModerationSettings.from(getConfig());
        if (coordinator != null) {
            coordinator.resetCircuit();
        }
    }

    public ModerationSettings settings() {
        return settings;
    }

    public ChatModerationCoordinator coordinator() {
        return coordinator;
    }
}
