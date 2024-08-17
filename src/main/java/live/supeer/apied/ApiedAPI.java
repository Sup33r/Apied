package live.supeer.apied;

import lombok.Setter;
import org.bukkit.entity.Player;

import java.util.UUID;

public class ApiedAPI {
    @Setter
    private static Apied plugin;

    public static MPlayer getPlayer(UUID playerUUID) {
        return MPlayerManager.getPlayerFromUUID(playerUUID);
    }

    public static MPlayer getPlayer(String playerName) {
        return MPlayerManager.getPlayerFromName(playerName);
    }

    public static MPlayer getPlayer(Player player) {
        return MPlayerManager.getPlayerFromPlayer(player);
    }

    public static MPlayer createPlayer(Player player) {
        return MPlayerManager.createPlayer(player);
    }

    public static MPlayer addPlayer(MPlayer player) {
        MPlayerManager.addPlayer(player);
        return player;
    }

    public static void removePlayer(MPlayer player) {
        MPlayerManager.removePlayer(player);
    }
}
