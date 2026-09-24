package com.neomechanical.neomoderation.commands.subcommands;

import com.neomechanical.neomoderation.NeoModerationPlugin;
import com.neomechanical.neomoderation.config.BukkitConfigView;
import com.neomechanical.neomoderation.config.ModerationCategorySettings;
import com.neomechanical.neomoderation.config.ModerationSettings;
import com.neomechanical.neomoderation.messages.MessageService;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PrivacyCoverageTest {
    @Test
    void savedKeyWithoutArmedChecksDoesNotClaimContentLeavesTheServer() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("moderation.enabled", true);
        config.set("moderation.offline.enabled", false);
        config.set("moderation.spam.enabled", false);
        config.set("moderation.api.apiKey", "test-key");
        config.set("moderation.mapArt.enabled", false);
        for (String category : ModerationCategorySettings.categoryKeys()) {
            config.set("moderation.categories." + category, false);
        }
        NeoModerationPlugin plugin = mock(NeoModerationPlugin.class);
        MessageService messages = mock(MessageService.class);
        CommandSender sender = mock(CommandSender.class);
        when(plugin.settings()).thenReturn(ModerationSettings.from(new BukkitConfigView(config)));
        when(plugin.messages()).thenReturn(messages);

        new PrivacyCmd(plugin).execute(sender, "nmod", new String[0]);

        verify(messages).send(eq(sender), eq("privacy.headline"), argThat(fields ->
                "No checks armed".equals(fields.get("value"))));
        verify(messages).send(sender, "privacy.cloud-off");
        verify(messages, never()).send(eq(sender), eq("privacy.cloud"), org.mockito.ArgumentMatchers.anyMap());
    }
}
