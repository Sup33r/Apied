package live.supeer.apied;

import co.aikar.idb.DB;
import co.aikar.idb.DbRow;
import org.bukkit.Location;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.block.ShulkerBox;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.block.sign.SignSide;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
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
}
