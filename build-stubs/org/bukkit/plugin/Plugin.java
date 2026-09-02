package org.bukkit.plugin;

public interface Plugin {
    default boolean isEnabled() { return true; }
}
