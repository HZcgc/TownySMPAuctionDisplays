package org.bukkit.block;

import org.bukkit.block.sign.Side;
import org.bukkit.block.sign.SignSide;

public interface Sign extends BlockState {
    SignSide getSide(Side side);
    void setWaxed(boolean waxed);
}
