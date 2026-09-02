package org.bukkit;

public enum Material {
    AIR,
    DARK_OAK_WALL_SIGN,
    DIAMOND,
    DIAMOND_SWORD;

    public boolean isAir() {
        return this == AIR;
    }
}
