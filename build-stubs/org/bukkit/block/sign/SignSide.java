package org.bukkit.block.sign;

public interface SignSide {
    @Deprecated
    void setLine(int index, String line);
    void setGlowingText(boolean glowing);
}
