package org.bukkit;

import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.block.Block;

public interface World {
    String getName();
    Entity spawnEntity(Location location, EntityType type);
    Block getBlockAt(int x, int y, int z);
}
