package live.supeer.apied;

import co.aikar.idb.DB;
import co.aikar.idb.DbRow;
import lombok.Getter;
import org.bukkit.Location;
import org.bukkit.block.sign.Side;
import org.bukkit.inventory.ItemStack;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

@Getter
public class ChestShop {
    private final int id;
    private final UUID ownerUUID;
    private final Location signLocation;
    private final List<Location> chestLocations;
    private final String type;
    private final int price;
    private int maxUses;
    private int maxPlayerUses;
    private int maxPlayerDailyUses;
    private int maxDailyUses;
    private final ItemStack[] items;
    private final long dateTime;
    private boolean admin;
    private boolean removed;
    private final Side signSide;

    public ChestShop(DbRow data) throws IOException {
        this.id = data.getInt("id");
        this.ownerUUID = UUID.fromString(data.getString("ownerUUID"));
        this.signLocation = Utils.stringToLocation(data.getString("signLocation"));
        this.chestLocations = Utils.stringToLocationList(data.getString("chestLocations"));
        this.price = data.getInt("price");
        this.type = data.getString("type");
        this.maxUses = data.getInt("maxUses");
        this.maxPlayerUses = data.getInt("maxPlayerUses");
        this.maxPlayerDailyUses = data.getInt("maxPlayerDailyUses");
        this.maxDailyUses = data.getInt("maxDailyUses");
        this.items = Utils.itemStackArrayFromBase64(data.getString("items"));
        this.dateTime = data.getLong("dateTime");
        this.signSide = data.getString("signSide") != null ? Side.valueOf(data.getString("signSide")) : Side.FRONT;
        this.admin = data.get("admin");
        this.removed = data.get("removed");
    }

    public void addChestLocation(Location location) {
        chestLocations.add(location);
        try {
            DB.executeUpdate("UPDATE `md_shops` SET `chestLocations` = ? WHERE `id` = ?", Utils.locationListToString(chestLocations), this.id);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void removeChestLocation(Location location) {
        chestLocations.remove(location);
        try {
            DB.executeUpdate("UPDATE `md_shops` SET `chestLocations` = ? WHERE `id` = ?", Utils.locationListToString(chestLocations), this.id);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void setRemoved(boolean removed) {
        this.removed = removed;
        try {
            DB.executeUpdate("UPDATE `md_shops` SET `removed` = ? WHERE `id` = ?", removed, this.id);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void setAdmin(boolean admin) {
        this.admin = admin;
        try {
            DB.executeUpdate("UPDATE `md_shops` SET `admin` = ? WHERE `id` = ?", admin, this.id);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void setMaxUses(int maxUses) {
        this.maxUses = maxUses;
        try {
            DB.executeUpdate("UPDATE `md_shops` SET `maxUses` = ? WHERE `id` = ?", maxUses, this.id);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void setMaxPlayerUses(int maxPlayerUses) {
        this.maxPlayerUses = maxPlayerUses;
        try {
            DB.executeUpdate("UPDATE `md_shops` SET `maxPlayerUses` = ? WHERE `id` = ?", maxPlayerUses, this.id);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void setMaxPlayerDailyUses(int maxPlayerDailyUses) {
        this.maxPlayerDailyUses = maxPlayerDailyUses;
        try {
            DB.executeUpdate("UPDATE `md_shops` SET `maxPlayerDailyUses` = ? WHERE `id` = ?", maxPlayerDailyUses, this.id);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void setMaxDailyUses(int maxDailyUses) {
        this.maxDailyUses = maxDailyUses;
        try {
            DB.executeUpdate("UPDATE `md_shops` SET `maxDailyUses` = ? WHERE `id` = ?", maxDailyUses, this.id);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

}
