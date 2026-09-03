package com.neomechanical.neomoderation.messages;

import net.md_5.bungee.api.chat.BaseComponent;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MessageServiceMenuTest {
    private static MessageService messages() {
        org.bukkit.configuration.file.YamlConfiguration empty =
                new org.bukkit.configuration.file.YamlConfiguration();
        return messages(empty, empty);
    }

    private static MessageService messages(
            org.bukkit.configuration.file.YamlConfiguration fallback,
            org.bukkit.configuration.file.YamlConfiguration active) {
        try {
            java.lang.reflect.Constructor<MessageService> ctor =
                    MessageService.class.getDeclaredConstructor(
                            org.bukkit.configuration.file.YamlConfiguration.class,
                            org.bukkit.configuration.file.YamlConfiguration.class);
            ctor.setAccessible(true);
            return ctor.newInstance(fallback, active);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static MessageService messagesWithChrome() {
        org.bukkit.configuration.file.YamlConfiguration fallback =
                new org.bukkit.configuration.file.YamlConfiguration();
        fallback.set("prefix", "[NMod]");
        fallback.set("help.dashboard", "{prefix} v{version} {mode} {cloud} {total}");
        fallback.set("help.footer", "{prefix} --");
        return messages(fallback, new org.bukkit.configuration.file.YamlConfiguration());
    }

    @Test
    void consoleSenderGetsLegacyLines() {
        CommandSender console = mock(CommandSender.class);
        List<MenuRenderer.Line> lines = MenuRenderer.lines(MenuRenderer.text("&aHello"));
        messages().sendMenu(console, lines);
        verify(console).sendMessage("§aHello");
    }

    @Test
    void playerGetsComponents() {
        Player player = mock(Player.class);
        Player.Spigot spigot = mock(Player.Spigot.class);
        when(player.spigot()).thenReturn(spigot);
        List<MenuRenderer.Line> lines = MenuRenderer.lines(MenuRenderer.text("&aHello"));
        messages().sendMenu(player, lines);
        ArgumentCaptor<BaseComponent[]> captor = ArgumentCaptor.forClass(BaseComponent[].class);
        verify(spigot).sendMessage(captor.capture());
        assertEquals("Hello", BaseComponent.toPlainText(captor.getValue()));
    }

    @Test
    void dashboardLineSubstitutesEveryPlaceholder() {
        assertEquals("[NMod] v1.5.1 ENFORCE MONITOR 7",
                messagesWithChrome().dashboardLine("1.5.1", "ENFORCE", "MONITOR", 7L));
    }

    @Test
    void sendDashboardDeliversFormattedLine() {
        CommandSender console = mock(CommandSender.class);
        messagesWithChrome().sendDashboard(console, "1.5.1", "ENFORCE", "MONITOR", 7L);
        verify(console).sendMessage("[NMod] v1.5.1 ENFORCE MONITOR 7");
    }

    @Test
    void sendFooterDeliversDivider() {
        CommandSender console = mock(CommandSender.class);
        messagesWithChrome().sendFooter(console);
        verify(console).sendMessage("[NMod] --");
    }
}
