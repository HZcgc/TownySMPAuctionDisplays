package com.artillexstudios.axauctions.items;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class AuctionManager {
    private static final ConcurrentHashMap<Integer, AuctionItem> ITEMS = new ConcurrentHashMap<>();

    private AuctionManager() {}

    public static ConcurrentHashMap<Integer, AuctionItem> getItems() { return ITEMS; }
    public static Optional<AuctionItem> getItemByID(int id) { return Optional.ofNullable(ITEMS.get(id)); }
}
