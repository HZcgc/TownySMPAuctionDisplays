package org.bukkit.event.player;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

public class PlayerInteractEntityEvent {
    public Player getPlayer() { return null; }
    public Entity getRightClicked() { return null; }
    public void setCancelled(boolean cancelled) {}
}
