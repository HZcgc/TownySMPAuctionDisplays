package de.townysmp.auctiondisplay;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.logging.Level;

final class SellMessageBridge {
    private final TownySMPAuctionDisplays plugin;
    private Method getPlayer;
    private Method getItem;
    private Method getItemStack;
    private Method getPrice;

    SellMessageBridge(TownySMPAuctionDisplays plugin) {
        this.plugin = plugin;
    }

    @SuppressWarnings("unchecked")
    void register(ClassLoader axAuctionsClassLoader) throws ReflectiveOperationException {
        Class<?> rawEvent = Class.forName(
                "com.artillexstudios.axauctions.api.events.AxAuctionsSellEvent",
                true,
                axAuctionsClassLoader
        );
        if (!Event.class.isAssignableFrom(rawEvent)) {
            throw new ClassNotFoundException(rawEvent.getName() + " is not a Bukkit event");
        }
        Class<? extends Event> eventClass = (Class<? extends Event>) rawEvent;
        this.getPlayer = rawEvent.getMethod("getPlayer");
        this.getItem = rawEvent.getMethod("getItem");
        Class<?> auctionItem = getItem.getReturnType();
        this.getItemStack = auctionItem.getMethod("getItemStack");
        this.getPrice = auctionItem.getMethod("getPrice");

        plugin.getServer().getPluginManager().registerEvent(
                eventClass,
                plugin,
                EventPriority.MONITOR,
                (listener, event) -> onSell(event),
                plugin,
                true
        );
        plugin.getLogger().info("Registered the TownySMP item-name message for successful auction listings.");
    }

    private void onSell(Event event) {
        if (!plugin.getConfig().getBoolean("settings.custom-sell-message-enabled", true)) return;
        try {
            Object playerObject = getPlayer.invoke(event);
            Object auctionItem = getItem.invoke(event);
            Object stackObject = getItemStack.invoke(auctionItem);
            Object priceObject = getPrice.invoke(auctionItem);
            if (!(playerObject instanceof Player player)
                    || !(stackObject instanceof ItemStack stack)
                    || !(priceObject instanceof Number price)) return;

            String message = successMessage(stack, price.doubleValue());
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (player.isValid() && !player.isDead()) player.sendMessage(message);
            });
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause() == null ? exception : exception.getCause();
            plugin.getLogger().log(Level.WARNING, "Could not format the AxAuctions sell message", cause);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "Could not format the AxAuctions sell message", exception);
        }
    }

    static String successMessage(ItemStack stack, double price) {
        int amount = Math.max(1, stack.getAmount());
        return Colors.PREFIX + Colors.GREEN + "Listed " + Colors.WHITE + amount + "x "
                + itemName(stack) + Colors.GREEN + " for " + Colors.WHITE + Colors.money(price)
                + Colors.GREEN + ".";
    }

    static String itemName(ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            if (meta.hasDisplayName()) return Colors.clean(meta.getDisplayName());
            String itemName = modernItemName(meta);
            if (itemName != null && !itemName.isBlank()) return Colors.clean(itemName);
        }
        return Colors.materialName(stack.getType().name());
    }

    private static String modernItemName(ItemMeta meta) {
        try {
            Method hasItemName = meta.getClass().getMethod("hasItemName");
            if (!Boolean.TRUE.equals(hasItemName.invoke(meta))) return null;
            Object value = meta.getClass().getMethod("getItemName").invoke(meta);
            return value instanceof String string ? string : null;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }
}
