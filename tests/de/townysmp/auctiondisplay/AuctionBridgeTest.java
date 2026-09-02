package de.townysmp.auctiondisplay;

import com.artillexstudios.axauctions.database.dtos.UserDto;
import com.artillexstudios.axauctions.items.AuctionItem;
import com.artillexstudios.axauctions.items.AuctionManager;
import com.artillexstudios.axauctions.items.actions.PurchaseAuction;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

public final class AuctionBridgeTest {
    public static void main(String[] args) throws Exception {
        long now = System.currentTimeMillis();
        UUID sellerUuid = UUID.randomUUID();
        UserDto seller = new UserDto("Seller", sellerUuid);
        for (int id = 1; id <= 15; id++) {
            AuctionManager.getItems().put(id,
                    new AuctionItem(id, new ItemStack(), seller, id * 100D, now + id, now + 172_800_000L));
        }
        AuctionManager.getItems().get(15).setExpired(true);
        AuctionManager.getItems().get(14).setDeleted(true);

        AuctionBridge bridge = new AuctionBridge(Logger.getLogger("test"), 172_800L);
        bridge.initialize();
        List<AuctionListing> newest = bridge.newest(12);
        require(newest.size() == 12, "Expected exactly 12 active listings");
        require(newest.get(0).id == 13, "Newest active listing must be first");
        require(newest.get(11).id == 2, "The thirteenth active listing must be trimmed");

        UUID buyerUuid = UUID.randomUUID();
        Player buyer = player(buyerUuid, true);
        require(bridge.purchase(buyer, 13) == AuctionBridge.PurchaseResult.OPENED,
                "Purchase confirmation should open");
        require(PurchaseAuction.builds == 1 && PurchaseAuction.lastListingId == 13,
                "PurchaseAuction.build must receive the exact listing");

        AuctionManager.getItems().remove(13);
        require(bridge.purchase(buyer, 13) == AuctionBridge.PurchaseResult.UNAVAILABLE,
                "A sold listing must be rejected on click");
        require(bridge.purchase(player(sellerUuid, true), 12) == AuctionBridge.PurchaseResult.OWN_LISTING,
                "A seller must not buy their own listing");
        require(bridge.purchase(player(buyerUuid, false), 12) == AuctionBridge.PurchaseResult.NO_PERMISSION,
                "axauctions.use must be enforced");

        System.out.println("AuctionBridgeTest: all checks passed");
    }

    private static Player player(UUID uuid, boolean permission) {
        return (Player) Proxy.newProxyInstance(
                AuctionBridgeTest.class.getClassLoader(),
                new Class<?>[]{Player.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> uuid;
                    case "hasPermission" -> permission;
                    case "isValid" -> true;
                    case "isDead" -> false;
                    case "performCommand" -> true;
                    default -> defaultValue(method.getReturnType());
                }
        );
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0F;
        if (type == double.class) return 0D;
        if (type == char.class) return '\0';
        return null;
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
