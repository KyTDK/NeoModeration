package com.neomechanical.neomoderation.commands.subcommands;

import com.neomechanical.neomoderation.NeoModerationPlugin;
import com.neomechanical.neomoderation.commands.SubCommand;
import com.neomechanical.neomoderation.config.ModerationMode;
import com.neomechanical.neomoderation.config.ModerationSettings;
import com.neomechanical.neomoderation.messages.MenuRenderer;
import com.neomechanical.neomoderation.messages.MessageService;
import com.neomechanical.neomoderation.moderation.MonitorStats;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.PluginDescriptionFile;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HelpCmdTest {
    private record Fixture(NeoModerationPlugin plugin, MessageService messages,
                           CommandSender sender, HelpCmd help) {
    }

    private static SubCommand fake(String name) {
        SubCommand cmd = mock(SubCommand.class);
        when(cmd.getName()).thenReturn(name);
        when(cmd.getUsage()).thenReturn("/nmod " + name);
        when(cmd.getDescription()).thenReturn(name + " desc");
        when(cmd.getPermission()).thenReturn("neomoderation.admin");
        return cmd;
    }

    private Fixture fixture(Map<String, SubCommand> commands) {
        NeoModerationPlugin plugin = mock(NeoModerationPlugin.class);
        MessageService messages = mock(MessageService.class);
        when(plugin.messages()).thenReturn(messages);
        ModerationSettings settings = mock(ModerationSettings.class);
        when(settings.mode()).thenReturn(ModerationMode.ENFORCE);
        when(settings.cloudMode()).thenReturn(ModerationMode.MONITOR);
        when(plugin.settings()).thenReturn(settings);
        MonitorStats stats = mock(MonitorStats.class);
        when(stats.total()).thenReturn(7L);
        when(plugin.monitorStats()).thenReturn(stats);
        PluginDescriptionFile desc = mock(PluginDescriptionFile.class);
        when(desc.getVersion()).thenReturn("1.5.1");
        when(plugin.getDescription()).thenReturn(desc);
        // Locale passthrough: empty maps echo the bare key so HelpCmd's
        // locale-first/fallback logic resolves to the fake usage/desc;
        // non-empty maps echo key + args for structural assertions.
        when(messages.format(anyString(), anyMap())).thenAnswer(inv -> {
            java.util.Map<?, ?> args = inv.getArgument(1);
            return args.isEmpty() ? inv.getArgument(0) : inv.getArgument(0) + args.toString();
        });
        when(messages.dashboardLine(anyString(), anyString(), anyString(), anyLong()))
                .thenReturn("dashboard");
        when(messages.footerLine()).thenReturn("footer");
        CommandSender sender = mock(CommandSender.class);
        when(sender.hasPermission(anyString())).thenReturn(true);
        return new Fixture(plugin, messages, sender, new HelpCmd(plugin, commands));
    }

    @SuppressWarnings("unchecked")
    private List<String> consoleLines(Fixture f) {
        ArgumentCaptor<List<MenuRenderer.Line>> captor = ArgumentCaptor.forClass(List.class);
        verify(f.messages()).sendMenu(eq(f.sender()), captor.capture());
        return captor.getValue().stream().map(MenuRenderer.Line::console).toList();
    }

    @Test
    void pageOneShowsDailyCommandsNotAdmin() {
        Fixture f = fixture(Map.of("status", fake("status"), "key", fake("key")));
        f.help().execute(f.sender(), "nmod", new String[0]);
        List<String> console = consoleLines(f);
        assertTrue(console.stream().anyMatch(l -> l.contains("/nmod status")));
        assertFalse(console.stream().anyMatch(l -> l.contains("/nmod key")));
    }

    @Test
    void pageTwoShowsAdminCommands() {
        Fixture f = fixture(Map.of("status", fake("status"), "key", fake("key")));
        f.help().execute(f.sender(), "nmod", new String[]{"2"});
        List<String> console = consoleLines(f);
        assertTrue(console.stream().anyMatch(l -> l.contains("/nmod key")));
        assertFalse(console.stream().anyMatch(l -> l.contains("/nmod status")));
    }

    @Test
    void commandsWithoutPermissionAreHidden() {
        Fixture f = fixture(Map.of("status", fake("status")));
        when(f.sender().hasPermission(anyString())).thenReturn(false);
        f.help().execute(f.sender(), "nmod", new String[0]);
        assertFalse(consoleLines(f).stream().anyMatch(l -> l.contains("/nmod status")));
    }
}
