package org.bukkit.configuration;

import java.util.Set;

public interface ConfigurationSection {
    Set<String> getKeys(boolean deep);
}
