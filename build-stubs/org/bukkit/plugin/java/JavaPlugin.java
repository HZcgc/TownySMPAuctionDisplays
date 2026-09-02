package org.bukkit.plugin.java;

import org.bukkit.Server;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

import java.util.logging.Logger;

public abstract class JavaPlugin implements Plugin {
    public void onEnable() {}
    public void onDisable() {}
    public void saveDefaultConfig() {}
    public void reloadConfig() {}
    public void saveConfig() {}
    public FileConfiguration getConfig() { return null; }
    public Server getServer() { return null; }
    public Logger getLogger() { return null; }
    public PluginCommand getCommand(String name) { return null; }
}
