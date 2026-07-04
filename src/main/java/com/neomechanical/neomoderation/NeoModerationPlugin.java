package com.neomechanical.neomoderation;

import com.neomechanical.neomoderation.commands.NeoModerationCommand;
import com.neomechanical.neomoderation.config.ModerationSettings;
import com.neomechanical.neomoderation.listener.ChatModerationListener;
import com.neomechanical.neomoderation.listener.PaperAsyncChatBridge;
import com.neomechanical.neomoderation.messages.MessageService;
import com.neomechanical.neomoderation.moderation.ChatModerationActionExecutor;
import com.neomechanical.neomoderation.moderation.ChatModerationCoordinator;
import com.neomechanical.neomoderation.moderation.ChatModerationProcessor;
import com.neomechanical.neomoderation.moderation.PlayerMuteService;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class NeoModerationPlugin extends JavaPlugin {
    private ChatModerationCoordinator coordinator;
    private ModerationSettings settings;
    private MessageService messages;
    private PlayerMuteService muteService;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadModerationConfig();
        muteService = new PlayerMuteService();
        coordinator = new ChatModerationCoordinator(getLogger());
        ChatModerationProcessor processor = new ChatModerationProcessor(
                this,
                coordinator,
                new ChatModerationActionExecutor("NeoModeration", muteService),
                muteService
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
        messages = MessageService.load(this, getConfig().getString("locale", "en_US"));
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

    public MessageService messages() {
        return messages;
    }

    public PlayerMuteService muteService() {
        return muteService;
    }
}
