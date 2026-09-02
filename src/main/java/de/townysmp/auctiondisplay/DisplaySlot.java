package de.townysmp.auctiondisplay;

import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;

final class DisplaySlot {
    final int index;
    final String worldName;
    final double x;
    final double y;
    final double z;
    final double frontX;
    final double frontZ;
    final int signX;
    final int signY;
    final int signZ;
    final BlockFace signFacing;
    ItemDisplay itemDisplay;
    Interaction interaction;
    AuctionListing listing;

    DisplaySlot(int index, String worldName, double x, double y, double z, double frontX, double frontZ,
                int signX, int signY, int signZ, BlockFace signFacing) {
        this.index = index;
        this.worldName = worldName;
        this.x = x;
        this.y = y;
        this.z = z;
        double length = Math.sqrt(frontX * frontX + frontZ * frontZ);
        this.frontX = length < 0.001D ? 0D : frontX / length;
        this.frontZ = length < 0.001D ? 1D : frontZ / length;
        this.signX = signX;
        this.signY = signY;
        this.signZ = signZ;
        this.signFacing = signFacing;
    }

    Location location(Server server) {
        World world = server.getWorld(worldName);
        return world == null ? null : new Location(world, x, y, z);
    }

    static BlockFace horizontalFace(double x, double z) {
        if (Math.abs(x) > Math.abs(z)) return x >= 0D ? BlockFace.EAST : BlockFace.WEST;
        return z >= 0D ? BlockFace.SOUTH : BlockFace.NORTH;
    }
}
