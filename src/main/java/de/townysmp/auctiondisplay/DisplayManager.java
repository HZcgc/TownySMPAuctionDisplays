package de.townysmp.auctiondisplay;

import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

final class DisplayManager {
    private final TownySMPAuctionDisplays plugin;
    private final AuctionBridge auctionBridge;
    private final List<DisplaySlot> slots = new ArrayList<>();
    private final Map<UUID, DisplaySlot> interactions = new HashMap<>();
    private final Map<UUID, Long> clickCooldowns = new HashMap<>();
    private BukkitTask refreshTask;
    private BukkitTask rotationTask;
    private float yaw;

    DisplayManager(TownySMPAuctionDisplays plugin, AuctionBridge auctionBridge) {
        this.plugin = plugin;
        this.auctionBridge = auctionBridge;
    }

    void start() {
        stop();
        loadSlots();
        long refreshTicks = Math.max(20L, plugin.getConfig().getLong("settings.update-interval-ticks", 100L));
        long rotationTicks = Math.max(1L, plugin.getConfig().getLong("settings.rotation-interval-ticks", 2L));
        refreshTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::refreshSafely, 1L, refreshTicks);
        rotationTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::rotateSafely, 1L, rotationTicks);
    }

    void stop() {
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
        if (rotationTask != null) {
            rotationTask.cancel();
            rotationTask = null;
        }
        for (DisplaySlot slot : slots) removeEntities(slot);
        interactions.clear();
        clickCooldowns.clear();
        slots.clear();
    }

    void restart() {
        start();
    }

    void refreshNow() {
        refreshSafely();
    }

    int configuredSlots() {
        return slots.size();
    }

    int occupiedSlots() {
        int result = 0;
        for (DisplaySlot slot : slots) {
            if (slot.listing != null) result++;
        }
        return result;
    }

    boolean isDisplayEntity(Entity entity) {
        return entity != null && interactions.containsKey(entity.getUniqueId());
    }

    void handleClick(Player player, Entity entity) {
        DisplaySlot slot = entity == null ? null : interactions.get(entity.getUniqueId());
        if (slot == null) return;

        long now = System.currentTimeMillis();
        long lastClick = clickCooldowns.getOrDefault(player.getUniqueId(), 0L);
        long cooldown = Math.max(250L, plugin.getConfig().getLong("settings.click-cooldown-millis", 750L));
        if (now - lastClick < cooldown) return;
        clickCooldowns.put(player.getUniqueId(), now);

        AuctionListing listing = slot.listing;
        if (listing == null) {
            String command = plugin.getConfig().getString("settings.empty-slot-command", "ah");
            if (command != null && !command.isBlank()) {
                player.performCommand(command.startsWith("/") ? command.substring(1) : command);
            }
            return;
        }

        AuctionBridge.PurchaseResult result = auctionBridge.purchase(player, listing.id);
        switch (result) {
            case OPENED -> {
                // AxAuctions now owns the confirmation and purchase flow.
            }
            case UNAVAILABLE -> {
                player.sendMessage(Colors.PREFIX + Colors.RED + "This auction is no longer available.");
                refreshSafely();
            }
            case OWN_LISTING -> player.sendMessage(Colors.PREFIX + Colors.YELLOW + "You cannot buy your own listing.");
            case NO_PERMISSION -> player.sendMessage(Colors.PREFIX + Colors.RED + "You do not have permission to use the auction house.");
            case ERROR -> player.sendMessage(Colors.PREFIX + Colors.RED + "The auction could not be opened. Please try /ah.");
        }
    }

    private void loadSlots() {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("slots");
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            int index;
            try {
                index = Integer.parseInt(key);
            } catch (NumberFormatException ignored) {
                plugin.getLogger().warning("Ignoring non-numeric display slot: " + key);
                continue;
            }
            if (index < 1) continue;
            String path = "slots." + key + ".";
            String world = plugin.getConfig().getString(path + "world");
            if (world == null || world.isBlank()) continue;
            slots.add(new DisplaySlot(
                    index,
                    world,
                    plugin.getConfig().getDouble(path + "x"),
                    plugin.getConfig().getDouble(path + "y"),
                    plugin.getConfig().getDouble(path + "z"),
                    plugin.getConfig().getDouble(path + "front-x"),
                    plugin.getConfig().getDouble(path + "front-z")
            ));
        }
        slots.sort(Comparator.comparingInt(slot -> slot.index));
        int minimum = Math.max(12, plugin.getConfig().getInt("settings.minimum-slots", 12));
        if (slots.size() < minimum) {
            plugin.getLogger().warning("Only " + slots.size() + " auction display slot(s) are configured; set at least "
                    + minimum + " with /ahdisplay set <1-" + minimum + ">.");
        }
    }

    private void refreshSafely() {
        try {
            List<AuctionListing> listings = auctionBridge.newest(slots.size());
            for (int position = 0; position < slots.size(); position++) {
                DisplaySlot slot = slots.get(position);
                AuctionListing next = position < listings.size() ? listings.get(position) : null;
                updateSlot(slot, next);
            }
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "Could not refresh auction displays", exception);
        }
    }

    private void rotateSafely() {
        try {
            float step = (float) plugin.getConfig().getDouble("settings.rotation-degrees-per-step", 1.8D);
            yaw = (yaw + step) % 360.0F;
            for (DisplaySlot slot : slots) {
                if (valid(slot.itemDisplay)) slot.itemDisplay.setRotation(yaw, 0.0F);
            }
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.FINE, "Could not rotate an auction display item", exception);
        }
    }

    private void updateSlot(DisplaySlot slot, AuctionListing next) {
        Location base = slot.location(plugin.getServer());
        if (base == null) {
            removeEntities(slot);
            return;
        }

        ensureInteraction(slot, base);
        ensureText(slot, base);

        slot.listing = next;
        if (next == null) {
            removeItem(slot);
        } else {
            ensureItem(slot, base);
            if (valid(slot.itemDisplay)) {
                slot.itemDisplay.setItemStack(next.item.clone());
            }
        }

        if (valid(slot.textDisplay)) slot.textDisplay.setText(label(slot.index, next));
    }

    private void ensureItem(DisplaySlot slot, Location base) {
        if (valid(slot.itemDisplay)) return;
        if (slot.itemDisplay != null) slot.itemDisplay.remove();
        slot.itemDisplay = null;
        Entity spawned = base.getWorld().spawnEntity(base, EntityType.ITEM_DISPLAY);
        if (!(spawned instanceof ItemDisplay display)) {
            spawned.remove();
            return;
        }
        configureEntity(display);
        display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
        display.setBillboard(Display.Billboard.FIXED);
        display.setShadowRadius(0.25F);
        display.setShadowStrength(0.65F);
        display.setViewRange(1.25F);
        scale(display, (float) plugin.getConfig().getDouble("settings.item-scale", 0.85D));
        slot.itemDisplay = display;
    }

    private void ensureText(DisplaySlot slot, Location base) {
        if (valid(slot.textDisplay)) return;
        if (slot.textDisplay != null) slot.textDisplay.remove();
        slot.textDisplay = null;
        double height = plugin.getConfig().getDouble("settings.text-height", 1.35D);
        Location textLocation = new Location(base.getWorld(), base.getX(), base.getY() + height, base.getZ());
        Entity spawned = base.getWorld().spawnEntity(textLocation, EntityType.TEXT_DISPLAY);
        if (!(spawned instanceof TextDisplay display)) {
            spawned.remove();
            return;
        }
        configureEntity(display);
        display.setBillboard(Display.Billboard.CENTER);
        display.setAlignment(TextDisplay.TextAlignment.CENTER);
        display.setSeeThrough(true);
        display.setShadowed(true);
        display.setDefaultBackground(false);
        display.setLineWidth(260);
        display.setViewRange(1.5F);
        slot.textDisplay = display;
    }

    private void ensureInteraction(DisplaySlot slot, Location base) {
        if (valid(slot.interaction)) return;
        if (slot.interaction != null) {
            interactions.remove(slot.interaction.getUniqueId());
            slot.interaction.remove();
        }
        slot.interaction = null;
        double forward = plugin.getConfig().getDouble("settings.interaction-forward-offset", 0.72D);
        double heightOffset = plugin.getConfig().getDouble("settings.interaction-y-offset", 0.25D);
        Location hitboxLocation = new Location(
                base.getWorld(),
                base.getX() + slot.frontX * forward,
                base.getY() + heightOffset,
                base.getZ() + slot.frontZ * forward
        );
        Entity spawned = base.getWorld().spawnEntity(hitboxLocation, EntityType.INTERACTION);
        if (!(spawned instanceof Interaction interaction)) {
            spawned.remove();
            return;
        }
        configureEntity(interaction);
        interaction.setInteractionWidth((float) plugin.getConfig().getDouble("settings.interaction-width", 1.35D));
        interaction.setInteractionHeight((float) plugin.getConfig().getDouble("settings.interaction-height", 2.25D));
        interaction.setResponsive(true);
        slot.interaction = interaction;
        interactions.put(interaction.getUniqueId(), slot);
    }

    private String label(int slot, AuctionListing listing) {
        if (listing == null) {
            return Colors.PINK + Colors.BOLD + "AUCTION " + Colors.GREEN + Colors.BOLD + "#" + slot
                    + "\n" + Colors.GRAY + "No listing available"
                    + "\n" + Colors.MUTED + "Click to open " + Colors.PINK + "/ah";
        }
        String itemName = itemName(listing.item);
        int amount = listing.item.getAmount();
        if (amount > 1) itemName += " x" + amount;
        return Colors.PINK + Colors.BOLD + "TOWNY" + Colors.GREEN + Colors.BOLD + "SMP "
                + Colors.GRAY + "• " + Colors.WHITE + "AUCTION #" + slot
                + "\n" + Colors.WHITE + itemName
                + "\n" + Colors.YELLOW + Colors.money(listing.price) + Colors.GRAY + " • "
                + Colors.CYAN + listing.seller
                + "\n" + Colors.MUTED + "Ends in " + Colors.WHITE
                + Colors.duration(listing.expiryDate - System.currentTimeMillis())
                + "\n" + Colors.GREEN + Colors.BOLD + "CLICK TO BUY";
    }

    private static String itemName(ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        if (meta != null && meta.hasDisplayName()) return Colors.clean(meta.getDisplayName());
        return Colors.materialName(stack.getType().name());
    }

    private static void configureEntity(Entity entity) {
        entity.setPersistent(false);
        entity.setInvulnerable(true);
        entity.setGravity(false);
        entity.setSilent(true);
    }

    private void removeItem(DisplaySlot slot) {
        if (slot.itemDisplay != null) slot.itemDisplay.remove();
        slot.itemDisplay = null;
    }

    private void removeEntities(DisplaySlot slot) {
        removeItem(slot);
        if (slot.textDisplay != null) slot.textDisplay.remove();
        if (slot.interaction != null) {
            interactions.remove(slot.interaction.getUniqueId());
            slot.interaction.remove();
        }
        slot.textDisplay = null;
        slot.interaction = null;
        slot.listing = null;
    }

    private static boolean valid(Entity entity) {
        return entity != null && entity.isValid() && !entity.isDead();
    }

    private void scale(ItemDisplay display, float amount) {
        try {
            Method getTransformation = display.getClass().getMethod("getTransformation");
            Object transformation = getTransformation.invoke(display);
            Object scale = transformation.getClass().getMethod("getScale").invoke(transformation);
            scale.getClass().getMethod("set", float.class, float.class, float.class)
                    .invoke(scale, amount, amount, amount);
            display.getClass().getMethod("setTransformation", transformation.getClass())
                    .invoke(display, transformation);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            plugin.getLogger().log(Level.FINE, "Item display scaling is unavailable on this Paper build", exception);
        }
    }
}
