package com.artillexstudios.axauctions.items.actions;

import com.artillexstudios.axauctions.items.AuctionItem;
import org.bukkit.entity.Player;

public final class PurchaseAuction {
    public static int builds;
    public static int lastListingId;

    private final AuctionItem item;
    private final PaginatedMenu lastGui;

    PurchaseAuction(Object lastGui, AuctionItem item, int page, Player player) {
        this.item = item;
        this.lastGui = (PaginatedMenu) lastGui;
    }

    public void build() {
        lastGui.reopen();
        builds++;
        lastListingId = item.getId();
    }

    public interface PaginatedMenu {
        void reopen();
    }
}
