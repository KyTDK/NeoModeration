package com.neomechanical.neomoderation.platform;

import com.neomechanical.neomoderation.NeoModerationPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.UUID;

/**
 * Manages the persistent, anonymous install identifier for this Minecraft server instance.
 *
 * <p>Persists the trial identity across server restarts. This local identifier is
 * not proof of a physical installation; trial eligibility is enforced by the service.
 */
public final class InstallIdentity {
    private static final String ID_FILE = ".install-id";
    private static volatile String cachedId;

    private InstallIdentity() {
    }

    public static synchronized String getOrCreate(NeoModerationPlugin plugin) {
        if (cachedId != null && !cachedId.isBlank()) {
            return cachedId;
        }

        File dataFolder = plugin.getDataFolder();
        if (dataFolder == null) {
            dataFolder = new File("plugins/NeoModeration");
        }
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }

        File file = new File(dataFolder, ID_FILE);
        if (file.exists()) {
            try {
                String existing = Files.readString(file.toPath(), StandardCharsets.UTF_8).trim();
                if (isValidUuid(existing)) {
                    cachedId = existing;
                    return cachedId;
                }
            } catch (IOException ignored) {
                // If unreadable, generate a fresh ID below
            }
        }

        String fresh = UUID.randomUUID().toString();
        try {
            Files.writeString(file.toPath(), fresh, StandardCharsets.UTF_8);
            cachedId = fresh;
        } catch (IOException ignored) {
            cachedId = fresh;
        }
        return cachedId;
    }

    private static boolean isValidUuid(String val) {
        try {
            UUID.fromString(val);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
