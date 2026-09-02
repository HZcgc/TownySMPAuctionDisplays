package org.bukkit;

import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;

public interface World {
    String getName();
    Entity spawnEntity(Location location, EntityType type);
}
