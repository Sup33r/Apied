package live.supeer.apied;

import co.aikar.idb.DB;
import co.aikar.idb.DbRow;
import de.tr7zw.changeme.nbtapi.NBT;
import de.tr7zw.changeme.nbtapi.iface.ReadableNBT;
import lombok.Getter;
import net.kyori.adventure.text.TextComponent;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.*;
import org.bukkit.block.sign.Side;
import org.bukkit.block.sign.SignSide;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class ShopManager {
    public static final HashMap<UUID, Integer> shopConfirmation = new HashMap<>();
    private static final Map<UUID, ShopEventCount> shopEventCountMap = new HashMap<>();
    public static final HashMap<UUID, Location> shopCreation = new HashMap<>();

    public static void newShop(Sign sign, Player player, Chest chest, int balance, String type) {
        Location signLocation = sign.getLocation();
        List<Location> chestLocationList = new ArrayList<>();
        chestLocationList.add(chest.getLocation());
        String chestLocations = Utils.locationListToString(chestLocationList);
        String serializedItems = serializeItems(chest.getLocation());
        try {
            DB.executeInsert("INSERT INTO md_shops (ownerUUID, signLocation, chestLocations, dateTime, balance, items, type) VALUES (?, ?, ?, ?, ?, ?, ?)",
                    player.getUniqueId().toString(), Utils.locationToString(signLocation), chestLocations, Utils.getTimestamp(), balance, serializedItems, type);
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
            DbRow row = DB.getFirstRow("SELECT * FROM md_shops WHERE signLocation = ?", Utils.locationToString(sign.getLocation()));
            return row != null ? new ChestShop(row) : null;
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
        ChestShop chestShop = getShopFromSign(sign);
        if  (chestShop == null) {
            if (!validChest(chestLocation, player)) {
                return;
            }
            newShop(sign, player, chest, getBalance(sign), getType(sign));
            chest.addShop(Objects.requireNonNull(getShopFromSign(sign)).getId());
            shopCreation.remove(player.getUniqueId());
            int balance = getBalance(sign);
            formatSign(sign, null);
            if (Objects.equals(getType(sign), "BUY")) {
                Apied.sendMessage(player, "messages.shop.success.create.buy", "%items%", chestFormatting(chestLocation, balance));
            } else {
                Apied.sendMessage(player, "messages.shop.success.create.sell", "%items%", chestFormatting(chestLocation, balance));
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
                Sign sign = (Sign) chestShop.getSignLocation().getBlock().getState();
                formatSign(sign, chestShop);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void handleSignRemoval(Location signLocation) {
        try {
            DbRow row = DB.getFirstRow("SELECT * FROM md_shops WHERE signLocation = ?", Utils.locationToString(signLocation));
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
        if (chestShop == null || chestShop.isRemoved() || !(chestShop.getSignSide() == clickedSide)) {
            return;
        }
        ShopEventCount shopEventCount = shopEventCountMap.get(player.getUniqueId());
        if (shopEventCount == null) {
            if (!shopConfirmation.containsKey(player.getUniqueId()) || !shopConfirmation.get(player.getUniqueId()).equals(chestShop.getId())) {
                shopConfirmation.put(player.getUniqueId(), chestShop.getId());
                sendConfirmationMessage(player, chestShop);
                return;
            }
            if (chestShop.getType().equals("BUY")) {
                if (!hasRequiredItemsInChests(chestShop.getChestLocations(), chestShop.getItems())) {
                    Apied.sendMessage(player, "messages.shop.error.missingItems");
                    formatSign(sign, chestShop);
                    return;
                }
                if (!hasEnoughInventorySpace(player, chestShop.getItems())) {
                    Apied.sendMessage(player, "messages.shop.error.fullInventory");
                    formatSign(sign, chestShop);
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
                if (!hasEnoughStorageInChests(chestShop.getChestLocations(), chestShop.getItems())) {
                    Apied.sendMessage(player, "messages.shop.error.noSpace");
                    formatSign(sign, chestShop);
                    return;
                }
            }
        } else if (shopEventCount.getType().equals("maxUses")) {

        } else if (shopEventCount.getType().equals("maxPlayerUses")) {

        } else if (shopEventCount.getType().equals("maxDailyUses")) {

        }

    }

    public static void buyItems(Player player, ChestShop chestShop) {

    }

    public static void sellItems(Player player, ChestShop chestShop) {

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

            return "   " + item.getAmount() + " " + plural + " " + hoverInfo + translatable + "</hover>\n";
    }

    public static String chestFormatting(Location chestLocation, int price)  {
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

        if (inventory == null) {
            return "Invalid container";
        }
        return itemsFormatting(inventory.getContents(), price);
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

    private static String getItemKey(ItemStack item) {
        ReadableNBT nbt = NBT.itemStackToNBT(item);
        return item.getType().getKey().toString() + nbt.toString();
    }

    private static String getHoverInfo(ItemStack item) {
        String itemId = item.getType().getKey().toString().toLowerCase();
        int count = item.getAmount();
        if (Apied.configuration.getBannedPreviewItems().contains(itemId)) {
            return "<hover:show_item:'" + itemId + "':" + 1 + ">";
        }
        ReadableNBT nbt = NBT.itemStackToNBT(item);
        String nbtString = nbt.toString();
        return "<hover:show_item:'" + itemId + "':" + 1 + ":'" + nbtString + "'>";
    }

    public static int getBalance(Sign sign) {
        SignSide backSide = sign.getSide(Side.BACK);
        SignSide frontSide = sign.getSide(Side.FRONT);

        if (isValidSide(backSide) && isEmptySide(frontSide)) {
            return Integer.parseInt(backSide.getLine(3).trim());
        } else if (isValidSide(frontSide) && isEmptySide(backSide)) {
            return Integer.parseInt(frontSide.getLine(3).trim());
        }
        return 0;
    }

    public static String getType(Sign sign) {
        SignSide backSide = sign.getSide(Side.BACK);
        SignSide frontSide = sign.getSide(Side.FRONT);

        if (isValidSide(backSide) && isEmptySide(frontSide)) {
            String line0 = backSide.getLine(0).trim().toUpperCase();
            if (line0.equals("BUY") || line0.equals("KÖP")) {
                return "BUY";
            } else if (line0.equals("SELL") || line0.equals("SÄLJ")) {
                return "SELL";
            }
        } else if (isValidSide(frontSide) && isEmptySide(backSide)) {
            String line0 = frontSide.getLine(0).trim().toUpperCase();
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
        if (chestShop == null) {
            String type = getType(sign);
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
        } else {
            String type = chestShop.getType();
            if (type == null) {
                return;
            }
            if (ShopManager.hasRequiredItemsInChests(chestShop.getChestLocations(), chestShop.getItems())) {
                if (type.equals("BUY")) {
                    comp1 = (TextComponent) Apied.getMessageComponent("messages.shop.sign.stocked.buy");
                    comp2 = (TextComponent) Apied.getMessageComponent("messages.shop.sign.price", "%price%", Utils.formattedMoney(getBalance(sign)));
                } else if (type.equals("SELL")) {
                    comp1 = (TextComponent) Apied.getMessageComponent("messages.shop.sign.stocked.sell");
                    comp2 = (TextComponent) Apied.getMessageComponent("messages.shop.sign.price", "%price%", Utils.formattedMoney(getBalance(sign)));
                }
            } else {
                if (type.equals("BUY")) {
                    comp1 = (TextComponent) Apied.getMessageComponent("messages.shop.sign.unstocked.buy");
                    comp2 = (TextComponent) Apied.getMessageComponent("messages.shop.sign.price", "%price%", Utils.formattedMoney(getBalance(sign)));
                } else if (type.equals("SELL")) {
                    comp1 = (TextComponent) Apied.getMessageComponent("messages.shop.sign.unstocked.sell");
                    comp2 = (TextComponent) Apied.getMessageComponent("messages.shop.sign.price", "%price%", Utils.formattedMoney(getBalance(sign)));
                }
            }
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

    public static boolean validSign(Sign sign) {
        SignSide backSide = sign.getSide(Side.BACK);
        SignSide frontSide = sign.getSide(Side.FRONT);

        boolean backValid = isValidSide(backSide);
        boolean frontValid = isValidSide(frontSide);

        if (backValid && isEmptySide(frontSide)) {
            return true;
        }
        return frontValid && isEmptySide(backSide);
    }

    private static boolean isValidSide(SignSide side) {
        String line0 = side.getLine(0).trim().toUpperCase();
        String line3 = side.getLine(3).trim();

        if (!("BUY".equals(line0) || "SELL".equals(line0) || "KÖP".equals(line0) || "SÄLJ".equals(line0))) {
            return false;
        }
        try {
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
        if (block.getState() instanceof org.bukkit.block.Chest chest) {
            Inventory inventory = chest.getInventory();
            return Utils.itemStackArrayToBase64(inventory.getContents());
        }
        if (block.getState() instanceof ShulkerBox shulkerBox) {
            Inventory inventory = shulkerBox.getInventory();
            return Utils.itemStackArrayToBase64(inventory.getContents());
        }
        if (block.getState() instanceof Barrel barrel) {
            Inventory inventory = barrel.getInventory();
            return Utils.itemStackArrayToBase64(inventory.getContents());
        }
        return null;
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

    public static boolean hasRequiredItemsInChests(List<Location> chestLocations, ItemStack[] requiredItems) {
        for (Location chestLocation : chestLocations) {
            Block block = chestLocation.getBlock();
            InventoryHolder chest = null;

            if (block.getState() instanceof org.bukkit.block.Chest) {
                chest = (org.bukkit.block.Chest) block.getState();
            } else if (block.getState() instanceof ShulkerBox) {
                chest = (ShulkerBox) block.getState();
            } else if (block.getState() instanceof Barrel) {
                chest = (Barrel) block.getState();
            }

            if (chest != null && hasRequiredItemsInChest(chest, requiredItems)) {
                return true;
            }
        }
        return false;
    }


    public static boolean hasEnoughStorageInChest(InventoryHolder chest, ItemStack[] itemsToStore) {
        Inventory inventory = chest.getInventory();
        Map<Material, Integer> requiredCounts = new HashMap<>();

        for (ItemStack item : itemsToStore) {
            requiredCounts.put(item.getType(), requiredCounts.getOrDefault(item.getType(), 0) + item.getAmount());
        }

        for (Map.Entry<Material, Integer> entry : requiredCounts.entrySet()) {
            Material material = entry.getKey();
            int requiredAmount = entry.getValue();

            for (ItemStack inventoryItem : inventory.getContents()) {
                if (inventoryItem != null && inventoryItem.getType() == material) {
                    int availableSpace = inventoryItem.getMaxStackSize() - inventoryItem.getAmount();
                    requiredAmount -= availableSpace;
                    if (requiredAmount <= 0) {
                        break;
                    }
                }
            }

            while (requiredAmount > 0) {
                int emptySlot = inventory.firstEmpty();
                if (emptySlot == -1) {
                    return false;
                }
                requiredAmount -= material.getMaxStackSize();
            }
        }

        return requiredCounts.values().stream().allMatch(count -> count <= 0);
    }

    public static boolean hasEnoughInventorySpace(Player player, ItemStack[] itemsToStore) {
        Inventory inventory = player.getInventory();
        Map<Material, Integer> requiredCounts = new HashMap<>();

        for (ItemStack item : itemsToStore) {
            requiredCounts.put(item.getType(), requiredCounts.getOrDefault(item.getType(), 0) + item.getAmount());
        }

        for (Map.Entry<Material, Integer> entry : requiredCounts.entrySet()) {
            Material material = entry.getKey();
            int requiredAmount = entry.getValue();

            for (ItemStack inventoryItem : inventory.getContents()) {
                if (inventoryItem != null && inventoryItem.getType() == material) {
                    int availableSpace = inventoryItem.getMaxStackSize() - inventoryItem.getAmount();
                    requiredAmount -= availableSpace;
                    if (requiredAmount <= 0) {
                        break;
                    }
                }
            }

            while (requiredAmount > 0) {
                int emptySlot = inventory.firstEmpty();
                if (emptySlot == -1) {
                    return false;
                }
                requiredAmount -= material.getMaxStackSize();
            }
        }

        return requiredCounts.values().stream().allMatch(count -> count <= 0);
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
        for (Location chestLocation : chestLocations) {
            Block block = chestLocation.getBlock();
            InventoryHolder chest = null;

            if (block.getState() instanceof org.bukkit.block.Chest) {
                chest = (org.bukkit.block.Chest) block.getState();
            } else if (block.getState() instanceof ShulkerBox) {
                chest = (ShulkerBox) block.getState();
            } else if (block.getState() instanceof Barrel) {
                chest = (Barrel) block.getState();
            }

            if (chest != null && hasEnoughStorageInChest(chest, itemsToStore)) {
                return true;
            }
        }
        return false;
    }


    @Getter
    public static class ShopEventCount {
        private int count;
        private final String type;
        private final UUID target;

        public ShopEventCount(int count, String type, UUID target) {
            this.count = count;
            this.type = type;
            this.target = target;
        }

        public void decrementCount() {
            count--;
        }
    }
}
