package org.bukkit.entity;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.CommandSender;

public interface Player extends Entity, CommandSender {
    boolean performCommand(String command);
    Block getTargetBlockExact(int maxDistance);
    BlockFace getTargetBlockFace(int maxDistance);
    Location getLocation();
}
