package live.supeer.apied;

import co.aikar.idb.DbRow;
import lombok.Getter;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Getter
public class ChestShop {
    private final int id;
    private final UUID ownerUUID;
    private final Location signLocation;
    private List<Location> chestLocations;
    private final String type;
    private int price;
    private int maxUses;
    private int maxPlayerUses;
    private int maxDailyUses;
    private final ItemStack[] items;
    private final long dateTime;
    private boolean removed;

    public ChestShop(DbRow data) throws IOException {
        this.id = data.getInt("id");
        this.ownerUUID = UUID.fromString(data.getString("ownerUUID"));
        this.signLocation = Utils.stringToLocation(data.getString("signLocation"));
        this.chestLocations = Utils.stringToLocationList(data.getString("chestLocations"));
        this.price = data.getInt("price");
        this.type = data.getString("type");
        this.maxUses = data.getInt("maxUses");
        this.maxPlayerUses = data.getInt("maxPlayerUses");
        this.maxDailyUses = data.getInt("maxDailyUses");
        this.items = Utils.itemStackArrayFromBase64(data.getString("items"));
        this.dateTime = data.getLong("dateTime");
        this.removed = data.get("removed");
    }

}
