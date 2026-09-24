package com.neomechanical.neomoderation.commands.subcommands;

import com.neomechanical.neomoderation.NeoModerationPlugin;
import com.neomechanical.neomoderation.config.BukkitConfigView;
import com.neomechanical.neomoderation.config.ModerationCategorySettings;
import com.neomechanical.neomoderation.config.ModerationSettings;
import com.neomechanical.neomoderation.messages.MessageService;
import com.neomechanical.neomoderation.moderation.MonitorStats;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.PluginDescriptionFile;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TestCmdCoverageTest {
    @Test
    void disabledChatScannerDoesNotPreviewRulesThatCannotRun() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("moderation.enabled", true);
        config.set("moderation.chat.scanAsyncChat", false);
        config.set("moderation.offline.bannedWords", java.util.List.of("badword"));
        NeoModerationPlugin plugin = mock(NeoModerationPlugin.class);
        MessageService messages = mock(MessageService.class);
        CommandSender sender = mock(CommandSender.class);
        when(plugin.settings()).thenReturn(ModerationSettings.from(new BukkitConfigView(config)));
        when(plugin.messages()).thenReturn(messages);
        when(plugin.monitorStats()).thenReturn(mock(MonitorStats.class));
        PluginDescriptionFile description = mock(PluginDescriptionFile.class);
        when(description.getVersion()).thenReturn("1.6.1");
        when(plugin.getDescription()).thenReturn(description);

        new TestCmd(plugin).execute(sender, "nmod", new String[]{"test", "badword"});

        verify(messages).send(sender, "test.chat-disabled");
        verify(messages).send(sender, "test.would-allowed");
    }

    @Test
    void allCloudTextCategoriesOffIsReportedWithoutACloudRequest() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("moderation.enabled", true);
        config.set("moderation.offline.enabled", false);
        config.set("moderation.api.apiKey", "test-key");
        for (String category : ModerationCategorySettings.categoryKeys()) {
            config.set("moderation.categories." + category, false);
        }
        NeoModerationPlugin plugin = mock(NeoModerationPlugin.class);
        MessageService messages = mock(MessageService.class);
        CommandSender sender = mock(CommandSender.class);
        when(plugin.settings()).thenReturn(ModerationSettings.from(new BukkitConfigView(config)));
        when(plugin.messages()).thenReturn(messages);
        when(plugin.monitorStats()).thenReturn(mock(MonitorStats.class));
        PluginDescriptionFile description = mock(PluginDescriptionFile.class);
        when(description.getVersion()).thenReturn("1.6.1");
        when(plugin.getDescription()).thenReturn(description);

        new TestCmd(plugin).execute(sender, "nmod", new String[]{"test", "hello"});

        verify(messages).send(sender, "test.cloud-skipped-categories");
        verify(messages).send(sender, "test.would-allowed");
    }
}
