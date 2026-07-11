package com.neomechanical.neomoderation.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ModerationModeTest {
    @Test
    void parseIsCaseInsensitiveAndDefaultsToEnforce() {
        assertEquals(ModerationMode.MONITOR, ModerationMode.parse("monitor"));
        assertEquals(ModerationMode.MONITOR, ModerationMode.parse("MONITOR"));
        assertEquals(ModerationMode.MONITOR, ModerationMode.parse(" Monitor "));
        assertEquals(ModerationMode.ENFORCE, ModerationMode.parse("enforce"));
        assertEquals(ModerationMode.ENFORCE, ModerationMode.parse(null));
        assertEquals(ModerationMode.ENFORCE, ModerationMode.parse(""));
        assertEquals(ModerationMode.ENFORCE, ModerationMode.parse("bogus"));
    }

    @Test
    void missingModeKeyKeepsExistingServersEnforcing() {
        YamlConfiguration config = new YamlConfiguration();
        assertEquals(ModerationMode.ENFORCE, ModerationSettings.from(config).mode());
    }

    @Test
    void monitorModeIsReadFromConfig() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("moderation.mode", "monitor");
        assertEquals(ModerationMode.MONITOR, ModerationSettings.from(config).mode());
    }
}
