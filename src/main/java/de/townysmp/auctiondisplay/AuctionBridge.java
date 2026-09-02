package de.townysmp.auctiondisplay;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

final class AuctionBridge {
    enum PurchaseResult { OPENED, UNAVAILABLE, OWN_LISTING, NO_PERMISSION, ERROR }

    private final Logger logger;
    private Method getItems;
    private Method getItemById;
    private Constructor<?> purchaseConstructor;
    private int purchasePlayerParameter;
    private int purchaseItemParameter;
    private int purchaseGuiParameter = -1;
    private Class<?> purchaseGuiType;
    private Method purchaseBuild;
    private long fallbackExpireMillis;
    private boolean ready;

    AuctionBridge(Logger logger, long fallbackExpireSeconds) {
        this.logger = logger;
        setFallbackExpireSeconds(fallbackExpireSeconds);
    }

    void setFallbackExpireSeconds(long seconds) {
        this.fallbackExpireMillis = Math.max(60L, seconds) * 1_000L;
    }

    void initialize() throws ReflectiveOperationException {
        initialize(AuctionBridge.class.getClassLoader());
    }

    void initialize(ClassLoader axAuctionsClassLoader) throws ReflectiveOperationException {
        if (axAuctionsClassLoader == null) {
            throw new ClassNotFoundException("AxAuctions class loader is unavailable");
        }
        Class<?> managerClass = Class.forName(
                "com.artillexstudios.axauctions.items.AuctionManager", true, axAuctionsClassLoader);
        Class<?> auctionItemClass = Class.forName(
                "com.artillexstudios.axauctions.items.AuctionItem", true, axAuctionsClassLoader);
        Class<?> purchaseClass = Class.forName(
                "com.artillexstudios.axauctions.items.actions.PurchaseAuction", true, axAuctionsClassLoader);
        this.getItems = findStaticMethod(managerClass, "getItems", 0);
        this.getItemById = findIdMethod(managerClass);
        PurchaseBinding purchaseBinding = findPurchaseConstructor(purchaseClass, auctionItemClass);
        this.purchaseConstructor = purchaseBinding.constructor();
        this.purchasePlayerParameter = purchaseBinding.playerParameter();
        this.purchaseItemParameter = purchaseBinding.itemParameter();
        GuiBinding guiBinding = findGuiBinding(purchaseClass, purchaseBinding);
        if (guiBinding != null) {
            this.purchaseGuiParameter = guiBinding.parameter();
            this.purchaseGuiType = guiBinding.type();
        }
        this.purchaseBuild = purchaseClass.getMethod("build");
        this.ready = true;
        logger.info("Connected to AxAuctions API (purchase constructor: "
                + purchaseConstructor.getParameterCount() + " parameters, sign GUI context: "
                + (purchaseGuiType == null ? "not exposed" : purchaseGuiType.getSimpleName()) + ").");
    }

    boolean isReady() {
        return ready;
    }

    List<AuctionListing> newest(int limit) {
        if (!ready || limit <= 0) return List.of();
        List<AuctionListing> listings = new ArrayList<>();
        try {
            Object raw = getItems.invoke(null);
            if (!(raw instanceof Map<?, ?> map)) return List.of();
            for (Object item : new ArrayList<>(map.values())) {
                try {
                    AuctionListing listing = read(item);
                    if (listing != null) listings.add(listing);
                } catch (ReflectiveOperationException | RuntimeException exception) {
                    logger.log(Level.FINE, "Skipped one unreadable AxAuctions listing", exception);
                }
            }
        } catch (ReflectiveOperationException exception) {
            logger.log(Level.SEVERE, "Could not read AxAuctions listings", exception);
            return List.of();
        }
        listings.sort(Comparator.comparingLong((AuctionListing listing) -> listing.startTime)
                .thenComparingInt(listing -> listing.id).reversed());
        return listings.size() <= limit ? listings : new ArrayList<>(listings.subList(0, limit));
    }

