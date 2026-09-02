package org.bukkit.event.player;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;

public class PlayerInteractEvent {
    public Player getPlayer() { return null; }
    public Action getAction() { return null; }
    public Block getClickedBlock() { return null; }
    public void setCancelled(boolean cancelled) {}
}
