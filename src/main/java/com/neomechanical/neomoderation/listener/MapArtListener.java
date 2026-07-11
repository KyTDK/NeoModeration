package com.neomechanical.neomoderation.listener;

import com.neomechanical.neomoderation.NeoModerationPlugin;
import com.neomechanical.neomoderation.config.MapArtSettings;
import com.neomechanical.neomoderation.config.ModerationMode;
import com.neomechanical.neomoderation.config.ModerationSettings;
import com.neomechanical.neomoderation.moderation.MapArtScanner;
import com.neomechanical.neomoderation.moderation.ModerationApiResult;
import org.bukkit.Material;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapView;
import org.bukkit.scheduler.BukkitRunnable;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Scans filled maps (map art) for NSFW content when players hold them or interact with
 * them in item frames. Results are cached per map id so a given map is only scanned once.
 * Uses reflection so the plugin still compiles against older Bukkit APIs while running on
 * modern Paper.
 */
public final class MapArtListener implements Listener {
    private static final String BYPASS_PERMISSION = "neomoderation.bypass";

    private final NeoModerationPlugin plugin;
    private final Set<Integer> scannedMaps;
    private final Set<Integer> flaggedMaps;
    private final Material filledMapMaterial = resolveFilledMapMaterial();

    public MapArtListener(NeoModerationPlugin plugin) {
        this.plugin = plugin;
        int cacheSize = plugin.settings().mapArt().cacheSize();
        this.scannedMaps = boundedSet(cacheSize);
        this.flaggedMaps = boundedSet(cacheSize);
    }

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (!config().enabled() || !config().scanOnFrameInteract()) {
            return;
        }
        if (!(event.getRightClicked() instanceof ItemFrame frame)) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack held = mainHand(player.getInventory());
        if (!isFilledMap(held)) {
            held = offHand(player.getInventory());
        }
        if (isFilledMap(held)) {
            handleMapItem(player, held);
            return;
        }
        ItemStack frameItem = frame.getItem();
        if (isFilledMap(frameItem)) {
            handleMapItem(player, frameItem);
        }
    }

    @EventHandler
    public void onItemHeld(PlayerItemHeldEvent event) {
        if (!config().enabled() || !config().scanOnHold()) {
            return;
        }
        ItemStack item = event.getPlayer().getInventory().getItem(event.getNewSlot());
        if (isFilledMap(item)) {
            handleMapItem(event.getPlayer(), item);
        }
    }

    private void handleMapItem(Player player, ItemStack mapItem) {
        if (shouldBypass(player)
                || !plugin.settings().enabled()
                || mapItem == null
                || !mapItem.hasItemMeta()) {
            return;
        }
        if (plugin.settings().api().apiKey().isBlank()) {
            return;
        }
        if (!(mapItem.getItemMeta() instanceof MapMeta meta)) {
            return;
        }
        Integer mapId = resolveMapId(meta);
        if (mapId == null) {
            return;
        }
        if (flaggedMaps.contains(mapId)) {
            handleFlaggedMap(player, mapItem, mapId, "mapart.blocked");
            return;
        }
        if (!scannedMaps.add(mapId)) {
            return;
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                String base64Image = MapArtScanner.getBase64Image(mapId);
                if (base64Image == null) {
                    scannedMaps.remove(mapId);
                    return;
                }
                ModerationApiResult result = plugin.apiClient().moderateImage(
                        player.getName(),
                        player.getUniqueId().toString(),
                        base64Image,
                        plugin.settings().api(),
                        plugin.settings().categories()
                );
                if (!isCacheableResult(result)) {
                    scannedMaps.remove(mapId);
                    return;
                }
                if (!result.isFlagged()) {
                    return;
                }
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        flaggedMaps.add(mapId);
                        handleFlaggedMap(player, mapItem, mapId, "mapart.confiscated");
                    }
                }.runTask(plugin);
            }
        }.runTaskAsynchronously(plugin);
    }

    /** Main thread only. Monitor mode alerts staff instead of messaging/confiscating. */
    private void handleFlaggedMap(Player player, ItemStack mapItem, int mapId, String messageKey) {
        ModerationSettings settings = plugin.settings();
        if (settings.mode() == ModerationMode.MONITOR) {
            plugin.monitorStats().record("map_art:" + mapId);
            plugin.notifier().notifyDetection(player, "map_art", "map_art:" + mapId,
                    "(map " + mapId + ")", settings, "confiscate", true);
            plugin.getLogger().info("MONITOR: map " + mapId + " held by " + player.getName()
                    + " would be flagged; no action taken.");
            return;
        }
        confiscateIfEnabled(player, mapItem, messageKey);
    }

    private void confiscateIfEnabled(Player player, ItemStack mapItem, String messageKey) {
        plugin.messages().send(player, messageKey);
        if (config().confiscate()) {
            player.getInventory().remove(mapItem);
        }
    }

    private MapArtSettings config() {
        return plugin.settings().mapArt();
    }

    static boolean shouldBypass(Player player) {
        return player.hasPermission(BYPASS_PERMISSION);
    }

    static boolean isCacheableResult(ModerationApiResult result) {
        return result.kind() == ModerationApiResult.Kind.CLEAR
                || result.kind() == ModerationApiResult.Kind.FLAGGED;
    }

    private boolean isFilledMap(ItemStack item) {
        return item != null && item.getType() == filledMapMaterial;
    }

    private static Material resolveFilledMapMaterial() {
        try {
            return Material.valueOf("FILLED_MAP");
        } catch (IllegalArgumentException ignored) {
            return Material.MAP;
        }
    }

    /** A thread-safe, insertion-ordered set that evicts its oldest entry past {@code maxSize}. */
    private static Set<Integer> boundedSet(int maxSize) {
        return Collections.synchronizedSet(Collections.newSetFromMap(
                new LinkedHashMap<>(16, 0.75f, false) {
                    @Override
                    protected boolean removeEldestEntry(Map.Entry<Integer, Boolean> eldest) {
                        return size() > maxSize;
                    }
                }));
    }

    private static ItemStack mainHand(PlayerInventory inventory) {
        try {
            Method method = inventory.getClass().getMethod("getItemInMainHand");
            return (ItemStack) method.invoke(inventory);
        } catch (ReflectiveOperationException ignored) {
            return inventory.getItemInHand();
        }
    }

    private static ItemStack offHand(PlayerInventory inventory) {
        try {
            Method method = inventory.getClass().getMethod("getItemInOffHand");
            return (ItemStack) method.invoke(inventory);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static Integer resolveMapId(MapMeta meta) {
        try {
            Method getMapView = meta.getClass().getMethod("getMapView");
            Object view = getMapView.invoke(meta);
            if (view instanceof MapView mapView) {
                return (int) mapView.getId();
            }
        } catch (ReflectiveOperationException ignored) {
            // Fall through to legacy map id.
        }
        try {
            Method getMapId = meta.getClass().getMethod("getMapId");
            Object id = getMapId.invoke(meta);
            if (id instanceof Number number) {
                return number.intValue();
            }
        } catch (ReflectiveOperationException ignored) {
            // Unsupported on this server version.
        }
        return null;
    }
}
