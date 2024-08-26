package live.supeer.apied;

import co.aikar.idb.DB;
import co.aikar.idb.DbRow;
import de.tr7zw.changeme.nbtapi.NBT;
import de.tr7zw.changeme.nbtapi.iface.ReadableNBT;
import net.kyori.adventure.text.TextComponent;
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

import java.util.*;

public class ShopManager {
    public static final HashMap<UUID, Integer> shopConfirmation = new HashMap<>();
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
                Apied.sendMessage(player, "messages.shop.success.create.buy", "%items%", itemsFormatting(chestLocation, balance));
            } else {
                Apied.sendMessage(player, "messages.shop.success.create.sell", "%items%", itemsFormatting(chestLocation, balance));
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
                DB.executeUpdate("UPDATE md_shops SET removed = ? WHERE id = ?", true, chestShop.getId());
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static String itemsFormatting(Location chestLocation, int price) {
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

        Map<String, ItemStack> itemMap = new HashMap<>();
        for (ItemStack item : inventory.getContents()) {
            if (item != null && item.getType() != Material.AIR) {
                String key = getItemKey(item);
                ItemStack existingItem = itemMap.get(key);
                if (existingItem == null) {
                    itemMap.put(key, item.clone());
                } else {
                    existingItem.setAmount(existingItem.getAmount() + item.getAmount());
                }
            }
        }
        StringBuilder formattedItems = new StringBuilder();
        for (ItemStack item : itemMap.values()) {
            String itemName = item.getType().getKey().getKey();
            String translatable = "";
            if (!item.getType().isBlock()) {
                translatable = "<lang:item.minecraft." + itemName + ">";
            } else {
                translatable = "<lang:block.minecraft." + itemName + ">";
            }
            String hoverInfo = getHoverInfo(item);
            String plural = item.getAmount() == 1 ? Apied.getMessage("messages.word.count.singular") : Apied.getMessage("messages.word.count.plural");

            formattedItems.append("   ")
                    .append(item.getAmount())
                    .append(" ")
                    .append(plural)
                    .append(" ")
                    .append(hoverInfo)
                    .append(translatable)
                    .append("</hover>\n");
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
        for (ItemStack requiredItem : requiredItems) {
            if (!inventory.containsAtLeast(requiredItem, requiredItem.getAmount())) {
                return false;
            }
        }
        return true;
    }

    public static boolean hasRequiredItemsInChests(List<Location> chestLocations, ItemStack[] requiredItems) {
        for (Location chestLocation : chestLocations) {
            Block block = chestLocation.getBlock();
            if (block.getState() instanceof org.bukkit.block.Chest chest) {
                Inventory inventory = chest.getInventory();
                for (ItemStack requiredItem : requiredItems) {
                    if (!inventory.containsAtLeast(requiredItem, requiredItem.getAmount())) {
                        return false;
                    }
                }
            }
            if (block.getState() instanceof ShulkerBox shulkerBox) {
                Inventory inventory = shulkerBox.getInventory();
                for (ItemStack requiredItem : requiredItems) {
                    if (!inventory.containsAtLeast(requiredItem, requiredItem.getAmount())) {
                        return false;
                    }
                }
            }
            if (block.getState() instanceof Barrel barrel) {
                Inventory inventory = barrel.getInventory();
                for (ItemStack requiredItem : requiredItems) {
                    if (!inventory.containsAtLeast(requiredItem, requiredItem.getAmount())) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    public static boolean hasEnoughStorageInChest(InventoryHolder chest, ItemStack[] itemsToStore) {
        Inventory inventory = chest.getInventory();
        for (ItemStack item : itemsToStore) {
            if (inventory.firstEmpty() == -1 && !inventory.containsAtLeast(item, item.getAmount())) {
                return false;
            }
        }
        return true;
    }

    public static boolean hasEnoughStorageInChests(List<Location> chestLocations, ItemStack[] itemsToStore) {
        for (Location chestLocation : chestLocations) {
            Block block = chestLocation.getBlock();
            if (block.getState() instanceof org.bukkit.block.Chest chest) {
                Inventory inventory = chest.getInventory();
                for (ItemStack item : itemsToStore) {
                    if (inventory.firstEmpty() == -1 && !inventory.containsAtLeast(item, item.getAmount())) {
                        return false;
                    }
                }
            }
            if (block.getState() instanceof ShulkerBox shulkerBox) {
                Inventory inventory = shulkerBox.getInventory();
                for (ItemStack item : itemsToStore) {
                    if (inventory.firstEmpty() == -1 && !inventory.containsAtLeast(item, item.getAmount())) {
                        return false;
                    }
                }
            }
            if (block.getState() instanceof Barrel barrel) {
                Inventory inventory = barrel.getInventory();
                for (ItemStack item : itemsToStore) {
                    if (inventory.firstEmpty() == -1 && !inventory.containsAtLeast(item, item.getAmount())) {
                        return false;
                    }
                }
            }
        }
        return true;
    }
}
