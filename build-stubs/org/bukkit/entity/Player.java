package org.bukkit.entity;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;

public interface Player extends Entity, CommandSender {
    boolean performCommand(String command);
    Block getTargetBlockExact(int maxDistance);
    Location getLocation();
}
