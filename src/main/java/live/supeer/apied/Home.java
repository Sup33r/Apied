package live.supeer.apied;

import co.aikar.idb.DB;
import co.aikar.idb.DbRow;
import lombok.Getter;
import org.bukkit.Location;

import java.util.UUID;

@Getter
public class Home {

    private Location location;
    private UUID playerUUID;
    private String name;

    public Home(DbRow data) {
        this.location = Utils.stringToLocation(data.getString("location"));
        this.playerUUID = UUID.fromString(data.getString("playerUUID"));
        this.name = data.getString("homeName");
    }

    public void updateHomeLocation(Location location) {
        this.location = location;
        DB.executeUpdateAsync("UPDATE `md_homes` SET `location` = ? WHERE `playerUUID` = ? AND `homeName` = ?;", Utils.locationToString(location), playerUUID.toString(), name);
    }
}
