package com.neomechanical.neomoderation;

import com.neomechanical.neomoderation.commands.NeoModerationCommand;
import com.neomechanical.neomoderation.config.ModerationSettings;
import com.neomechanical.neomoderation.listener.ChatModerationListener;
import com.neomechanical.neomoderation.listener.MapArtListener;
import com.neomechanical.neomoderation.listener.PaperAsyncChatBridge;
import com.neomechanical.neomoderation.listener.SurfaceModerationListener;
import com.neomechanical.neomoderation.messages.MessageService;
import com.neomechanical.neomoderation.moderation.CaseLog;
import com.neomechanical.neomoderation.moderation.ChatModerationActionExecutor;
import com.neomechanical.neomoderation.moderation.ChatModerationCoordinator;
import com.neomechanical.neomoderation.moderation.ChatModerationProcessor;
import com.neomechanical.neomoderation.moderation.DetectionHandler;
import com.neomechanical.neomoderation.moderation.DetectionNotifier;
import com.neomechanical.neomoderation.moderation.ModerationApiClient;
import com.neomechanical.neomoderation.moderation.MonitorStats;
import com.neomechanical.neomoderation.moderation.PlayerMuteService;
import com.neomechanical.neomoderation.moderation.SpamDetector;
import com.neomechanical.neomoderation.moderation.StrikeService;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;
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
    private SpamDetector spamDetector;
    private StrikeService strikeService;
    private CaseLog caseLog;
    private DetectionHandler detectionHandler;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadModerationConfig();
        muteService = new PlayerMuteService(this);
        coordinator = new ChatModerationCoordinator(getLogger());
        monitorStats = new MonitorStats();
        notifier = new DetectionNotifier(this);
        spamDetector = new SpamDetector();
        strikeService = new StrikeService();
        caseLog = CaseLog.open(getDataFolder().toPath().resolve("cases.db"), getLogger());
        detectionHandler = new DetectionHandler(
                this,
                new ChatModerationActionExecutor("NeoModeration", muteService),
                monitorStats,
                notifier,
                strikeService
        );
        ChatModerationProcessor processor = new ChatModerationProcessor(
                this,
                coordinator,
                muteService,
                spamDetector,
                detectionHandler
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
        getServer().getPluginManager().registerEvents(new SurfaceModerationListener(this, detectionHandler), this);
        PluginCommand command = getCommand("neomod");
        if (command != null) {
            NeoModerationCommand executor = new NeoModerationCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }
        registerMetrics();
        getLogger().info("NeoModeration enabled.");
    }

    private void registerMetrics() {
        Metrics metrics = new Metrics(this, BSTATS_PLUGIN_ID);
        metrics.addCustomChart(new SimplePie("moderation_mode",
                () -> settings.mode().name().toLowerCase(java.util.Locale.ROOT)));
        metrics.addCustomChart(new SimplePie("cloud_enabled",
                () -> settings.api().apiKey().isBlank() ? "local_only" : "local_and_cloud"));
        metrics.addCustomChart(new SimplePie("chat_censor",
                () -> settings.chatCensorLocal() ? "censor" : "block"));
    }

    @Override
    public void onDisable() {
        if (coordinator != null) {
            coordinator.close();
        }
        if (caseLog != null) {
            caseLog.close();
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

    public SpamDetector spamDetector() {
        return spamDetector;
    }

    public StrikeService strikeService() {
        return strikeService;
    }

    public CaseLog caseLog() {
        return caseLog;
    }

    public DetectionHandler detectionHandler() {
        return detectionHandler;
    }

    public ModerationApiClient apiClient() {
        return coordinator.apiClient();
    }
}
