package com.neomechanical.neomoderation.config;

import org.bukkit.configuration.ConfigurationSection;

import java.util.List;
import java.util.Map;

/**
 * A {@link ConfigView} backed by a Bukkit configuration, so the Bukkit entry
 * point keeps Bukkit's exact parsing and defaulting behaviour while the settings
 * records themselves stay platform-neutral.
 */
public final class BukkitConfigView implements ConfigView {

    private final ConfigurationSection config;

    public BukkitConfigView(ConfigurationSection config) {
        this.config = config;
    }

    @Override
    public Object get(String path) {
        return config.get(path);
    }

    @Override
    public boolean getBoolean(String path, boolean defaultValue) {
        return config.getBoolean(path, defaultValue);
    }

    @Override
    public String getString(String path, String defaultValue) {
        return config.getString(path, defaultValue);
    }

    @Override
    public int getInt(String path, int defaultValue) {
        return config.getInt(path, defaultValue);
    }

    @Override
    public double getDouble(String path, double defaultValue) {
        return config.getDouble(path, defaultValue);
    }

    @Override
    public List<String> getStringList(String path) {
        return config.getStringList(path);
    }

    @Override
    public List<Map<?, ?>> getMapList(String path) {
        return List.copyOf(config.getMapList(path));
    }
}
