package live.supeer.apied;

import co.aikar.idb.DB;
import co.aikar.idb.DbRow;
import lombok.Getter;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Player;
import org.bukkit.inventory.DoubleChestInventory;
import org.bukkit.inventory.Inventory;

import java.io.IOException;
import java.sql.SQLException;
import java.util.*;

public class ChestManager {
    private static final Map<UUID, ChestEventCount> chestEventCountMap = new HashMap<>();

    public static final HashMap<UUID, Integer> chestInventories = new HashMap<>();

    public static live.supeer.apied.Chest getClaimedChest(Location location) {
        try {
            DbRow row = DB.getFirstRow("SELECT * FROM `md_chests` WHERE `location` = ?", Utils.formatLocation(location));
            return row != null ? new live.supeer.apied.Chest(row) : null;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public static void newChest(Location location, Player player, String type) {
        try {
            DB.executeInsert("INSERT INTO `md_chests` (`ownerUUID`, `location`, `type`, `dateTime`) VALUES (?, ?, ?, ?)",
                    player.getUniqueId().toString(),
                    Utils.formatLocation(location),
                    type,
                    Utils.getTimestamp());
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public static void removeChest(Location location) {
        try {
            DB.executeUpdate("DELETE FROM `md_chests` WHERE `location` = ?", Utils.formatLocation(location));
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    //        if (!block.getType().equals(Material.CHEST) && !block.getType().equals(Material.TRAPPED_CHEST) && !block.getType().equals(Material.BARREL) && !block.getType().equals(Material.SHULKER_BOX)) {
    //            return false;
    //        }

    public static void handleChestGive(Location location, Player player, UUID target) {
        Block block = location.getBlock();
        live.supeer.apied.Chest chest = getClaimedChest(location);
        if (block.getState() instanceof Chest chest1) {
            Inventory inventory = chest1.getInventory();
            if (inventory instanceof DoubleChestInventory doubleChestInventory) {
                chest = getClaimedChest(doubleChestInventory.getLeftSide().getLocation());
            }
        }
        if (chest == null) {
            Apied.sendMessage(player, "messages.chest.error.give.unlocked");
            return;
        }
        if (!chest.getOwnerUUID().equals(player.getUniqueId())) {
            Apied.sendMessage(player, "messages.chest.error.give.notOwner");
            return;
        }
        if (chest.getOwnerUUID().equals(target)) {
            Apied.sendMessage(player, "messages.chest.error.give.self");
            return;
        }
        try {
            DB.executeUpdate("UPDATE `md_chests` SET `ownerUUID` = ? WHERE `location` = ?", target, Utils.formatLocation(location));
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        Apied.sendMessage(player, "messages.chest.give", "%player%", Objects.requireNonNull(MPlayerManager.getPlayerFromUUID(target)).getName());
    }

    public static void handleChestShare(Location location, Player player, UUID target) {
        Block block = location.getBlock();
        live.supeer.apied.Chest chest = getClaimedChest(location);
        if (block.getState() instanceof Chest chest1) {
            Inventory inventory = chest1.getInventory();
            if (inventory instanceof DoubleChestInventory doubleChestInventory) {
                chest = getClaimedChest(doubleChestInventory.getLeftSide().getLocation());
            }
        }
        if (chest == null) {
            Apied.sendMessage(player, "messages.chest.error.share.unlocked");
            return;
        }
        if (!chest.getOwnerUUID().equals(player.getUniqueId())) {
            Apied.sendMessage(player, "messages.chest.error.share.notOwner");
            return;
        }
        if (chest.getOwnerUUID().equals(target)) {
            Apied.sendMessage(player, "messages.chest.error.share.self");
            return;
        }
        List<UUID> sharedPlayers = chest.getSharedPlayers();
        if (chest.getSharedPlayers().contains(target)) {
            sharedPlayers.remove(target);
            try {
                DB.executeUpdate("UPDATE `md_chests` SET `sharedPlayers` = ? WHERE `location` = ?", Utils.uuidListToString(sharedPlayers), Utils.formatLocation(location));
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
            Apied.sendMessage(player, "messages.chest.share.stopped");
            return;
        }
        sharedPlayers.add(target);
        try {
            DB.executeUpdate("UPDATE `md_chests` SET `sharedPlayers` = ? WHERE `location` = ?", Utils.uuidListToString(sharedPlayers), Utils.formatLocation(location));
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        Apied.sendMessage(player, "messages.chest.share.started");
    }

    public static void handleChestClick(Location location, Player player) {
        Block block = location.getBlock();
        String type = "";
        if (block.getState() instanceof Chest chest) {
            Inventory inventory = chest.getInventory();
            if (inventory instanceof DoubleChestInventory doubleChestInventory) {
                type = ChestType.DOUBLE.getType();
                live.supeer.apied.Chest chest1 = getClaimedChest(doubleChestInventory.getLeftSide().getLocation());
                live.supeer.apied.Chest chest2 = getClaimedChest(doubleChestInventory.getRightSide().getLocation());
                if (chest1 == null && chest2 == null) {
                    newChest(doubleChestInventory.getLeftSide().getLocation(), player, type);
                    Apied.sendMessage(player, "messages.chest.lock");
                }
                if ((chest1 != null && chest2 == null) || (chest1 == null && chest2 != null)) {
                    chest1 = chest1 != null ? chest1 : chest2;
                    if (chest1.getOwnerUUID().equals(player.getUniqueId())) {
                        removeChest(chest1.getLocation());
                        Apied.sendMessage(player, "messages.chest.unlock");
                    } else {
                        Apied.sendMessage(player, "messages.chest.error.unlock.notOwner");
                    }
                }
            } else {
                type = ChestType.SINGLE.getType();
                live.supeer.apied.Chest chest1 = getClaimedChest(chest.getLocation());
                if (chest1 == null) {
                    newChest(location, player, type);
                    Apied.sendMessage(player, "messages.chest.lock");
                } else {
                    if (chest1.getOwnerUUID().equals(player.getUniqueId())) {
                        removeChest(chest1.getLocation());
                        Apied.sendMessage(player, "messages.chest.unlock");
                    } else {
                        Apied.sendMessage(player, "messages.chest.error.unlock.notOwner");
                    }
                }
            }
            return;
        }
        if (block.getState() instanceof Barrel barrel) {
            type = ChestType.BARREL.getType();
            live.supeer.apied.Chest chest = getClaimedChest(barrel.getLocation());
            if (chest == null) {
                newChest(location, player, type);
                Apied.sendMessage(player, "messages.chest.lock");
            } else {
                if (chest.getOwnerUUID().equals(player.getUniqueId())) {
                    removeChest(chest.getLocation());
                    Apied.sendMessage(player, "messages.chest.unlock");
                } else {
                    Apied.sendMessage(player, "messages.chest.error.unlock.notOwner");
                }
            }
            return;
        }
        if (block.getState() instanceof ShulkerBox shulkerBox) {
            type = ChestType.SHULKER.getType();
            live.supeer.apied.Chest chest = getClaimedChest(shulkerBox.getLocation());
            if (chest == null) {
                newChest(location, player, type);
                Apied.sendMessage(player, "messages.chest.lock");
            } else {
                if (chest.getOwnerUUID().equals(player.getUniqueId())) {
                    removeChest(chest.getLocation());
                    Apied.sendMessage(player, "messages.chest.unlock");
                } else {
                    Apied.sendMessage(player, "messages.chest.error.unlock.notOwner");
                }
            }
        }
    }

    public static void handleArchiveItems(Player archiver, Location location, boolean city, boolean self) {
        Block block = location.getBlock();
        live.supeer.apied.Chest chest1;
        if (block.getState() instanceof Chest chest) {
            Inventory inventory = chest.getInventory();
            if (inventory instanceof DoubleChestInventory doubleChestInventory) {
                chest1 = getClaimedChest(doubleChestInventory.getLeftSide().getLocation());
            } else {
                chest1 = getClaimedChest(chest.getLocation());
            }
            if (chest1 == null) {
                Apied.sendMessage(archiver, "messages.chest.error.archive.unlocked");
                return;
            }
            MPlayer mPlayer = MPlayerManager.getPlayerFromUUID(chest1.getOwnerUUID());
            if (mPlayer == null) {
                return;
            }
            if (chest1.getOwnerUUID().equals(archiver.getUniqueId()) && self) {
                archiveInventory(archiver.getUniqueId(), mPlayer.getUuid(), inventory);
                Apied.sendMessage(archiver, "messages.chest.archive", "%player%", mPlayer.getName());
                return;
            } else if (city) {
                archiveInventory(archiver.getUniqueId(), mPlayer.getUuid(), inventory);
                Apied.sendMessage(archiver, "messages.chest.archive", "%player%", mPlayer.getName());
                return;
            }
            Apied.sendMessage(archiver, "messages.permissionDenied");
            return;
        }
        if (block.getState() instanceof Barrel barrel) {
            live.supeer.apied.Chest chest = getClaimedChest(barrel.getLocation());
            if (chest == null) {
                Apied.sendMessage(archiver, "messages.chest.error.archive.unlocked");
                return;
            }
            MPlayer mPlayer = MPlayerManager.getPlayerFromUUID(chest.getOwnerUUID());
            if (mPlayer == null) {
                return;
            }
            if (chest.getOwnerUUID().equals(archiver.getUniqueId()) && self) {
                archiveInventory(archiver.getUniqueId(), mPlayer.getUuid(), barrel.getInventory());
                Apied.sendMessage(archiver, "messages.chest.archive", "%player%", mPlayer.getName());
                return;
            } else if (city) {
                archiveInventory(archiver.getUniqueId(), mPlayer.getUuid(), barrel.getInventory());
                Apied.sendMessage(archiver, "messages.chest.archive", "%player%", mPlayer.getName());
                return;
            }
            Apied.sendMessage(archiver, "messages.permissionDenied");
            return;
        }
        if (block.getState() instanceof ShulkerBox shulkerBox) {
            live.supeer.apied.Chest chest = getClaimedChest(shulkerBox.getLocation());
            if (chest == null) {
                Apied.sendMessage(archiver, "messages.chest.error.archive.unlocked");
                return;
            }
            MPlayer mPlayer = MPlayerManager.getPlayerFromUUID(chest.getOwnerUUID());
            if (mPlayer == null) {
                return;
            }
            if (chest.getOwnerUUID().equals(archiver.getUniqueId()) && self) {
                archiveInventory(archiver.getUniqueId(), mPlayer.getUuid(), shulkerBox.getInventory());
                Apied.sendMessage(archiver, "messages.chest.archive", "%player%", mPlayer.getName());
                return;
            } else if (city) {
                archiveInventory(archiver.getUniqueId(), mPlayer.getUuid(), shulkerBox.getInventory());
                Apied.sendMessage(archiver, "messages.chest.archive", "%player%", mPlayer.getName());
                return;
            }
            Apied.sendMessage(archiver, "messages.permissionDenied");
        }
    }

    public static void archiveInventory(UUID archiver, UUID owner, Inventory inventory) {
        try {
            DB.executeInsert("INSERT INTO `md_archives` (`archiver`, `owner`, `items`, `dateTime`) VALUES (?, ?, ?, ?)",
                    archiver.toString(),
                    owner.toString(),
                    Utils.toBase64(inventory),
                    Utils.getTimestamp());
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
    public static int getFirstArchiveId(UUID playerUUID) {
        try {
            DbRow row = DB.getFirstRow("SELECT * FROM `md_archives` WHERE `owner` = ? ORDER BY `dateTime` ASC LIMIT 1", playerUUID.toString());
            return row != null ? row.getInt("id") : -1;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public static boolean hasArchivedItems(UUID playerUUID) {
        try {
            return DB.getFirstRow("SELECT * FROM `md_archives` WHERE `owner` = ?", playerUUID.toString()) != null;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public static Inventory getInventoryFromArchive(int id) {
        try {
            DbRow row = DB.getFirstRow("SELECT * FROM `md_archives` WHERE `id` = ?", id);
            if (row == null) {
                return null;
            }
            return Utils.fromBase64(row.getString("items"));
        } catch (SQLException | IOException e) {
            throw new RuntimeException(e);
        }
    }
    public static void removeArchive(int id) {
        try {
            DB.executeUpdate("DELETE FROM `md_archives` WHERE `id` = ?", id);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public static boolean isChestEventCounting(UUID playerUUID) {
        return chestEventCountMap.containsKey(playerUUID);
    }

    public static ChestEventCount getChestEventCount(UUID playerUUID) {
        return chestEventCountMap.get(playerUUID);
    }

    public static void startChestEventCount(UUID playerUUID, String type, int count, UUID target) {
        chestEventCountMap.put(playerUUID, new ChestEventCount(count, type, target));
    }

    public static void stopChestEventCount(UUID playerUUID) {
        chestEventCountMap.remove(playerUUID);
    }

    public static void decrementChestEventCount(UUID playerUUID) {
        ChestEventCount chestEventCount = chestEventCountMap.get(playerUUID);
        if (chestEventCount != null) {
            chestEventCount.decrementCount();
            if (chestEventCount.getCount() <= 0) {
                stopChestEventCount(playerUUID);
            }
        }
    }

    @Getter
    public static class ChestEventCount {
        private int count;
        private final String type;
        private final UUID target;

        public ChestEventCount(int count, String type, UUID target) {
            this.count = count;
            this.type = type;
            this.target = target;
        }

        public void decrementCount() {
            count--;
        }
    }
}
