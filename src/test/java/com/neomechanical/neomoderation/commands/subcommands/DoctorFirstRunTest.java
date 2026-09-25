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

import java.util.List;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DoctorFirstRunTest {
    @Test
    void blockOnlyFreshInstallIsHealthyRatherThanWarnedAsMissingActions() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("moderation.enabled", true);
        config.set("moderation.offline.bannedWords", List.of("badword"));
        CommandSender sender = mock(CommandSender.class);
        MessageService messages = mock(MessageService.class);

        new DoctorCmd(plugin(ModerationSettings.from(new BukkitConfigView(config)), messages))
                .execute(sender, "nmod", new String[0]);

        verify(messages).send(eq(sender), eq("doctor.pass"), argThat(fields ->
                "Actions".equals(fields.get("check"))
                        && fields.get("detail").contains("block only")));
        verify(messages, never()).send(eq(sender), eq("doctor.warn"), argThat(fields ->
                "Actions".equals(fields.get("check"))));
    }

    @Test
    void doctorDistinguishesLocalMonitorFromCloudEnforce() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("moderation.enabled", true);
        config.set("moderation.mode", "monitor");
        config.set("moderation.cloudMode", "enforce");
        config.set("moderation.api.apiKey", "test-key");
        CommandSender sender = mock(CommandSender.class);
        MessageService messages = mock(MessageService.class);

        new DoctorCmd(plugin(ModerationSettings.from(new BukkitConfigView(config)), messages))
                .execute(sender, "nmod", new String[0]);

        verify(messages).send(eq(sender), eq("doctor.warn"), argThat(fields ->
                "Local mode".equals(fields.get("check"))
                        && fields.get("detail").contains("local detections")));
        verify(messages).send(eq(sender), eq("doctor.pass"), argThat(fields ->
                "Cloud mode".equals(fields.get("check"))
                        && fields.get("detail").contains("enforce")));
    }

    @Test
    void doctorDoesNotCallAnAntiSpamOnlyServerUnprotected() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("moderation.enabled", true);
        config.set("moderation.offline.enabled", false);
        CommandSender sender = mock(CommandSender.class);
        MessageService messages = mock(MessageService.class);

        new DoctorCmd(plugin(ModerationSettings.from(new BukkitConfigView(config)), messages))
                .execute(sender, "nmod", new String[0]);

        verify(messages).send(eq(sender), eq("doctor.warn"), argThat(fields ->
                "Local rules".equals(fields.get("check"))
                        && fields.get("detail").contains("anti-spam still active")));
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
