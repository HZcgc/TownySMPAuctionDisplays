package com.artillexstudios.axauctions.items.actions;

import com.artillexstudios.axauctions.items.AuctionItem;
import org.bukkit.entity.Player;

public final class PurchaseAuction {
    public static int builds;
    public static int lastListingId;

    private final AuctionItem item;

    PurchaseAuction(Object lastGui, AuctionItem item, int page, Player player) {
        this.item = item;
    }

    public void build() {
        builds++;
        lastListingId = item.getId();
    }
}
