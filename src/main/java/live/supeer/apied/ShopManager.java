package live.supeer.apied;

import co.aikar.idb.DB;
import co.aikar.idb.DbRow;
import de.tr7zw.changeme.nbtapi.NBT;
import de.tr7zw.changeme.nbtapi.iface.ReadableNBT;
import lombok.Getter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.*;
import org.bukkit.block.sign.Side;
import org.bukkit.block.sign.SignSide;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class ShopManager {
    public static final HashMap<UUID, Integer> shopConfirmation = new HashMap<>();
    private static final Map<UUID, ShopEventCount> shopEventCountMap = new HashMap<>();
    public static final HashMap<UUID, Location> shopCreation = new HashMap<>();

    private static final Map<UUID, List<TransactionInfo>> pendingRecaps = new HashMap<>();
    private static final Map<UUID, BukkitTask> scheduledRecapTasks = new HashMap<>();

    public static ChestShop newShop(Sign sign, Player player, Chest chest, int price, String type, Side side) {
        Location signLocation = sign.getLocation();
        List<Location> chestLocationList = new ArrayList<>();
        chestLocationList.add(chest.getLocation());
        String chestLocations = Utils.locationListToString(chestLocationList);
        String serializedItems = serializeItems(chest.getLocation());
        try {
            DB.executeInsert("INSERT INTO md_shops (ownerUUID, signLocation, chestLocations, dateTime, price, items, type, signSide) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    player.getUniqueId().toString(), Utils.locationToString(signLocation), chestLocations, Utils.getTimestamp(), price, serializedItems, type, side.toString());
            DbRow row = DB.getFirstRow("SELECT * FROM md_shops WHERE signLocation = ?", Utils.locationToString(signLocation));
            return new ChestShop(row);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    public static int playerShopUses(UUID playerUUID, int shopId) {
        try {
            return DB.getResults("SELECT * FROM md_shoptransactions WHERE uuid = ? AND shopId = ?", playerUUID.toString(), shopId).size();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static int playerDailyShopUses(UUID playerUUID, int shopId) {
        try {
            return DB.getResults("SELECT * FROM md_shoptransactions WHERE uuid = ? AND shopId = ? AND dateTime > CURDATE()",
                    playerUUID.toString(), shopId).size();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static int totalDailyShopUses(int shopId) {
        try {
            return DB.getResults("SELECT * FROM md_shoptransactions WHERE shopId = ? AND dateTime > CURDATE()", shopId).size();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static int totalShopUses(int shopId) {
        try {
            return DB.getResults("SELECT * FROM md_shoptransactions WHERE shopId = ?", shopId).size();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static ChestShop getShopFromSign(Sign sign) {
        try {
            DbRow row = DB.getFirstRow("SELECT * FROM md_shops WHERE signLocation = ? AND removed = 0", Utils.locationToString(sign.getLocation()));
            return row != null ? new ChestShop(row) : null;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static ChestShop getShopFromId(int shopId) {
        try {
            DbRow row = DB.getFirstRow("SELECT * FROM md_shops WHERE id = ?", shopId);
            return row != null ? new ChestShop(row) : null;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void logTransaction(UUID playerUUID, int shopId, int price) {
        try {
            DB.executeInsert("INSERT INTO md_shoptransactions (shopId, uuid, dateTime, price) VALUES (?, ?, ?, ?)",
                    shopId, playerUUID.toString(), Utils.getTimestamp(), price);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void handleChestClick(Player player, Location chestLocation, Location signLocation) {
        //Check if clicked chest is a doublechest, is so get the left chest
        if (chestLocation.getBlock().getState() instanceof DoubleChest doubleChest) {
            chestLocation = ((Chest) doubleChest.getLeftSide()).getLocation();
        } else {
            chestLocation = chestLocation.getBlock().getLocation();
        }

        Chest chest = ChestManager.getClaimedChest(chestLocation);
        if (chest == null) {
            Apied.sendMessage(player, "messages.chest.error.notFound");
            return;
        }
        if (!chest.getOwnerUUID().equals(player.getUniqueId())) {
            Apied.sendMessage(player, "messages.chest.error.modify.notOwner");
            return;
        }
        Sign sign = signLocation.getBlock().getState() instanceof Sign ? (Sign) signLocation.getBlock().getState() : null;
        if (sign == null) {
            Apied.sendMessage(player, "messages.shop.error.signNotFound");
            return;
        }
        if (chestLocation.distance(signLocation) > Apied.configuration.getShopMaxDistance()) {
            Apied.sendMessage(player, "messages.shop.error.tooFarAway", "%max%", String.valueOf(Apied.configuration.getShopMaxDistance()), "%distance%", String.valueOf((int) chestLocation.distance(signLocation)));
            return;
        }
        if (getBalance(sign) == 0) {
            Apied.sendMessage(player, "messages.shop.error.noBalance");
            return;
        }
        if (getBalance(sign) > Apied.configuration.getShopMaxPrice()) {
            Apied.sendMessage(player, "messages.shop.error.maxPrice", "%max%", String.valueOf(Apied.configuration.getShopMaxPrice()));
            return;
        }
        Inventory inventory = chestLocation.getBlock().getState() instanceof InventoryHolder inventoryHolder ? inventoryHolder.getInventory() : null;
        if (inventory == null) {
            Apied.sendMessage(player, "messages.chest.error.notFound");
            return;
        }
        if (inventory.isEmpty()) {
            Apied.sendMessage(player, "messages.shop.error.empty");
            return;
        }
        ChestShop chestShop = getShopFromSign(sign);
        if  (chestShop == null) {
            if (!validChest(chestLocation, player)) {
                return;
            }
            chestShop = newShop(sign, player, chest, getBalance(sign), getType(sign), getSide(sign));
            chest.addShop(Objects.requireNonNull(getShopFromSign(sign)).getId());
            shopCreation.remove(player.getUniqueId());
            int balance = getBalance(sign);
            formatSign(sign, null);
            if (Objects.equals(getType(sign), "BUY")) {
                Apied.sendMessage(player, "messages.shop.success.create.buy", "%items%", itemsFormatting(chestShop.getItems(), balance));
            } else {
                Apied.sendMessage(player, "messages.shop.success.create.sell", "%items%", itemsFormatting(chestShop.getItems(), balance));
            }
        } else {
            if (chestShop.getChestLocations().size() >= Apied.configuration.getShopMaxChests()) {
                Apied.sendMessage(player, "messages.shop.error.tooManyChests", "%max%", String.valueOf(Apied.configuration.getShopMaxChests()));
                return;
            }
            if (chestShop.getChestLocations().contains(chestLocation)) {
                Apied.sendMessage(player, "messages.shop.error.alreadyAdded");
                return;
            }
            chestShop.addChestLocation(chestLocation);
            chest.addShop(chestShop.getId());
            shopCreation.remove(player.getUniqueId());
            formatSign(sign, chestShop);
            Apied.sendMessage(player, "messages.shop.success.addChest");
        }
    }

    public static void handleChestLocationChange(Location oldLocation, Location newLocation) {
        try {
            List<DbRow> rows = DB.getResults("SELECT * FROM md_shops WHERE chestLocations LIKE ?", "%" + Utils.locationToString(oldLocation) + "%");
            if (rows == null || rows.isEmpty()) {
                return;
            }
            for (DbRow row : rows) {
                ChestShop chestShop = new ChestShop(row);
                chestShop.removeChestLocation(oldLocation);
                chestShop.addChestLocation(newLocation);
                Sign sign = (Sign) chestShop.getSignLocation().getBlock().getState();
                formatSign(sign, chestShop);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void handleChestRemoval(Location chestLocation) {
        try {
            List<DbRow> rows = DB.getResults("SELECT * FROM md_shops WHERE chestLocations LIKE ?", "%" + Utils.locationToString(chestLocation) + "%");
            if (rows == null || rows.isEmpty()) {
                return;
            }
            for (DbRow row : rows) {
                ChestShop chestShop = new ChestShop(row);
                chestShop.removeChestLocation(chestLocation);
                BlockState blockState = chestShop.getSignLocation().getBlock().getState();
                if (blockState instanceof Sign sign) {
                    formatSign(sign, chestShop);
                } else {
                    Apied.getInstance().getLogger().warning("Expected a Sign block state but found: " + blockState.getClass().getName());
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void handleSignRemoval(Location signLocation) {
        try {
            String signLocationString = Utils.locationToString(signLocation);
            DbRow row = DB.getFirstRow("SELECT * FROM md_shops WHERE signLocation = ?", signLocationString);
            if (row != null) {
                ChestShop chestShop = new ChestShop(row);
                for (Location chestLocation : chestShop.getChestLocations()) {
                    Chest chest = ChestManager.getClaimedChest(chestLocation);
                    if (chest != null) {
                        chest.removeShop(chestShop.getId());
                    }
                }
                chestShop.setRemoved(true);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void handleSignClick(Player player, Sign sign, Side clickedSide) {
        ChestShop chestShop = getShopFromSign(sign);
        if (chestShop == null || chestShop.isRemoved()) {
            return;
        }
        if (chestShop.getSignSide() != clickedSide) {
            return;
        }
        if (!shopConfirmation.containsKey(player.getUniqueId()) || !shopConfirmation.get(player.getUniqueId()).equals(chestShop.getId())) {
            shopConfirmation.put(player.getUniqueId(), chestShop.getId());
            sendConfirmationMessage(player, chestShop);
            return;
        }
        if (chestShop.getMaxUses() != 0) {
            if (totalShopUses(chestShop.getId()) >= chestShop.getMaxUses()) {
                Apied.sendMessage(player, "messages.shop.error.maxUses");
                formatSign(sign, chestShop);
                return;
            }
        }
        if (chestShop.getMaxDailyUses() != 0) {
            if (totalDailyShopUses(chestShop.getId()) >= chestShop.getMaxDailyUses()) {
                Apied.sendMessage(player, "messages.shop.error.maxDailyUses");
                formatSign(sign, chestShop);
                return;
            }
        }
        if (chestShop.getMaxPlayerUses() != 0) {
            if (playerShopUses(player.getUniqueId(), chestShop.getId()) >= chestShop.getMaxPlayerUses()) {
                Apied.sendMessage(player, "messages.shop.error.maxPlayerUses");
                formatSign(sign, chestShop);
                return;
            }
        }
        if (chestShop.getMaxPlayerDailyUses() != 0) {
            if (playerDailyShopUses(player.getUniqueId(), chestShop.getId()) >= chestShop.getMaxPlayerDailyUses()) {
                Apied.sendMessage(player, "messages.shop.error.maxDailyPlayerUses");
                formatSign(sign, chestShop);
                return;
            }
        }
        if (chestShop.getType().equals("BUY")) {
            if (!hasRequiredItemsInChests(chestShop.getChestLocations(), chestShop.getItems())) {
                Apied.sendMessage(player, "messages.shop.error.missingItems");
                formatSign(sign, chestShop);
                return;
            }
            if (!hasEnoughInventorySpace(player, chestShop.getItems()) && !chestShop.isAdmin()) {
                Apied.sendMessage(player, "messages.shop.error.fullInventory");
                formatSign(sign, chestShop);
                return;
            }
            MPlayer mPlayer = MPlayerManager.getPlayerFromPlayer(player);
            if (mPlayer == null) {
                return;
            }
            if (chestShop.getPrice() > mPlayer.getBalance()) {
                Apied.sendMessage(player, "messages.shop.error.insufficientFunds");
                formatSign(sign, chestShop);
                return;
            }
            buyItems(player, chestShop);
        } else if (chestShop.getType().equals("SELL")) {
            if (!hasEnoughStorageInChests(chestShop.getChestLocations(), chestShop.getItems()) && !chestShop.isAdmin()) {
                Apied.sendMessage(player, "messages.shop.error.noSpace");
                formatSign(sign, chestShop);
                return;
            }
            if (!hasRequiredItems(player, chestShop.getItems())) {
                Apied.sendMessage(player, "messages.shop.error.missingInventory");
                formatSign(sign, chestShop);
                return;
            }
            MPlayer mPlayer = MPlayerManager.getPlayerFromUUID(chestShop.getOwnerUUID());
            if (mPlayer == null) {
                return;
            }
            if (chestShop.getPrice() > mPlayer.getBalance()) {
                Apied.sendMessage(player, "messages.shop.error.ownerInsufficientFunds");
                formatSign(sign, chestShop);
                return;
            }
            sellItems(player, chestShop);
        }
    }

    public static void handleSignModify(Player player, Sign sign) {
        ChestShop chestShop = getShopFromSign(sign);
        if (chestShop == null) {
            return;
        }
        if (chestShop.isRemoved()) {
            return;
        }
        ShopEventCount shopEventCount = shopEventCountMap.get(player.getUniqueId());
        switch (shopEventCount.getType()) {
            case "admin" -> {
                if (!chestShop.getOwnerUUID().equals(player.getUniqueId()) && !ChestManager.chestOverride.contains(player)) {
                    Apied.sendMessage(player, "messages.permissionDenied");
                    return;
                }
                if (chestShop.isAdmin()) {
                    chestShop.setAdmin(false);
                    Apied.sendMessage(player, "messages.shop.modify.admin.on");
                } else {
                    chestShop.setAdmin(true);
                    Apied.sendMessage(player, "messages.shop.modify.admin.off");
                }
            }
            case "maxUses" -> {
                if (!chestShop.getOwnerUUID().equals(player.getUniqueId()) && !ChestManager.chestOverride.contains(player)) {
                    Apied.sendMessage(player, "messages.permissionDenied");
                    return;
                }
                chestShop.setMaxUses(shopEventCount.uses);
                decrementChestEventCount(player.getUniqueId());
                Apied.sendMessage(player, "messages.shop.modify.maxUses", "%count%", String.valueOf(shopEventCount.uses));
                if (shopEventCount.count > 0) {
                    Apied.sendMessage(player, "messages.shop.operationsleft", "%count%", String.valueOf(shopEventCount.count));
                }
            }
            case "maxPlayerUses" -> {
                if (!chestShop.getOwnerUUID().equals(player.getUniqueId()) && !ChestManager.chestOverride.contains(player)) {
                    Apied.sendMessage(player, "messages.permissionDenied");
                    return;
                }
                chestShop.setMaxPlayerUses(shopEventCount.uses);
                decrementChestEventCount(player.getUniqueId());
                Apied.sendMessage(player, "messages.shop.modify.maxPlayerUses", "%count%", String.valueOf(shopEventCount.uses));
                if (shopEventCount.count > 0) {
                    Apied.sendMessage(player, "messages.shop.operationsleft", "%count%", String.valueOf(shopEventCount.count));
                }
            }
            case "maxDailyUses" -> {
                if (!chestShop.getOwnerUUID().equals(player.getUniqueId()) && !ChestManager.chestOverride.contains(player)) {
                    Apied.sendMessage(player, "messages.permissionDenied");
                    return;
                }
                chestShop.setMaxDailyUses(shopEventCount.uses);
                decrementChestEventCount(player.getUniqueId());
                Apied.sendMessage(player, "messages.shop.modify.maxDailyUses", "%count%", String.valueOf(shopEventCount.uses));
                if (shopEventCount.count > 0) {
                    Apied.sendMessage(player, "messages.shop.operationsleft", "%count%", String.valueOf(shopEventCount.count));
                }
            }
            case "maxPlayerDailyUses" -> {
                if (!chestShop.getOwnerUUID().equals(player.getUniqueId()) && !ChestManager.chestOverride.contains(player)) {
                    Apied.sendMessage(player, "messages.permissionDenied");
                    return;
                }
                chestShop.setMaxPlayerDailyUses(shopEventCount.uses);
                decrementChestEventCount(player.getUniqueId());
                Apied.sendMessage(player, "messages.shop.modify.maxPlayerDailyUses", "%count%", String.valueOf(shopEventCount.uses));
                if (shopEventCount.count > 0) {
                    Apied.sendMessage(player, "messages.shop.operationsleft", "%count%", String.valueOf(shopEventCount.count));
                }
            }
        }
    }

    public static void handleChestClose(Location chestLocation) {
        Chest chest = ChestManager.getClaimedChest(chestLocation);
        if (chest == null) {
            return;
        }
        for (ChestShop chestShop : chest.getShops()) {
            if (chestShop == null) {
                continue;
            }
            Sign sign = (Sign) chestShop.getSignLocation().getBlock().getState();
            formatSign(sign, chestShop);
        }
    }

    public static void buyItems(Player player, ChestShop chestShop) {
        List<Location> chestLocations = chestShop.getChestLocations();
        ItemStack[] itemsToBuy = chestShop.getItems();
        Sign sign = (Sign) chestShop.getSignLocation().getBlock().getState();
        // Ensure that the required items are available in the chests
        if (!hasRequiredItemsInChests(chestLocations, itemsToBuy)) {
            Apied.sendMessage(player, "messages.shop.error.missingItems");
            formatSign(sign, chestShop);
            return;
        }

        // Attempt to remove items from chests
        for (ItemStack item : itemsToBuy) {
            if (!chestShop.isAdmin()) {
                removeItemFromChests(chestLocations, item.clone());
            }
            player.getInventory().addItem(item.clone());  // Clone the item before adding it to avoid modifying the original stack
        }

        // Handle balance
        MPlayer buyer = MPlayerManager.getPlayerFromPlayer(player);
        MPlayer seller = MPlayerManager.getPlayerFromUUID(chestShop.getOwnerUUID());

        if (chestShop.getPrice() > 0) {
            if (buyer == null || seller == null) {
                Apied.getInstance().getLogger().warning("The buyer or seller of a signshop transaction is null. Buyer: " + buyer + ", Seller: " + seller);
                return;
            }
            if (!buyer.getUuid().equals(seller.getUuid())) {
                buyer.removeBalance(chestShop.getPrice(), "{ \"type\": \"shop\", \"subtype\": \"buySelf\", \"playerUUID\": \"" + seller.getUuid() + "\", \"shopId\": " + chestShop.getId() + "}");
                seller.addBalance(chestShop.getPrice(), "{ \"type\": \"shop\", \"subtype\": \"buyOther\", \"playerUUID\": \"" + buyer.getUuid() + "\", \"shopId\": " + chestShop.getId() + "}");
            }
        }

        // Update sign
        formatSign(sign, chestShop);

        // Log transaction
        logTransaction(player.getUniqueId(), chestShop.getId(), chestShop.getPrice());
        if (chestShop.getItems().length == 1) {
            Apied.sendMessage(player, "messages.shop.success.buy.single", "%item%", itemFormatting(chestShop.getItems()[0]), "%price%", Utils.formattedMoney(chestShop.getPrice()));
        } else {
            Apied.sendMessage(player, "messages.shop.success.buy.multiple", "%items%", itemsFormatting(chestShop.getItems(), chestShop.getPrice()));
        }
        scheduleRecap(chestShop.getOwnerUUID(), new TransactionInfo(player.getUniqueId(), "BUY", chestShop.getPrice(), chestShop.getId()));
    }



    public static void sellItems(Player player, ChestShop chestShop) {
        // Remove items from player inventory and add to chests
        for (ItemStack item : chestShop.getItems()) {
            ItemStack itemToRemove = item.clone();
            player.getInventory().removeItem(itemToRemove);
            if (!chestShop.isAdmin()) {
                addItemToChests(chestShop.getChestLocations(), itemToRemove);
            }
        }

        // Handle balance
        MPlayer seller = MPlayerManager.getPlayerFromPlayer(player);
        MPlayer buyer = MPlayerManager.getPlayerFromUUID(chestShop.getOwnerUUID());
        if (chestShop.getPrice() > 0) {
            if (buyer == null || seller == null) {
                Apied.getInstance().getLogger().warning("The buyer or seller of a signshop transaction is null. Buyer: " + buyer + ", Seller: " + seller);
                return;
            }
            if (!buyer.getUuid().equals(seller.getUuid())) {
                seller.addBalance(chestShop.getPrice(), "{ \"type\": \"shop\", \"subtype\": \"sellSelf\", \"playerUUID\": \"" + buyer.getUuid() + "\", \"shopId\": " + chestShop.getId() + "}");
                buyer.removeBalance(chestShop.getPrice(), "{ \"type\": \"shop\", \"subtype\": \"sellOther\", \"playerUUID\": \"" + seller.getUuid() + "\", \"shopId\": " + chestShop.getId() + "}");
            }
        }

        // Update sign
        Sign sign = (Sign) chestShop.getSignLocation().getBlock().getState();
        formatSign(sign, chestShop);

        // Log transaction
        logTransaction(player.getUniqueId(), chestShop.getId(), chestShop.getPrice());
        // Send messages
        if (chestShop.getItems().length == 1) {
            Apied.sendMessage(player,"messages.shop.success.sell.single", "%item%", itemFormatting(chestShop.getItems()[0]), "%price%", Utils.formattedMoney(chestShop.getPrice()));
        } else {
            Apied.sendMessage(player,"messages.shop.success.sell.multiple", "%items%", itemsFormatting(chestShop.getItems(), chestShop.getPrice()));
        }
        scheduleRecap(chestShop.getOwnerUUID(), new TransactionInfo(player.getUniqueId(), "SELL", chestShop.getPrice(), chestShop.getId()));
    }

    private static void scheduleRecap(UUID ownerUUID, TransactionInfo transactionInfo) {
        pendingRecaps.computeIfAbsent(ownerUUID, k -> new ArrayList<>()).add(transactionInfo);

        if (!scheduledRecapTasks.containsKey(ownerUUID)) {
            BukkitTask task = Bukkit.getScheduler().runTaskLater(Apied.getInstance(), () -> {
                sendRecap(ownerUUID);
                scheduledRecapTasks.remove(ownerUUID);
            }, 20 * 60 * 15); // 15 minutes in ticks

            scheduledRecapTasks.put(ownerUUID, task);
        }
    }

    private static void sendRecap(UUID ownerUUID) {
        List<TransactionInfo> transactions = pendingRecaps.remove(ownerUUID);
        if (transactions == null || transactions.isEmpty()) {
            return;
        }

        Player owner = Bukkit.getPlayer(ownerUUID);
        if (owner == null || !owner.isOnline()) {
            return;
        }
        boolean hasBuy = false;
        int totalBuy = 0;

        boolean hasSell = false;
        int totalSell = 0;

        for (TransactionInfo transaction : transactions) {
            if (transaction.type.equals("BUY")) {
                hasBuy = true;
                totalBuy += transaction.price;
            } else if (transaction.type.equals("SELL")) {
                hasSell = true;
                totalSell += transaction.price;
            }
        }
        Apied.sendMessage(owner, "messages.shop.recap.header");
        if (hasBuy) {
            Apied.sendMessage(owner, "messages.shop.recap.sold", "%amount%", Utils.formattedMoney(totalBuy));
        }
        if (hasSell) {
            Apied.sendMessage(owner, "messages.shop.recap.bought", "%amount%", Utils.formattedMoney(totalSell));
        }
    }

    public static void sendConfirmationMessage(Player player, ChestShop chestShop) {
        if (chestShop.getItems().length == 1) {
            if (chestShop.getType().equals("BUY")) {
                Apied.sendMessage(player,"messages.shop.confirm.buy.single", "%item%", itemFormatting(chestShop.getItems()[0]), "%price%", Utils.formattedMoney(chestShop.getPrice()));
            } else if (chestShop.getType().equals("SELL")) {
                Apied.sendMessage(player,"messages.shop.confirm.sell.single", "%item%", itemFormatting(chestShop.getItems()[0]), "%price%", Utils.formattedMoney(chestShop.getPrice()));
            }
        } else {
            if (chestShop.getType().equals("BUY")) {
                Apied.sendMessage(player,"messages.shop.confirm.buy.multiple", "%items%", itemsFormatting(chestShop.getItems(), chestShop.getPrice()));
            } else if (chestShop.getType().equals("SELL")) {
                Apied.sendMessage(player,"messages.shop.confirm.sell.multiple", "%items%", itemsFormatting(chestShop.getItems(), chestShop.getPrice()));
            }
        }
    }

    public static String itemFormatting(ItemStack item) {
        String itemName = item.getType().getKey().getKey();
        String translatable = item.getType().isBlock()
                ? "<lang:block.minecraft." + itemName + ">"
                : "<lang:item.minecraft." + itemName + ">";
        String hoverInfo = getHoverInfo(item);
        String plural = item.getAmount() == 1
                ? Apied.getMessage("messages.word.count.singular")
                : Apied.getMessage("messages.word.count.plural");

            return item.getAmount() + " " + plural + " " + hoverInfo + translatable + "</hover>";
    }

    public static String itemsFormatting(ItemStack[] itemStacks, int price) {
        Map<String, List<ItemStack>> itemMap = new HashMap<>();
        for (ItemStack item : itemStacks) {
            if (item != null && item.getType() != Material.AIR) {
                String key = getItemKey(item);
                itemMap.computeIfAbsent(key, k -> new ArrayList<>()).add(item.clone());
            }
        }
        StringBuilder formattedItems = new StringBuilder();
        for (List<ItemStack> items : itemMap.values()) {
            Map<Integer, Integer> stackSizes = new HashMap<>();
            for (ItemStack item : items) {
                stackSizes.merge(item.getAmount(), 1, Integer::sum);
            }

            for (Map.Entry<Integer, Integer> entry : stackSizes.entrySet()) {
                int stackSize = entry.getKey();
                int count = entry.getValue();
                ItemStack representativeItem = items.getFirst().clone();
                representativeItem.setAmount(stackSize);

                String itemName = representativeItem.getType().getKey().getKey();
                String translatable = representativeItem.getType().isBlock()
                        ? "<lang:block.minecraft." + itemName + ">"
                        : "<lang:item.minecraft." + itemName + ">";
                String hoverInfo = getHoverInfo(representativeItem);
                String plural = stackSize == 1
                        ? Apied.getMessage("messages.word.count.singular")
                        : Apied.getMessage("messages.word.count.plural");
                formattedItems.append("   ")
                        .append(stackSize * count)
                        .append(" ")
                        .append(plural)
                        .append(" ")
                        .append(hoverInfo)
                        .append(translatable)
                        .append("</hover>\n");
            }
        }

        // Remove the last newline character
        if (!formattedItems.isEmpty()) {
            formattedItems.setLength(formattedItems.length() - 1);
        }

        formattedItems.append("\n...").append(Apied.getMessage("messages.word.for")).append(" ")
                .append(Utils.formattedMoney(price)).append(" ").append(Apied.getMessage("messages.word.currency")).append(".");

        return formattedItems.toString();
    }

    private static String getHoverInfo(ItemStack item) {
        String itemId = item.getType().getKey().toString().toLowerCase();
        if (Apied.configuration.getBannedPreviewItems().contains(itemId)) {
            return "<hover:show_item:'" + itemId + "':" + 1 + ">";
        }
        String hoverString = Apied.componentToMiniMessage(Component.text().hoverEvent(item).asComponent());
        return hoverString.replaceAll(":(\\d+):", ":1:");
    }

    public static int getBalance(Sign sign) {
        SignSide backSide = sign.getSide(Side.BACK);
        SignSide frontSide = sign.getSide(Side.FRONT);

        if (isValidSide(backSide) && isEmptySide(frontSide)) {
            return Integer.parseInt(PlainTextComponentSerializer.plainText().serialize(backSide.line(3)).replaceAll("\\D", "").trim());
        } else if (isValidSide(frontSide) && isEmptySide(backSide)) {
            return Integer.parseInt(PlainTextComponentSerializer.plainText().serialize(frontSide.line(3)).replaceAll("\\D", "").trim());
        }
        return 0;
    }

    public static String getType(Sign sign) {
        SignSide backSide = sign.getSide(Side.BACK);
        SignSide frontSide = sign.getSide(Side.FRONT);

        if (isValidSide(backSide) && isEmptySide(frontSide)) {
            String line0 = PlainTextComponentSerializer.plainText().serialize(backSide.line(0)).trim();
            if (line0.equals("BUY") || line0.equals("KÖP")) {
                return "BUY";
            } else if (line0.equals("SELL") || line0.equals("SÄLJ")) {
                return "SELL";
            }
        } else if (isValidSide(frontSide) && isEmptySide(backSide)) {
            String line0 = PlainTextComponentSerializer.plainText().serialize(frontSide.line(0)).trim();
            if (line0.equals("BUY") || line0.equals("KÖP")) {
                return "BUY";
            } else if (line0.equals("SELL") || line0.equals("SÄLJ")) {
                return "SELL";
            }
        }
        return null;
    }

    public static void formatSign(Sign sign, ChestShop chestShop) {
        SignSide backSide = sign.getSide(Side.BACK);
        SignSide frontSide = sign.getSide(Side.FRONT);
        TextComponent comp1 = null;
        TextComponent comp2 = null;
        String type;

        if (chestShop == null) {
            type = getType(sign);
            if (type == null) {
                return;
            }

            //Sätt en grej som kollar om den är stocked eller inte
            if (type.equals("BUY")) {
                comp1 = (TextComponent) Apied.getMessageComponent("messages.shop.sign.stocked.buy");
                comp2 = (TextComponent) Apied.getMessageComponent("messages.shop.sign.price", "%price%", Utils.formattedMoney(getBalance(sign)));
            } else if (type.equals("SELL")) {
                comp1 = (TextComponent) Apied.getMessageComponent("messages.shop.sign.stocked.sell");
                comp2 = (TextComponent) Apied.getMessageComponent("messages.shop.sign.price", "%price%", Utils.formattedMoney(getBalance(sign)));
            }
        } else {
            type = chestShop.getType();
            if (type == null) {
                return;
            }

            // Check if the chest shop is stocked based on type
            if (type.equals("BUY")) {
                if (ShopManager.hasRequiredItemsInChests(chestShop.getChestLocations(), chestShop.getItems())) {
                    comp1 = (TextComponent) Apied.getMessageComponent("messages.shop.sign.stocked.buy");
                } else {
                    comp1 = (TextComponent) Apied.getMessageComponent("messages.shop.sign.unstocked.buy");
                }
                comp2 = (TextComponent) Apied.getMessageComponent("messages.shop.sign.price", "%price%", Utils.formattedMoney(chestShop.getPrice()));
            } else if (type.equals("SELL")) {
                if (hasEnoughStorageInChests(chestShop.getChestLocations(), chestShop.getItems())) {
                    comp1 = (TextComponent) Apied.getMessageComponent("messages.shop.sign.stocked.sell");
                } else {
                    comp1 = (TextComponent) Apied.getMessageComponent("messages.shop.sign.unstocked.sell");
                }
                comp2 = (TextComponent) Apied.getMessageComponent("messages.shop.sign.price", "%price%", Utils.formattedMoney(chestShop.getPrice()));
            }
        }

        // Update the sign text based on the valid side and the text components
        if (isValidSide(backSide) && isEmptySide(frontSide)) {
            assert comp1 != null;
            backSide.line(0, comp1);
            assert comp2 != null;
            backSide.line(3, comp2);
        } else if (isValidSide(frontSide) && isEmptySide(backSide)) {
            assert comp1 != null;
            frontSide.line(0, comp1);
            assert comp2 != null;
            frontSide.line(3, comp2);
        }

        sign.setWaxed(true);
        sign.update();
    }


    public static boolean validChest(Location location, Player player) {
        Block block = location.getBlock();
        Inventory inventory = null;

        if (block.getState() instanceof org.bukkit.block.Chest chest) {
            inventory = chest.getInventory();
        } else if (block.getState() instanceof ShulkerBox shulkerBox) {
            inventory = shulkerBox.getInventory();
        } else if (block.getState() instanceof Barrel barrel) {
            inventory = barrel.getInventory();
        } else if (block.getState() instanceof DoubleChest doubleChest) {
            inventory = doubleChest.getInventory();
        }

        if (inventory == null) {
            Apied.sendMessage(player, "messages.chest.error.notFound");
            return false;
        }

        Chest chest = ChestManager.getClaimedChest(location);
        if (chest == null || !chest.getOwnerUUID().equals(player.getUniqueId())) {
            Apied.sendMessage(player, "messages.chest.error.modify.notOwner");
            return false;
        }

        int itemCount = 0;
        for (ItemStack item : inventory.getContents()) {
            if (item != null) {
                itemCount++;
            }
        }
        if (itemCount > Apied.configuration.getShopMaxItems()) {
            Apied.sendMessage(player, "messages.shop.error.tooManyItems", "%max%", String.valueOf(Apied.configuration.getShopMaxItems()));
            return false;
        }
        return true;
    }

    public static Side getSide(Sign sign) {
        SignSide backSide = sign.getSide(Side.BACK);
        SignSide frontSide = sign.getSide(Side.FRONT);
        if (isValidSide(backSide) && isEmptySide(frontSide)) {
            return Side.BACK;
        } else if (isValidSide(frontSide) && isEmptySide(backSide)) {
            return Side.FRONT;
        }
        return null;
    }

    public static boolean isValidSign(Sign sign) {
        SignSide backSide = sign.getSide(Side.BACK);
        SignSide frontSide = sign.getSide(Side.FRONT);
        return (isValidSide(backSide) && isEmptySide(frontSide)) || (isValidSide(frontSide) && isEmptySide(backSide));
    }

    private static boolean isValidSide(SignSide side) {
        // Get the raw text from the Component, stripping all formatting
        String line0 = PlainTextComponentSerializer.plainText().serialize(side.line(0)).trim();

        // Extract only the digits from line3, also after stripping formatting
        String line3 = PlainTextComponentSerializer.plainText().serialize(side.line(3)).replaceAll("[^\\d]", "").trim();

        // Check if line0 is a valid shop type
        if (!("BUY".equals(line0) || "SELL".equals(line0) || "KÖP".equals(line0) || "SÄLJ".equals(line0))) {
            return false;
        }

        try {
            // Ensure line3 is not empty and can be parsed as an integer
            Integer.parseInt(line3);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }



    private static boolean isEmptySide(SignSide side) {
        for (int i = 0; i < 4; i++) {
            if (!side.getLine(i).trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    public static String serializeItems(Location chestLocation) {
        Block block = chestLocation.getBlock();
        Inventory inventory = null;
        if (block.getState() instanceof org.bukkit.block.Chest chest) {
            inventory = chest.getInventory();
        }
        if (block.getState() instanceof ShulkerBox shulkerBox) {
            inventory = shulkerBox.getInventory();
        }
        if (block.getState() instanceof Barrel barrel) {
            inventory = barrel.getInventory();

        }
        VirtualInventory virtualInventory = new VirtualInventory(Collections.singletonList(inventory));
        virtualInventory.sortInventory();
        return Utils.itemStackArrayToBase64(virtualInventory.getContents());
    }

    public static void removeItemFromChests(List<Location> chestLocations, ItemStack itemToRemove) {
        Inventory inventory = getFirstInventoryWithItems(chestLocations, itemToRemove);

        if (inventory != null) {
            int amountToRemove = itemToRemove.getAmount();

            for (ItemStack item : inventory.getContents()) {
                if (item != null && item.getType() == itemToRemove.getType() && item.isSimilar(itemToRemove)) {
                    int currentAmount = item.getAmount();

                    if (currentAmount <= amountToRemove) {
                        amountToRemove -= currentAmount;
                        inventory.removeItem(item); // Remove the item completely
                    } else {
                        item.setAmount(currentAmount - amountToRemove);
                        return; // Finished removing the required amount
                    }

                    if (amountToRemove <= 0) {
                        return; // Stop once we've removed the full amount
                    }
                }
            }
        }
    }


    private static void addItemToChests(List<Location> chestLocations, ItemStack itemToAdd) {
        for (Location location : chestLocations) {
            Block block = location.getBlock();
            if (block.getState() instanceof InventoryHolder) {
                Inventory inventory = ((InventoryHolder) block.getState()).getInventory();
                HashMap<Integer, ItemStack> leftover = inventory.addItem(itemToAdd);
                if (leftover.isEmpty()) {
                    return;
                } else {
                    itemToAdd = leftover.get(0);
                }
            }
        }
    }

    public static boolean hasRequiredItems(Player player, ItemStack[] requiredItems) {
        return hasRequiredItemsInChest(player, requiredItems);
    }

    public static boolean hasRequiredItemsInChest(InventoryHolder chest, ItemStack[] requiredItems) {
        Inventory inventory = chest.getInventory();
        Map<Material, Integer> inventoryItemsCount = new HashMap<>();

        for (ItemStack item : inventory.getContents()) {
            if (item != null && item.getType() != Material.AIR) {
                inventoryItemsCount.put(item.getType(), inventoryItemsCount.getOrDefault(item.getType(), 0) + item.getAmount());
            }
        }

        for (ItemStack requiredItem : requiredItems) {
            int requiredAmount = requiredItem.getAmount();
            int inventoryAmount = inventoryItemsCount.getOrDefault(requiredItem.getType(), 0);
            if (inventoryAmount < requiredAmount) {
                return false;
            }
        }
        return true;
    }

    public static Inventory getFirstInventoryWithItems(List<Location> chestLocations, ItemStack itemToRemove) {
        for (Location chestLocation : chestLocations) {
            Block block = chestLocation.getBlock();
            Inventory inventory = null;

            if (block.getState() instanceof org.bukkit.block.Chest chest) {
                inventory = chest.getInventory();
            } else if (block.getState() instanceof ShulkerBox shulkerBox) {
                inventory = shulkerBox.getInventory();
            } else if (block.getState() instanceof Barrel barrel) {
                inventory = barrel.getInventory();
            } else if (block.getState() instanceof DoubleChest doubleChest) {
                inventory = doubleChest.getInventory();
            }

            if (inventory != null && hasEnoughItems(inventory, itemToRemove)) {
                return inventory;
            }
        }

        return null;
    }

    private static boolean hasEnoughItems(Inventory inventory, ItemStack itemToRemove) {
        int amountNeeded = itemToRemove.getAmount();
        int amountFound = 0;

        for (ItemStack item : inventory.getContents()) {
            if (item != null && item.getType() == itemToRemove.getType() && item.isSimilar(itemToRemove)) {
                amountFound += item.getAmount();
                if (amountFound >= amountNeeded) {
                    return true;
                }
            }
        }
        return false;
    }


    public static boolean hasRequiredItemsInChests(List<Location> chestLocations, ItemStack[] requiredItems) {
        List<Inventory> inventories = new ArrayList<>();

        for (Location chestLocation : chestLocations) {
            Block block = chestLocation.getBlock();
            Inventory inventory = null;

            if (block.getState() instanceof org.bukkit.block.Chest chest) {
                inventory = chest.getInventory();
            } else if (block.getState() instanceof ShulkerBox shulkerBox) {
                inventory = shulkerBox.getInventory();
            } else if (block.getState() instanceof Barrel barrel) {
                inventory = barrel.getInventory();
            } else if (block.getState() instanceof DoubleChest doubleChest) {
                inventory = doubleChest.getInventory();
            }

            if (inventory != null) {
                inventories.add(inventory);
            }
        }

        VirtualInventory virtualInventory = new VirtualInventory(inventories);
        return virtualInventory.hasItems(requiredItems);
    }

    private static String getItemKey(ItemStack item) {
        ReadableNBT nbt = NBT.itemStackToNBT(item);
        return item.getType().getKey() + nbt.toString();
    }

    public static boolean hasEnoughInventorySpace(Player player, ItemStack[] itemsToStore) {
        Inventory inventory = player.getInventory();
        Map<Material, Integer> requiredCounts = new HashMap<>();

        // Calculate the total required amounts for each material
        for (ItemStack item : itemsToStore) {
            requiredCounts.put(item.getType(), requiredCounts.getOrDefault(item.getType(), 0) + item.getAmount());
        }

        // Check inventory space for each required material
        for (Map.Entry<Material, Integer> entry : requiredCounts.entrySet()) {
            Material material = entry.getKey();
            int requiredAmount = entry.getValue();

            // Check for existing stacks of the same material in the inventory
            for (ItemStack inventoryItem : inventory.getContents()) {
                if (inventoryItem != null && inventoryItem.getType() == material) {
                    int availableSpace = inventoryItem.getMaxStackSize() - inventoryItem.getAmount();
                    if (availableSpace > 0) {
                        int amountToStore = Math.min(availableSpace, requiredAmount);
                        requiredAmount -= amountToStore;
                    }
                    if (requiredAmount <= 0) {
                        break;
                    }
                }
            }

            // Check for empty slots in the inventory
            while (requiredAmount > 0) {
                int emptySlot = inventory.firstEmpty();
                if (emptySlot == -1) {
                    // No empty slots left
                    return false;
                }
                // Assume we can place a full stack in the empty slot
                int amountToStore = Math.min(requiredAmount, material.getMaxStackSize());
                requiredAmount -= amountToStore;
            }
        }

        // If we have satisfied the requirement for all materials, return true
        return true;
    }

    public static int getEmptySlotsInInventory(Inventory inventory) {
        int emptySlots = 0;
        for (ItemStack item : inventory.getContents()) {
            if (item == null || item.getType() == Material.AIR) {
                emptySlots++;
            }
        }
        return emptySlots;
    }

    public static boolean hasEnoughStorageInChests(List<Location> chestLocations, ItemStack[] itemsToStore) {
        // Create a list to hold all the inventories from the chest locations
        List<Inventory> inventories = new ArrayList<>();

        // Aggregate all the inventories from the chest locations
        for (Location chestLocation : chestLocations) {
            Block block = chestLocation.getBlock();
            Inventory inventory = null;

            if (block.getState() instanceof org.bukkit.block.Chest chest) {
                inventory = chest.getInventory();
            } else if (block.getState() instanceof ShulkerBox shulkerBox) {
                inventory = shulkerBox.getInventory();
            } else if (block.getState() instanceof Barrel barrel) {
                inventory = barrel.getInventory();
            }

            if (inventory != null) {
                inventories.add(inventory);
            }
        }

        // Create a VirtualInventory to represent the combined inventories
        VirtualInventory virtualInventory = new VirtualInventory(inventories);

        // Use the canTakeItems method to check if the items can be stored in the combined inventories
        return virtualInventory.canTakeItems(itemsToStore);
    }

    public static void startShopEventCount(Player player, String type, int uses, int count) {
        shopEventCountMap.put(player.getUniqueId(), new ShopEventCount(count, type, player.getUniqueId(), uses));
    }

    public static boolean isShopEventCounting(UUID playerUUID) {
        return shopEventCountMap.containsKey(playerUUID);
    }

    public static ShopEventCount getShopEventCount(UUID playerUUID) {
        return shopEventCountMap.get(playerUUID);
    }

    public static void stopShopEventCount(UUID playerUUID) {
        shopEventCountMap.remove(playerUUID);
    }

    public static void decrementChestEventCount(UUID playerUUID) {
        ShopEventCount shopEventCount = shopEventCountMap.get(playerUUID);
        if (shopEventCount != null) {
            shopEventCount.decrementCount();
            if (shopEventCount.count <= 0) {
                shopEventCountMap.remove(playerUUID);
            }
        }
    }

    @Getter
    public static class ShopEventCount {
        private int count;
        private final String type;
        private final UUID target;
        private final int uses;

        public ShopEventCount(int count, String type, UUID target, int uses) {
            this.count = count;
            this.type = type;
            this.target = target;
            this.uses = uses;
        }

        public void decrementCount() {
            count--;
        }
    }

    public static class TransactionInfo {
        UUID playerUUID;
        String type;
        int price;
        int shopId;

        TransactionInfo(UUID playerUUID, String type, int price, int shopId) {
            this.playerUUID = playerUUID;
            this.type = type;
            this.price = price;
            this.shopId = shopId;
        }
    }
}
