package de.townysmp.auctiondisplay;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.block.sign.Side;
import org.bukkit.block.sign.SignSide;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

final class DisplayManager {
    private final TownySMPAuctionDisplays plugin;
    private final AuctionBridge auctionBridge;
    private final List<DisplaySlot> slots = new ArrayList<>();
    private final Map<UUID, DisplaySlot> interactions = new HashMap<>();
    private final Map<BlockKey, DisplaySlot> signBlocks = new HashMap<>();
    private final Set<BlockKey> warnedBlockedSigns = new HashSet<>();
    private final Map<UUID, Long> clickCooldowns = new HashMap<>();
    private BukkitTask refreshTask;
    private BukkitTask rotationTask;
    private Material signMaterial = Material.DARK_OAK_WALL_SIGN;
    private float yaw;

    DisplayManager(TownySMPAuctionDisplays plugin, AuctionBridge auctionBridge) {
        this.plugin = plugin;
        this.auctionBridge = auctionBridge;
    }

    void start() {
        stop();
        signMaterial = configuredSignMaterial();
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
        signBlocks.clear();
        warnedBlockedSigns.clear();
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
        handleSlotClick(player, slot);
    }

    boolean handleSignClick(Player player, Block block) {
        if (block == null || !isWallSign(block.getType())) return false;
        DisplaySlot slot = signBlocks.get(BlockKey.of(block));
        if (slot == null) return false;
        handleSlotClick(player, slot);
        return true;
    }

    boolean canUseSignBlock(int slotIndex, Block block) {
        if (block == null) return false;
        if (block.getType().isAir()) return true;
        DisplaySlot owner = signBlocks.get(BlockKey.of(block));
        return isWallSign(block.getType()) && owner != null && owner.index == slotIndex;
    }

    void removeSign(int slotIndex) {
        for (DisplaySlot slot : slots) {
            if (slot.index != slotIndex) continue;
            BlockKey key = BlockKey.of(slot);
            World world = plugin.getServer().getWorld(slot.worldName);
            if (world != null) {
                Block block = world.getBlockAt(slot.signX, slot.signY, slot.signZ);
                if (isWallSign(block.getType()) && signBlocks.get(key) == slot) {
                    block.setType(Material.AIR, false);
                }
            }
            signBlocks.remove(key);
            warnedBlockedSigns.remove(key);
            return;
        }
    }

