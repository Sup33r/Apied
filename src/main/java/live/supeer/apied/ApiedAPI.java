package live.supeer.apied;

import lombok.Setter;
import net.kyori.adventure.text.Component;
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

    public static Component getTABPrefix(Player player, Boolean afk) {
        MPlayer mPlayer = ApiedAPI.getPlayer(player);
        if (mPlayer == null) {
            return null;
        }
        String playerName = player.getName();
        if (afk) {
            playerName = "<italic>" + playerName + "</italic>";
        }
        if (mPlayer.getChatprefix() != null && !mPlayer.getChatprefix().isEmpty()) {
            return Apied.stringToMiniMessage(mPlayer.getChatprefix() + playerName);
        }
        if (player.hasPermission("mandatory.mod")) {
            return Apied.getMessageComponent("messages.chat.prefix.tab.mod", "%player%", playerName);
        }
        if (afk) {
            return Apied.stringToMiniMessage(playerName);
        } else {
            return null;
        }
    }
}
