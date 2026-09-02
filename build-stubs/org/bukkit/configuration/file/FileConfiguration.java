package org.bukkit.configuration.file;

import org.bukkit.configuration.ConfigurationSection;

public abstract class FileConfiguration implements ConfigurationSection {
    public abstract ConfigurationSection getConfigurationSection(String path);
    public abstract long getLong(String path, long def);
    public abstract int getInt(String path, int def);
    public abstract double getDouble(String path);
    public abstract double getDouble(String path, double def);
    public abstract String getString(String path);
    public abstract String getString(String path, String def);
    public abstract boolean getBoolean(String path, boolean def);
    public abstract boolean contains(String path);
    public abstract boolean isSet(String path);
    public abstract void set(String path, Object value);
}
