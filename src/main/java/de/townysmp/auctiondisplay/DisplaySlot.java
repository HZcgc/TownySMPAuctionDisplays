package de.townysmp.auctiondisplay;

import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;

final class DisplaySlot {
    final int index;
    final String worldName;
    final double x;
    final double y;
    final double z;
    final double frontX;
    final double frontZ;
    ItemDisplay itemDisplay;
    TextDisplay textDisplay;
    Interaction interaction;
    AuctionListing listing;

    DisplaySlot(int index, String worldName, double x, double y, double z, double frontX, double frontZ) {
        this.index = index;
        this.worldName = worldName;
        this.x = x;
        this.y = y;
        this.z = z;
        double length = Math.sqrt(frontX * frontX + frontZ * frontZ);
        this.frontX = length < 0.001D ? 0D : frontX / length;
        this.frontZ = length < 0.001D ? 1D : frontZ / length;
    }

    Location location(Server server) {
        World world = server.getWorld(worldName);
        return world == null ? null : new Location(world, x, y, z);
    }
}
