package de.townysmp.auctiondisplay;

import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

public final class TownySMPAuctionDisplays extends JavaPlugin implements Listener {
    private AuctionBridge auctionBridge;
    private DisplayManager displayManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        auctionBridge = new AuctionBridge(getLogger(),
                getConfig().getLong("settings.fallback-listing-duration-seconds", 172_800L));
        try {
            auctionBridge.initialize();
        } catch (ReflectiveOperationException exception) {
            getLogger().log(Level.SEVERE, "Unsupported AxAuctions build: its auction or purchase API could not be found.", exception);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        displayManager = new DisplayManager(this, auctionBridge);
        DisplayCommand displayCommand = new DisplayCommand(this, displayManager);
        PluginCommand command = getCommand("ahdisplay");
        if (command == null) {
            getLogger().severe("Command ahdisplay is missing from plugin.yml.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        command.setExecutor(displayCommand);
        command.setTabCompleter(displayCommand);
        getServer().getPluginManager().registerEvents(this, this);
        displayManager.start();
        getLogger().info("TownySMP auction showcases enabled. Configure at least 12 with /ahdisplay set <slot>.");
    }

    @Override
    public void onDisable() {
        if (displayManager != null) displayManager.stop();
    }

    void reloadPlugin() {
        reloadConfig();
        auctionBridge.setFallbackExpireSeconds(
                getConfig().getLong("settings.fallback-listing-duration-seconds", 172_800L));
        displayManager.restart();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onRightClick(PlayerInteractEntityEvent event) {
        if (displayManager == null || !displayManager.isDisplayEntity(event.getRightClicked())) return;
        event.setCancelled(true);
        displayManager.handleClick(event.getPlayer(), event.getRightClicked());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onRightClickAt(PlayerInteractAtEntityEvent event) {
        if (displayManager == null || !displayManager.isDisplayEntity(event.getRightClicked())) return;
        event.setCancelled(true);
        displayManager.handleClick(event.getPlayer(), event.getRightClicked());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onLeftClick(EntityDamageByEntityEvent event) {
        if (displayManager == null || !displayManager.isDisplayEntity(event.getEntity())) return;
        event.setCancelled(true);
        if (event.getDamager() instanceof Player player) {
            displayManager.handleClick(player, event.getEntity());
        }
    }
}
