package com.neomechanical.neomoderation.moderation;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerMuteService {
    private final ConcurrentHashMap<UUID, Long> mutedUntilMillis = new ConcurrentHashMap<>();
    private final File storageFile;
    private final Object diskLock = new Object();

    public PlayerMuteService(JavaPlugin plugin) {
        this(new File(plugin.getDataFolder(), "mutes.yml"));
    }

    PlayerMuteService(File storageFile) {
        this.storageFile = storageFile;
        load();
    }

    public void mute(UUID playerId, int durationSeconds) {
        long durationMs = Math.max(1L, durationSeconds) * 1000L;
        mutedUntilMillis.put(playerId, System.currentTimeMillis() + durationMs);
        save();
    }

    public boolean isMuted(UUID playerId) {
        Long until = mutedUntilMillis.get(playerId);
        if (until == null) {
            return false;
        }
        if (until <= System.currentTimeMillis()) {
            if (mutedUntilMillis.remove(playerId, until)) {
                save();
            }
            return false;
        }
        return true;
    }

    public int remainingSeconds(UUID playerId) {
        Long until = mutedUntilMillis.get(playerId);
        if (until == null) {
            return 0;
        }
        long remainingMs = until - System.currentTimeMillis();
        if (remainingMs <= 0) {
            if (mutedUntilMillis.remove(playerId, until)) {
                save();
            }
            return 0;
        }
        return (int) Math.ceil(remainingMs / 1000.0);
    }

    public void unmute(UUID playerId) {
        if (mutedUntilMillis.remove(playerId) != null) {
            save();
        }
    }

    private void load() {
        if (storageFile == null || !storageFile.exists()) {
            return;
        }
        synchronized (diskLock) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(storageFile);
            long now = System.currentTimeMillis();
            for (String key : yaml.getKeys(false)) {
                try {
                    UUID id = UUID.fromString(key);
                    long until = yaml.getLong(key, 0L);
                    if (until > now) {
                        mutedUntilMillis.put(id, until);
                    }
                } catch (IllegalArgumentException ignored) {
                    // Skip malformed UUID keys.
                }
            }
        }
    }

    private void save() {
        if (storageFile == null) {
            return;
        }
        synchronized (diskLock) {
            YamlConfiguration yaml = new YamlConfiguration();
            long now = System.currentTimeMillis();
            for (Map.Entry<UUID, Long> entry : mutedUntilMillis.entrySet()) {
                if (entry.getValue() > now) {
                    yaml.set(entry.getKey().toString(), entry.getValue());
                }
            }
            try {
                File parent = storageFile.getParentFile();
                if (parent != null && !parent.exists()) {
                    parent.mkdirs();
                }
                Path target = storageFile.toPath();
                Path temp = target.resolveSibling(storageFile.getName() + ".tmp");
                yaml.save(temp.toFile());
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException ignored) {
                try {
                    yaml.save(storageFile);
                } catch (IOException ignoredAgain) {
                    // Best-effort persistence.
                }
            }
        }
    }
}
