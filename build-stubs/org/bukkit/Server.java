package org.bukkit;

import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;

public interface Server {
    World getWorld(String name);
    PluginManager getPluginManager();
    BukkitScheduler getScheduler();
}
