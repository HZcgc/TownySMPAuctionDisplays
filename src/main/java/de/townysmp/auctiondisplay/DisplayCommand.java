package de.townysmp.auctiondisplay;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class DisplayCommand implements CommandExecutor, TabCompleter {
    private final TownySMPAuctionDisplays plugin;
    private final DisplayManager displays;

    DisplayCommand(TownySMPAuctionDisplays plugin, DisplayManager displays) {
        this.plugin = plugin;
        this.displays = displays;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("townysmp.auctiondisplay.admin")) {
            sender.sendMessage(Colors.PREFIX + Colors.RED + "You do not have permission for this command.");
            return true;
        }
        if (args.length == 0) {
            help(sender, label);
            return true;
        }

        String action = args[0].toLowerCase(Locale.ROOT);
        switch (action) {
            case "set" -> set(sender, args);
            case "remove", "delete" -> remove(sender, args);
            case "refresh" -> {
                displays.refreshNow();
                sender.sendMessage(Colors.PREFIX + Colors.GREEN + "Auction displays refreshed.");
            }
            case "reload" -> {
                plugin.reloadPlugin();
                sender.sendMessage(Colors.PREFIX + Colors.GREEN + "Configuration and displays reloaded.");
            }
            case "status" -> sender.sendMessage(Colors.PREFIX + Colors.WHITE + "Configured: "
                    + Colors.GREEN + displays.configuredSlots() + Colors.GRAY + " | " + Colors.WHITE + "Occupied: "
                    + Colors.YELLOW + displays.occupiedSlots() + Colors.GRAY + " | " + Colors.WHITE + "Minimum: "
                    + Colors.PINK + Math.max(12, plugin.getConfig().getInt("settings.minimum-slots", 12)));
            default -> help(sender, label);
        }
        return true;
    }

    private void set(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Colors.PREFIX + Colors.RED + "This command must be used in game.");
            return;
        }
        Integer slot = slot(args);
        if (slot == null) {
            sender.sendMessage(Colors.PREFIX + Colors.RED + "Usage: /ahdisplay set <slot> (use at least 1-12)");
            return;
        }
        Block block = player.getTargetBlockExact(8);
        if (block == null) {
            sender.sendMessage(Colors.PREFIX + Colors.RED + "Look directly at the pedestal block inside the showcase.");
            return;
        }

        double x = block.getX() + 0.5D;
        double y = block.getY() + plugin.getConfig().getDouble("settings.item-y-offset", 1.25D);
        double z = block.getZ() + 0.5D;
        Location playerLocation = player.getLocation();
        double frontX = playerLocation.getX() - x;
        double frontZ = playerLocation.getZ() - z;
        double length = Math.sqrt(frontX * frontX + frontZ * frontZ);
        if (length < 0.001D) {
            frontX = 0D;
            frontZ = 1D;
        } else {
            frontX /= length;
            frontZ /= length;
        }

        String path = "slots." + slot + ".";
        plugin.getConfig().set(path + "world", block.getWorld().getName());
        plugin.getConfig().set(path + "x", x);
        plugin.getConfig().set(path + "y", y);
        plugin.getConfig().set(path + "z", z);
        plugin.getConfig().set(path + "front-x", frontX);
        plugin.getConfig().set(path + "front-z", frontZ);
        plugin.saveConfig();
        displays.restart();
        sender.sendMessage(Colors.PREFIX + Colors.GREEN + "Showcase slot #" + slot + " saved. "
                + Colors.MUTED + "Its clickable side faces your current position.");
    }

    private void remove(CommandSender sender, String[] args) {
        Integer slot = slot(args);
        if (slot == null) {
            sender.sendMessage(Colors.PREFIX + Colors.RED + "Usage: /ahdisplay remove <slot>");
            return;
        }
        plugin.getConfig().set("slots." + slot, null);
        plugin.saveConfig();
        displays.restart();
        sender.sendMessage(Colors.PREFIX + Colors.GREEN + "Showcase slot #" + slot + " removed.");
    }

    private static Integer slot(String[] args) {
        if (args.length < 2) return null;
        try {
            int value = Integer.parseInt(args[1]);
            return value > 0 && value <= 250 ? value : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static void help(CommandSender sender, String label) {
        sender.sendMessage(Colors.PINK + Colors.BOLD + "TOWNY" + Colors.GREEN + Colors.BOLD + "SMP "
                + Colors.WHITE + "Auction Displays");
        sender.sendMessage(Colors.GRAY + "/" + label + " set <slot> " + Colors.MUTED + "- look at the pedestal and save it");
        sender.sendMessage(Colors.GRAY + "/" + label + " remove <slot> " + Colors.MUTED + "- remove one showcase");
        sender.sendMessage(Colors.GRAY + "/" + label + " refresh|reload|status");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("townysmp.auctiondisplay.admin")) return List.of();
        if (args.length == 1) return filter(List.of("set", "remove", "refresh", "reload", "status"), args[0]);
        if (args.length == 2 && (args[0].equalsIgnoreCase("set") || args[0].equalsIgnoreCase("remove"))) {
            List<String> values = new ArrayList<>();
            for (int slot = 1; slot <= 12; slot++) values.add(String.valueOf(slot));
            return filter(values, args[1]);
        }
        return List.of();
    }

    private static List<String> filter(List<String> values, String input) {
        String prefix = input.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String value : values) if (value.toLowerCase(Locale.ROOT).startsWith(prefix)) result.add(value);
        return result;
    }
}
