package com.neomechanical.neomoderation.listener;

import com.neomechanical.neomoderation.NeoModerationPlugin;
import com.neomechanical.neomoderation.config.CaseSettings;
import com.neomechanical.neomoderation.config.MapArtSettings;
import com.neomechanical.neomoderation.config.ModerationApiSettings;
import com.neomechanical.neomoderation.config.ModerationCategorySettings;
import com.neomechanical.neomoderation.config.ModerationMode;
import com.neomechanical.neomoderation.config.ModerationSettings;
import com.neomechanical.neomoderation.config.OfflineModerationSettings;
import com.neomechanical.neomoderation.config.SpamSettings;
import com.neomechanical.neomoderation.config.StrikeSettings;
import com.neomechanical.neomoderation.config.SurfaceSettings;
import com.neomechanical.neomoderation.moderation.ChatModerationCoordinator;
import com.neomechanical.neomoderation.moderation.DetectionNotifier;
import com.neomechanical.neomoderation.moderation.ModerationApiResult;
import com.neomechanical.neomoderation.moderation.MonitorStats;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.MapMeta;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;
import static org.mockito.Mockito.when;

class MapArtListenerTest {
    @Test
    void bypassPermissionSkipsMapChecks() {
        Player player = mock(Player.class);
        when(player.hasPermission("neomoderation.bypass")).thenReturn(true);

        assertTrue(MapArtListener.shouldBypass(player));
    }

    @Test
    void onlyCompletedModerationResultsAreCached() {
        assertTrue(MapArtListener.isCacheableResult(ModerationApiResult.clear()));
        assertTrue(MapArtListener.isCacheableResult(ModerationApiResult.flagged()));
        assertFalse(MapArtListener.isCacheableResult(ModerationApiResult.transientTransport()));
        assertFalse(MapArtListener.isCacheableResult(ModerationApiResult.clientAuth()));
        assertFalse(MapArtListener.isCacheableResult(ModerationApiResult.insufficientCredits()));
        assertFalse(MapArtListener.isCacheableResult(ModerationApiResult.clientRequest()));
    }

    @Test
    void previouslyFlaggedMapIsStillEnforcedWhileCloudCircuitIsOpen() throws Exception {
        ModerationSettings settings = settings();
        try (ChatModerationCoordinator coordinator =
                     new ChatModerationCoordinator(Logger.getLogger("test"))) {
            coordinator.recordApiResult(ModerationApiResult.transientTransport());
            coordinator.recordApiResult(ModerationApiResult.transientTransport());
            coordinator.recordApiResult(ModerationApiResult.transientTransport());
            assertFalse(coordinator.isRemoteCallAllowed());

            MonitorStats stats = new MonitorStats();
            NeoModerationPlugin plugin = mock(NeoModerationPlugin.class);
            when(plugin.settings()).thenReturn(settings);
            when(plugin.coordinator()).thenReturn(coordinator);
            when(plugin.monitorStats()).thenReturn(stats);
            when(plugin.notifier()).thenReturn(mock(DetectionNotifier.class));
            when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));

            MapArtListener listener = new MapArtListener(plugin);
            flaggedMaps(listener).add(7);

            MapMeta meta = mock(MapMeta.class, withSettings().extraInterfaces(LegacyMapId.class));
            when(((LegacyMapId) meta).getMapId()).thenReturn(7);
            ItemStack mapItem = mock(ItemStack.class);
            when(mapItem.getType()).thenReturn(Material.MAP);
            when(mapItem.hasItemMeta()).thenReturn(true);
            when(mapItem.getItemMeta()).thenReturn(meta);

            PlayerInventory inventory = mock(PlayerInventory.class);
            when(inventory.getItem(0)).thenReturn(mapItem);
            Player player = mock(Player.class);
            when(player.getInventory()).thenReturn(inventory);
            when(player.getName()).thenReturn("Tester");

            PlayerItemHeldEvent event = mock(PlayerItemHeldEvent.class);
            when(event.getPlayer()).thenReturn(player);
            when(event.getNewSlot()).thenReturn(0);

            listener.onItemHeld(event);

            assertEquals(1, stats.total());
            assertEquals(1L, stats.byReason().get("map_art"));
        }
    }

    @SuppressWarnings("unchecked")
    private static Set<Integer> flaggedMaps(MapArtListener listener) throws Exception {
        Field field = MapArtListener.class.getDeclaredField("flaggedMaps");
        field.setAccessible(true);
        return (Set<Integer>) field.get(listener);
    }

    private static ModerationSettings settings() {
        return new ModerationSettings(
                true,
                ModerationMode.MONITOR,
                new ModerationApiSettings("https://api.neomechanical.com/v1/events", "test-key", 100, 100),
                new OfflineModerationSettings(true, false, true, List.of(), List.of(), List.of(), List.of()),
                new ModerationCategorySettings(Map.of()),
                new MapArtSettings(true, true, true, true, 1000),
                List.of(),
                true,
                true,
                new ModerationSettings.AlertSettings(true, true),
                new SpamSettings(false, 0, 0, 0.9D, 0, 0, 0, 0),
                new StrikeSettings(false, 30, List.of()),
                new SurfaceSettings(
                        SurfaceSettings.SurfaceMode.OFF,
                        SurfaceSettings.SurfaceMode.OFF,
                        SurfaceSettings.SurfaceMode.OFF,
                        SurfaceSettings.SurfaceMode.OFF,
                        List.of()),
                new CaseSettings(false, false),
                false
        );
    }

    public interface LegacyMapId {
        int getMapId();
    }
}
