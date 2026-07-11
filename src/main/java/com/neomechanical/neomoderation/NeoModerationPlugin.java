package com.neomechanical.neomoderation;

import com.neomechanical.neomoderation.commands.NeoModerationCommand;
import com.neomechanical.neomoderation.config.ModerationSettings;
import com.neomechanical.neomoderation.listener.ChatModerationListener;
import com.neomechanical.neomoderation.listener.MapArtListener;
import com.neomechanical.neomoderation.listener.PaperAsyncChatBridge;
import com.neomechanical.neomoderation.messages.MessageService;
import com.neomechanical.neomoderation.moderation.ChatModerationActionExecutor;
import com.neomechanical.neomoderation.moderation.ChatModerationCoordinator;
import com.neomechanical.neomoderation.moderation.ChatModerationProcessor;
import com.neomechanical.neomoderation.moderation.DetectionNotifier;
import com.neomechanical.neomoderation.moderation.MonitorStats;
import com.neomechanical.neomoderation.moderation.PlayerMuteService;
import org.bstats.bukkit.Metrics;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class NeoModerationPlugin extends JavaPlugin {
    private static final int BSTATS_PLUGIN_ID = 32542;

    private ChatModerationCoordinator coordinator;
    private ModerationSettings settings;
    private MessageService messages;
    private PlayerMuteService muteService;
    private MonitorStats monitorStats;
    private DetectionNotifier notifier;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadModerationConfig();
        muteService = new PlayerMuteService(this);
        coordinator = new ChatModerationCoordinator(getLogger());
        monitorStats = new MonitorStats();
        notifier = new DetectionNotifier(this);
        ChatModerationProcessor processor = new ChatModerationProcessor(
                this,
                coordinator,
                new ChatModerationActionExecutor("NeoModeration", muteService),
                muteService,
                monitorStats,
                notifier
        );
        // Paper fires the legacy AsyncPlayerChatEvent whenever a legacy listener is
        // registered, so registering both would moderate every message twice
        // (double API usage, double punishments). Prefer the modern Paper event and
        // fall back to the legacy listener only on Spigot.
        if (!PaperAsyncChatBridge.registerIfAvailable(this, processor)) {
            getServer().getPluginManager().registerEvents(
                    new ChatModerationListener(this, processor),
                    this
            );
        }
        getServer().getPluginManager().registerEvents(new MapArtListener(this), this);
        PluginCommand command = getCommand("neomod");
        if (command != null) {
            NeoModerationCommand executor = new NeoModerationCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }
        new Metrics(this, BSTATS_PLUGIN_ID);
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
        settings = ModerationSettings.from(getConfig(), getLogger());
        messages = MessageService.load(this, getConfig().getString("locale", "en_US"));
        if (coordinator != null) {
            coordinator.resetCircuit();
        }
    }

    /** Persist in-memory config edits and re-apply them atomically. */
    public void saveAndReload() {
        saveConfig();
        reloadModerationConfig();
    }

    public void runAsync(Runnable task) {
        getServer().getScheduler().runTaskAsynchronously(this, task);
    }

    public void runSync(Runnable task) {
        getServer().getScheduler().runTask(this, task);
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

    public MonitorStats monitorStats() {
        return monitorStats;
    }

    public DetectionNotifier notifier() {
        return notifier;
    }

    public com.neomechanical.neomoderation.moderation.ModerationApiClient apiClient() {
        return coordinator.apiClient();
    }
}
