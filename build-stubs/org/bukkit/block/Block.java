package org.bukkit.block;

import org.bukkit.World;

public interface Block {
    int getX();
    int getY();
    int getZ();
    World getWorld();
}
