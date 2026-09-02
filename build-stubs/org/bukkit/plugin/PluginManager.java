package org.bukkit.plugin;

import org.bukkit.event.Event;
import org.bukkit.event.EventExecutor;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public interface PluginManager {
    Plugin getPlugin(String name);
    void registerEvents(Listener listener, Plugin plugin);
    void registerEvent(Class<? extends Event> event, Listener listener, EventPriority priority,
                       EventExecutor executor, Plugin plugin, boolean ignoreCancelled);
    void disablePlugin(Plugin plugin);
}
