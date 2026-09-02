package com.artillexstudios.axauctions.items;

import com.artillexstudios.axauctions.database.dtos.UserDto;
import org.bukkit.inventory.ItemStack;

public final class AuctionItem {
    private final int id;
    private final ItemStack item;
    private final UserDto seller;
    private final double price;
    private final long startTime;
    private final long expiryDate;
    private boolean expired;
    private boolean deleted;

    public AuctionItem(int id, ItemStack item, UserDto seller, double price, long startTime, long expiryDate) {
        this.id = id;
        this.item = item;
        this.seller = seller;
        this.price = price;
        this.startTime = startTime;
        this.expiryDate = expiryDate;
    }

    public int getId() { return id; }
    public ItemStack getItemStack() { return item; }
    public UserDto getSeller() { return seller; }
    public double getPrice() { return price; }
    public long getStartTime() { return startTime; }
    public long getExpiryDate() { return expiryDate; }
    public long getExpiryTime() { return expiryDate - System.currentTimeMillis(); }
    public boolean isExpired() { return expired; }
    public boolean isDeleted() { return deleted; }
    public void setExpired(boolean expired) { this.expired = expired; }
    public void setDeleted(boolean deleted) { this.deleted = deleted; }
}
