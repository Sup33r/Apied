package live.supeer.apied;

import co.aikar.idb.DB;
import co.aikar.idb.DbRow;
import org.bukkit.Location;
import org.bukkit.block.*;
import org.bukkit.block.sign.Side;
import org.bukkit.block.sign.SignSide;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

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
        if (!validChest(chestLocation, player)) {
            return;
        }
        String serializedItems = serializeItems(chestLocation);
        Sign sign = signLocation.getBlock().getState() instanceof Sign ? (Sign) signLocation.getBlock().getState() : null;
        if (sign == null) {
            Apied.sendMessage(player, "messages.shop.error.signNotFound");
            return;
        }
        if (!validSign(sign)) {
            Apied.sendMessage(player, "messages.shop.error.invalidSign");
            return;
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

    public static boolean hasEnoughStorageInChest(InventoryHolder chest, ItemStack[] itemsToStore) {
        Inventory inventory = chest.getInventory();
        for (ItemStack item : itemsToStore) {
            if (inventory.firstEmpty() == -1 && !inventory.containsAtLeast(item, item.getAmount())) {
                return false;
            }
        }
        return true;
    }
}
