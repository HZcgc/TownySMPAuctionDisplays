package org.bukkit.block;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;

public interface Block {
    int getX();
    int getY();
    int getZ();
    World getWorld();
    Block getRelative(BlockFace face);
    Material getType();
    void setType(Material material, boolean applyPhysics);
    BlockData getBlockData();
    void setBlockData(BlockData data, boolean applyPhysics);
    BlockState getState();
}
