package com.neomechanical.neomoderation;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class LocaleParityTest {
    @Test
    void bundledLocalesContainTheSameKeysAsEnglish() {
        File root = new File("src/main/resources/locale");
        YamlConfiguration english = YamlConfiguration.loadConfiguration(new File(root, "en_US.yml"));
        Set<String> expected = english.getKeys(true);

        assertFalse(expected.isEmpty(), "en_US locale must not be empty");
        for (File locale : root.listFiles((dir, name) -> name.endsWith(".yml") && !name.equals("en_US.yml"))) {
            YamlConfiguration translated = YamlConfiguration.loadConfiguration(locale);
            assertEquals(expected, translated.getKeys(true), locale.getName() + " must match en_US keys");
        }
    }
}
