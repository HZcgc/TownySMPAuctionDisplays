package de.townysmp.auctiondisplay;

import org.bukkit.inventory.ItemStack;

import java.util.UUID;

final class AuctionListing {
    final int id;
    final ItemStack item;
    final double price;
    final String seller;
    final UUID sellerUuid;
    final long startTime;
    final long expiryDate;
    final Object handle;

    AuctionListing(int id, ItemStack item, double price, String seller, UUID sellerUuid,
                   long startTime, long expiryDate, Object handle) {
        this.id = id;
        this.item = item;
        this.price = price;
        this.seller = seller;
        this.sellerUuid = sellerUuid;
        this.startTime = startTime;
        this.expiryDate = expiryDate;
        this.handle = handle;
    }
}