    PurchaseResult purchase(Player player, int listingId) {
        if (!player.hasPermission("axauctions.use")) return PurchaseResult.NO_PERMISSION;
        try {
            Object raw = findRaw(listingId);
            if (raw == null) return PurchaseResult.UNAVAILABLE;
            AuctionListing listing = read(raw);
            if (listing == null) return PurchaseResult.UNAVAILABLE;
            if (listing.sellerUuid != null && listing.sellerUuid.equals(player.getUniqueId())) {
                return PurchaseResult.OWN_LISTING;
            }
            Object[] arguments = new Object[purchaseConstructor.getParameterCount()];
            Class<?>[] parameterTypes = purchaseConstructor.getParameterTypes();
            for (int index = 0; index < arguments.length; index++) {
                arguments[index] = defaultValue(parameterTypes[index]);
            }
            arguments[purchasePlayerParameter] = player;
            arguments[purchaseItemParameter] = raw;
            if (purchaseGuiParameter >= 0) {
                arguments[purchaseGuiParameter] = createGuiContext();
            }
            Object purchase = purchaseConstructor.newInstance(arguments);
            purchaseBuild.invoke(purchase);
            return PurchaseResult.OPENED;
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause() == null ? exception : exception.getCause();
            logger.log(Level.SEVERE, "AxAuctions rejected display purchase for listing " + listingId, cause);
            return PurchaseResult.ERROR;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            logger.log(Level.SEVERE, "Could not open AxAuctions purchase for listing " + listingId, exception);
            return PurchaseResult.ERROR;
        }
    }

    private Object findRaw(int id) throws ReflectiveOperationException {
        Class<?> idType = getItemById.getParameterTypes()[0];
        Object argument = idType == long.class || idType == Long.class ? (long) id
                : idType == String.class ? String.valueOf(id) : id;
        Object result = getItemById.invoke(null, argument);
        if (result instanceof Optional<?> optional) return optional.orElse(null);
        return result;
    }

