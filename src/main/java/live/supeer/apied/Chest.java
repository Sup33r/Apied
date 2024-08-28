package live.supeer.apied;

import co.aikar.idb.DB;
import co.aikar.idb.DbRow;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.Location;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

@Getter
public class Chest {
    private final int id;
    private UUID ownerUUID;
    private Location location;
    private ChestType type;
    private long dateTime;
    private List<UUID> sharedPlayers;
    private List<Integer> linkedShopIds;



    public Chest(DbRow data) {
        this.id = data.getInt("id");
        this.ownerUUID = UUID.fromString(data.getString("ownerUUID"));
        this.location = Utils.stringToLocation(data.getString("location"));
        this.type = ChestType.getByName(data.getString("type"));
        this.dateTime = data.getLong("dateTime");
        this.sharedPlayers = Utils.stringToUUIDList(data.getString("sharedPlayers"));
        this.linkedShopIds = Utils.stringToIntegerList(data.getString("shops"));
    }

    public void setLocation(Location location) {
        this.location = location;
        try {
            DB.executeUpdate("UPDATE `md_chests` SET `location` = ? WHERE `id` = ?", Utils.locationToString(location), this.id);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void setType(ChestType type) {
        this.type = type;
        try {
            DB.executeUpdate("UPDATE `md_chests` SET `type` = ? WHERE `id` = ?", type.getType(), this.id);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
    public void addShop(int shopId) {
        linkedShopIds.add(shopId);
        try {
            DB.executeUpdate("UPDATE `md_chests` SET `shops` = ? WHERE `id` = ?", Utils.integerListToString(linkedShopIds), this.id);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void removeShop(int shopId) {
        linkedShopIds.remove((Integer) shopId);
        try {
            DB.executeUpdate("UPDATE `md_chests` SET `shops` = ? WHERE `id` = ?", Utils.integerListToString(linkedShopIds), this.id);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