    private void handleSlotClick(Player player, DisplaySlot slot) {

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
        boolean migrated = false;
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
            double x = plugin.getConfig().getDouble(path + "x");
            double y = plugin.getConfig().getDouble(path + "y");
            double z = plugin.getConfig().getDouble(path + "z");
            double frontX = plugin.getConfig().getDouble(path + "front-x");
            double frontZ = plugin.getConfig().getDouble(path + "front-z");
            org.bukkit.block.BlockFace facing = parseFacing(plugin.getConfig().getString(path + "sign-facing"));
            boolean invalidFacing = facing == null;
            if (invalidFacing) facing = DisplaySlot.horizontalFace(frontX, frontZ);
            double alignedFrontX = facing.getModX();
            double alignedFrontZ = facing.getModZ();
            int defaultSignX = (int) Math.floor(x) + facing.getModX();
            int defaultSignY = (int) Math.floor(y) - 1;
            int defaultSignZ = (int) Math.floor(z) + facing.getModZ();
            int signX = plugin.getConfig().getInt(path + "sign-x", defaultSignX);
            int signY = plugin.getConfig().getInt(path + "sign-y", defaultSignY);
            int signZ = plugin.getConfig().getInt(path + "sign-z", defaultSignZ);

            if (invalidFacing
                    || !plugin.getConfig().contains(path + "sign-x")
                    || !plugin.getConfig().contains(path + "sign-y")
                    || !plugin.getConfig().contains(path + "sign-z")
                    || Math.abs(frontX - alignedFrontX) > 0.0001D
                    || Math.abs(frontZ - alignedFrontZ) > 0.0001D) {
                plugin.getConfig().set(path + "sign-facing", facing.name());
                plugin.getConfig().set(path + "sign-x", signX);
                plugin.getConfig().set(path + "sign-y", signY);
                plugin.getConfig().set(path + "sign-z", signZ);
                plugin.getConfig().set(path + "front-x", alignedFrontX);
                plugin.getConfig().set(path + "front-z", alignedFrontZ);
                migrated = true;
            }

            DisplaySlot slot = new DisplaySlot(
                    index,
                    world,
                    x,
                    y,
                    z,
                    alignedFrontX,
                    alignedFrontZ,
                    signX,
                    signY,
                    signZ,
                    facing
            );
            slots.add(slot);
            DisplaySlot duplicate = signBlocks.put(BlockKey.of(slot), slot);
            if (duplicate != null) {
                plugin.getLogger().warning("Auction display slots #" + duplicate.index + " and #" + slot.index
                        + " use the same sign block; the latter slot owns its clicks.");
            }
        }
        slots.sort(Comparator.comparingInt(slot -> slot.index));
        if (migrated) {
            plugin.saveConfig();
            plugin.getLogger().info("Added physical sign positions to existing auction display slots.");
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

        slot.listing = next;
        if (next == null) {
            removeItem(slot);
        } else {
            ensureItem(slot, base);
            if (valid(slot.itemDisplay)) {
                slot.itemDisplay.setItemStack(next.item.clone());
            }
        }

        updateSign(slot, next);
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

    @SuppressWarnings("deprecation")
    private void updateSign(DisplaySlot slot, AuctionListing listing) {
        World world = plugin.getServer().getWorld(slot.worldName);
        if (world == null) return;
        Block block = world.getBlockAt(slot.signX, slot.signY, slot.signZ);
        BlockKey key = BlockKey.of(slot);
        if (block.getType().isAir()) block.setType(signMaterial, false);
        if (!isWallSign(block.getType())) {
            if (warnedBlockedSigns.add(key)) {
                plugin.getLogger().warning("Cannot place auction sign for slot #" + slot.index + " at "
                        + slot.worldName + " " + slot.signX + "," + slot.signY + "," + slot.signZ
                        + ": the block is occupied. Clear it or set the slot again.");
            }
            return;
        }
        warnedBlockedSigns.remove(key);

        BlockData data = block.getBlockData();
        if (data instanceof WallSign wallSign) {
            wallSign.setFacing(slot.signFacing);
            block.setBlockData(wallSign, false);
        }
        if (!(block.getState() instanceof Sign sign)) return;
        SignSide front = sign.getSide(Side.FRONT);
        String[] lines = signLines(slot.index, listing);
        for (int line = 0; line < lines.length; line++) front.setLine(line, lines[line]);
        front.setGlowingText(plugin.getConfig().getBoolean("settings.sign-glowing-text", false));
        sign.setWaxed(true);
        sign.update(true, false);
    }

    private String[] signLines(int slot, AuctionListing listing) {
        String heading = Colors.PINK + "Auction " + Colors.GRAY + "#" + slot;
        if (listing == null) {
            return new String[]{
                    heading,
                    Colors.GRAY + "No listing",
                    "",
                    Colors.PINK + "[ OPEN /AH ]"
            };
        }
        String itemName = itemName(listing.item);
        int amount = listing.item.getAmount();
        String amountSuffix = amount > 1 ? " x" + amount : "";
        itemName = shorten(itemName, Math.max(3, 15 - amountSuffix.length())) + amountSuffix;
        String price = shorten(Colors.money(listing.price), 7);
        int sellerLength = Math.max(3, 15 - price.length() - 3);
        String seller = shorten(listing.seller, sellerLength);
        return new String[]{
                heading,
                Colors.WHITE + itemName,
                Colors.YELLOW + price + Colors.GRAY + " | " + Colors.CYAN + seller,
                Colors.GREEN + shorten(plugin.getConfig().getString(
                        "settings.sign-buy-text", "RIGHT-CLICK BUY"), 15)
        };
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
        if (slot.interaction != null) {
            interactions.remove(slot.interaction.getUniqueId());
            slot.interaction.remove();
        }
        slot.interaction = null;
        slot.listing = null;
    }

    private Material configuredSignMaterial() {
        String configured = plugin.getConfig().getString("settings.sign-material", "DARK_OAK_WALL_SIGN");
        try {
            Material material = Material.valueOf(configured.toUpperCase(Locale.ROOT));
            if (isWallSign(material)) return material;
        } catch (IllegalArgumentException ignored) {
            // The warning below contains the actionable fallback.
        }
        plugin.getLogger().warning("Invalid settings.sign-material '" + configured
                + "'; using DARK_OAK_WALL_SIGN.");
        return Material.DARK_OAK_WALL_SIGN;
    }

    private static org.bukkit.block.BlockFace parseFacing(String configured) {
        if (configured == null) return null;
        try {
            org.bukkit.block.BlockFace face = org.bukkit.block.BlockFace.valueOf(configured.toUpperCase(Locale.ROOT));
            return face.getModY() == 0 && (face.getModX() != 0 || face.getModZ() != 0) ? face : null;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static boolean isWallSign(Material material) {
        return material != null && material.name().endsWith("_WALL_SIGN");
    }

    private static String shorten(String value, int maximum) {
        String safe = value == null || value.isBlank() ? "Unknown" : value.trim();
        if (safe.length() <= maximum) return safe;
        if (maximum <= 3) return safe.substring(0, maximum);
        return safe.substring(0, maximum - 3) + "...";
    }

    private record BlockKey(String world, int x, int y, int z) {
        static BlockKey of(Block block) {
            return new BlockKey(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
        }

        static BlockKey of(DisplaySlot slot) {
            return new BlockKey(slot.worldName, slot.signX, slot.signY, slot.signZ);
        }
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
