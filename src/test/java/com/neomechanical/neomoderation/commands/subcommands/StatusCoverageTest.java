package com.neomechanical.neomoderation.commands.subcommands;

import com.neomechanical.neomoderation.NeoModerationPlugin;
import com.neomechanical.neomoderation.config.BukkitConfigView;
import com.neomechanical.neomoderation.config.ModerationSettings;
import com.neomechanical.neomoderation.messages.MessageService;
import com.neomechanical.neomoderation.moderation.CaseLog;
import com.neomechanical.neomoderation.moderation.ChatModerationCoordinator;
import com.neomechanical.neomoderation.moderation.MonitorStats;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.PluginDescriptionFile;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StatusCoverageTest {
    @Test
    void statusNamesCloudAndLocalModesWithoutInventingActiveLocalChecks() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("moderation.enabled", true);
        config.set("moderation.mode", "monitor");
        config.set("moderation.cloudMode", "enforce");
        config.set("moderation.offline.enabled", false);
        config.set("moderation.spam.enabled", false);
        config.set("moderation.api.apiKey", "test-key");
        CommandSender sender = mock(CommandSender.class);
        MessageService messages = mock(MessageService.class);

        new StatusCmd(plugin(ModerationSettings.from(new BukkitConfigView(config)), messages))
                .execute(sender, "nmod", new String[0]);

        verify(messages).send(eq(sender), eq("status.mode"), argThat(fields ->
                fields.get("value").contains("no local checks armed")));
        verify(messages).send(eq(sender), eq("status.cloud"), argThat(fields ->
                fields.get("value").contains("mode ENFORCE")
                        && !fields.get("value").contains("Local + cloud")));
    }

    @Test
    void keylessStatusDistinguishesLocalProtectionFromNoChecks() {
        CommandSender sender = mock(CommandSender.class);
        MessageService localMessages = mock(MessageService.class);
        YamlConfiguration localConfig = new YamlConfiguration();
        localConfig.set("moderation.enabled", true);
        localConfig.set("moderation.offline.bannedWords", java.util.List.of("badword"));
        new StatusCmd(plugin(ModerationSettings.from(new BukkitConfigView(localConfig)), localMessages))
                .execute(sender, "nmod", new String[0]);
        verify(localMessages).send(eq(sender), eq("status.cloud"), argThat(fields ->
                fields.get("value").contains("local only")));

        MessageService inertMessages = mock(MessageService.class);
        YamlConfiguration inertConfig = new YamlConfiguration();
        inertConfig.set("moderation.enabled", true);
        inertConfig.set("moderation.offline.enabled", false);
        inertConfig.set("moderation.spam.enabled", false);
        new StatusCmd(plugin(ModerationSettings.from(new BukkitConfigView(inertConfig)), inertMessages))
                .execute(sender, "nmod", new String[0]);
        verify(inertMessages).send(eq(sender), eq("status.cloud"), argThat(fields ->
                fields.get("value").contains("no checks armed")));
    }

    private static NeoModerationPlugin plugin(ModerationSettings settings, MessageService messages) {
        NeoModerationPlugin plugin = mock(NeoModerationPlugin.class);
        when(plugin.settings()).thenReturn(settings);
        when(plugin.messages()).thenReturn(messages);
        when(plugin.monitorStats()).thenReturn(mock(MonitorStats.class));
        when(plugin.caseLog()).thenReturn(mock(CaseLog.class));
        when(plugin.coordinator()).thenReturn(mock(ChatModerationCoordinator.class));
        PluginDescriptionFile description = mock(PluginDescriptionFile.class);
        when(description.getVersion()).thenReturn("1.6.1");
        when(plugin.getDescription()).thenReturn(description);
        return plugin;
    }
}
