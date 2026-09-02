package org.bukkit.event.entity;

import org.bukkit.entity.Entity;

public class EntityDamageByEntityEvent {
    public Entity getDamager() { return null; }
    public Entity getEntity() { return null; }
    public void setCancelled(boolean cancelled) {}
}
