package live.supeer.apied;

import co.aikar.idb.DbRow;
import lombok.Getter;
import org.bukkit.Location;

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
    //HÄR SKA DET VARA EN OCKSÅ MED SHOPS; private List<Shop> shops;



    public Chest(DbRow data) {
        this.id = data.getInt("id");
        this.ownerUUID = UUID.fromString(data.getString("ownerUUID"));
        this.location = Utils.unformatLocation(data.getString("location"));
        this.type = ChestType.getByName(data.getString("type"));
        this.dateTime = data.getLong("dateTime");
        this.sharedPlayers = Utils.stringToUUIDList(data.getString("sharedPlayers"));
    }
}