    private static Method findStaticMethod(Class<?> type, String name, int parameterCount)
            throws NoSuchMethodException {
        for (Method method : type.getMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount() != parameterCount) continue;
            if (!Modifier.isStatic(method.getModifiers())) continue;
            return method;
        }
        throw new NoSuchMethodException(type.getName() + "." + name + " with " + parameterCount + " parameters");
    }

    private static Method findIdMethod(Class<?> managerClass) throws NoSuchMethodException {
        for (Method method : managerClass.getMethods()) {
            if (!(method.getName().equals("getItemByID") || method.getName().equals("getItemById"))) continue;
            if (!Modifier.isStatic(method.getModifiers()) || method.getParameterCount() != 1) continue;
            Class<?> type = method.getParameterTypes()[0];
            if (type == int.class || type == Integer.class || type == long.class || type == Long.class
                    || type == String.class) return method;
        }
        throw new NoSuchMethodException(managerClass.getName() + ".getItemByID(int)");
    }

    private static PurchaseBinding findPurchaseConstructor(Class<?> purchaseClass, Class<?> auctionItemClass)
            throws NoSuchMethodException {
        PurchaseBinding best = null;
        int bestScore = Integer.MIN_VALUE;
        for (Constructor<?> constructor : purchaseClass.getDeclaredConstructors()) {
            Class<?>[] parameters = constructor.getParameterTypes();
            if (parameters.length < 2) continue;
            int playerParameter = matchingParameter(parameters, Player.class, -1);
            int itemParameter = matchingParameter(parameters, auctionItemClass, playerParameter);
            if (playerParameter < 0 || itemParameter < 0) continue;

            int score = (Modifier.isPublic(constructor.getModifiers()) ? 100 : 0)
                    - parameters.length * 2
                    + (playerParameter == 0 ? 10 : 0)
                    + (itemParameter == 1 ? 10 : 0)
                    + (parameters[playerParameter] == Player.class ? 5 : 0)
                    + (parameters[itemParameter] == auctionItemClass ? 5 : 0);
            if (score <= bestScore) continue;
            try {
                constructor.setAccessible(true);
            } catch (RuntimeException ignored) {
                // Public constructors remain callable even if accessibility cannot be relaxed.
            }
            best = new PurchaseBinding(constructor, playerParameter, itemParameter);
            bestScore = score;
        }
        if (best != null) return best;
        throw new NoSuchMethodException(purchaseClass.getName()
                + " has no usable constructor containing Player and AuctionItem. Available: "
                + constructorSignatures(purchaseClass));
    }

    private static GuiBinding findGuiBinding(Class<?> purchaseClass, PurchaseBinding purchaseBinding)
            throws NoSuchMethodException {
        Class<?> guiType = null;
        for (Field field : purchaseClass.getDeclaredFields()) {
            if (field.getName().equalsIgnoreCase("lastGui")
                    || field.getType().getSimpleName().equals("PaginatedMenu")) {
                guiType = field.getType();
                break;
            }
        }
        if (guiType == null) return null;
        if (!guiType.isInterface()) {
            throw new NoSuchMethodException("AxAuctions purchase GUI context " + guiType.getName()
                    + " is not an interface and cannot be safely supplied for a physical sign purchase");
        }

        Constructor<?> constructor = purchaseBinding.constructor();
        Class<?>[] types = constructor.getParameterTypes();
        Parameter[] parameters = constructor.getParameters();
        int compatibleFallback = -1;
        for (int index = 0; index < types.length; index++) {
            if (index == purchaseBinding.playerParameter() || index == purchaseBinding.itemParameter()) continue;
            if (types[index].isPrimitive()) continue;
            if (!(types[index] == Object.class || types[index].isAssignableFrom(guiType))) continue;
            String name = parameters[index].getName().toLowerCase(java.util.Locale.ROOT);
            if (name.contains("gui") || name.contains("menu")) {
                return new GuiBinding(index, guiType);
            }
            if (compatibleFallback < 0) compatibleFallback = index;
        }
        if (compatibleFallback >= 0) return new GuiBinding(compatibleFallback, guiType);
        throw new NoSuchMethodException("AxAuctions exposes " + guiType.getName()
                + " but its selected purchase constructor has no compatible GUI parameter");
    }

    private Object createGuiContext() {
        if (purchaseGuiType == null) return null;
        return Proxy.newProxyInstance(
                purchaseGuiType.getClassLoader(),
                new Class<?>[]{purchaseGuiType},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "toString" -> "TownySMPSignPurchaseContext";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> arguments != null && arguments.length == 1 && proxy == arguments[0];
                    default -> defaultValue(method.getReturnType());
                }
        );
    }

    private static int matchingParameter(Class<?>[] parameters, Class<?> valueType, int excluded) {
        for (int index = 0; index < parameters.length; index++) {
            if (index == excluded || parameters[index] == Object.class) continue;
            if (parameters[index] == valueType) return index;
        }
        for (int index = 0; index < parameters.length; index++) {
            if (index == excluded || parameters[index] == Object.class) continue;
            if (parameters[index].isAssignableFrom(valueType)) return index;
        }
        return -1;
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

    private static String constructorSignatures(Class<?> type) {
        if (type.getDeclaredConstructors().length == 0) return "<none>";
        List<String> signatures = new ArrayList<>();
        for (Constructor<?> constructor : type.getDeclaredConstructors()) {
            String parameters = Arrays.stream(constructor.getParameterTypes())
                    .map(Class::getTypeName)
                    .reduce((left, right) -> left + ", " + right)
                    .orElse("");
            signatures.add("(" + parameters + ")");
        }
        return String.join("; ", signatures);
    }

    private record PurchaseBinding(Constructor<?> constructor, int playerParameter, int itemParameter) {}

    private record GuiBinding(int parameter, Class<?> type) {}

    private AuctionListing read(Object raw) throws ReflectiveOperationException {
        if (raw == null) return null;
        if (booleanValue(callOptional(raw, "isExpired"), false)) return null;
        if (booleanValue(callOptional(raw, "isDeleted"), false)) return null;

        int id = ((Number) call(raw, "getId")).intValue();
        Object stackObject = call(raw, "getItemStack");
        if (!(stackObject instanceof ItemStack stack)) return null;
        double price = ((Number) call(raw, "getPrice")).doubleValue();
        long startTime = normalizeEpoch(((Number) call(raw, "getStartTime")).longValue());

        Object sellerObject = call(raw, "getSeller");
        String seller = String.valueOf(call(sellerObject, "getName"));
        Object uuidObject = callOptional(sellerObject, "getUUID");
        UUID sellerUuid = uuidObject instanceof UUID uuid ? uuid : null;

        long expiryDate = numberValue(callOptional(raw, "getExpiryDate"), -1L);
        expiryDate = normalizeEpoch(expiryDate);
        if (expiryDate <= 0L) {
            long expiryValue = numberValue(callOptional(raw, "getExpiryTime"), -1L);
            if (expiryValue > 0L) {
                expiryDate = expiryValue > 10_000_000_000L
                        ? expiryValue
                        : System.currentTimeMillis() + expiryValue * 1_000L;
            }
        }
        if (expiryDate <= 0L) expiryDate = startTime + fallbackExpireMillis;
        if (expiryDate <= System.currentTimeMillis()) return null;
        return new AuctionListing(id, stack, price, seller, sellerUuid, startTime, expiryDate, raw);
    }

    private static Object call(Object target, String method) throws ReflectiveOperationException {
        if (target == null) throw new ReflectiveOperationException("Missing target for " + method);
        return target.getClass().getMethod(method).invoke(target);
    }

    private static Object callOptional(Object target, String method) {
        try {
            return call(target, method);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static boolean booleanValue(Object value, boolean fallback) {
        return value instanceof Boolean bool ? bool : fallback;
    }

    private static long numberValue(Object value, long fallback) {
        return value instanceof Number number ? number.longValue() : fallback;
    }

    private static long normalizeEpoch(long value) {
        if (value > 0L && value < 10_000_000_000L) return value * 1_000L;
        return value;
    }
}
