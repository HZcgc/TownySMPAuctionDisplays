package org.bukkit.inventory;

import org.bukkit.Material;
import org.bukkit.inventory.meta.ItemMeta;

public class ItemStack implements Cloneable {
    public ItemStack clone() { return this; }
    public int getAmount() { return 1; }
    public ItemMeta getItemMeta() { return null; }
    public Material getType() { return null; }
}
