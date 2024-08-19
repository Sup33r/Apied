package live.supeer.apied;

import co.aikar.idb.DB;
import co.aikar.idb.DbRow;
import lombok.Getter;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class MPlayerManager {

    @Getter
    private static final List<MPlayer> players = new ArrayList<>();

    public static void addPlayer(MPlayer player) {
        players.add(player);
    }

    public static void removePlayer(MPlayer player) {
        players.remove(player);
    }

    public static MPlayer createPlayer(Player player) {
        try {
            DB.executeInsert("INSERT INTO `md_players` (`uuid`, `name`, `firstJoin`, `latestJoin`, `balance`) VALUES (?, ?, ?, ?, ?)",
                    player.getUniqueId().toString(),
                    player.getName(),
                    Utils.getTimestamp(),
                    Utils.getTimestamp(),
                    Apied.configuration.getStartingBalance());
            return new MPlayer(DB.getFirstRow("SELECT * FROM `md_players` WHERE `uuid` = ?", player.getUniqueId().toString()));
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public static MPlayer getPlayerFromUUID(UUID uuid) {
        try {
            DbRow row = DB.getFirstRow("SELECT * FROM `md_players` WHERE `uuid` = ?", uuid.toString());
            return row != null ? new MPlayer(row) : null;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

    }

    public static MPlayer getPlayerFromName(String name) {
        try {
            DbRow row = DB.getFirstRow("SELECT * FROM `md_players` WHERE `name` = ?", name);
            return row != null ? new MPlayer(row) : null;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public static MPlayer getPlayerFromPlayer(Player player) {
        try {
            DbRow row = DB.getFirstRow("SELECT * FROM `md_players` WHERE `uuid` = ?", player.getUniqueId().toString());
            return row != null ? new MPlayer(row) : null;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public static List<String> getPlayerNamesFromUUIDs(List<UUID> uuids) {
        List<String> names = new ArrayList<>();
        for (UUID uuid : uuids) {
            MPlayer player = getPlayerFromUUID(uuid);
            if (player != null) {
                names.add(player.getName());
            }
        }
        return names;
    }

    public static boolean playerExists(UUID uuid) {
        return getPlayerFromUUID(uuid) != null;
    }


    public static List<String> getIgnoredByPlayers(UUID playerUUID) {
        List<String> ignoredByPlayers = new ArrayList<>();
        try {
            for (DbRow ignoredBy : DB.getResults("SELECT * FROM `md_players` WHERE `ignores` LIKE ? AND `ignores` IS NOT NULL", "%" + playerUUID.toString() + "%")) {
                ignoredByPlayers.add(ignoredBy.getString("name"));
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return ignoredByPlayers;
    }

    public static void placeBan(MPlayer player, Player placer, String reason, long length) {
        try {
            DB.executeUpdate("INSERT INTO `md_bans` (`playerUUID`, `placerUUID`, `reason`, `placeTime`, `length`) VALUES (?, ?, ?, ?, ?)",
                    player.getUuid().toString(),
                    placer.getUniqueId().toString(),
                    reason,
                    Utils.getTimestamp(),
                    length);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public static void removeBan(UUID playerUUID) {
        try {
            DB.executeUpdate("UPDATE `md_bans` SET `length` = ? WHERE `playerUUID` = ? AND `length` > ?", Utils.getTimestamp(), playerUUID.toString(), Utils.getTimestamp());
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public static boolean isBanned(UUID playerUUID) {
        try {
            return DB.getFirstRow("SELECT * FROM `md_bans` WHERE `playerUUID` = ? AND `length` > ?", playerUUID.toString(), Utils.getTimestamp()) != null;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public static String getBanReason(UUID playerUUID) {
        try {
            DbRow row = DB.getFirstRow("SELECT * FROM `md_bans` WHERE `playerUUID` = ? AND `length` > ?", playerUUID.toString(), Utils.getTimestamp());
            return row != null ? row.getString("reason") : null;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public static long getBanLength(UUID playerUUID) {
        try {
            DbRow row = DB.getFirstRow("SELECT * FROM `md_bans` WHERE `playerUUID` = ? AND `length` > ?", playerUUID.toString(), Utils.getTimestamp());
            return row != null ? row.getLong("length") : 0;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public static List<ExpiringBan> getExpiringBans(long time) {
        List<ExpiringBan> expiringBans = new ArrayList<>();
        try {
            for (DbRow row : DB.getResults("SELECT * FROM `md_bans` WHERE `length` < ?", time)) {
                UUID playerUUID = UUID.fromString(row.getString("playerUUID"));
                long unbanTime = row.getLong("length");
                expiringBans.add(new ExpiringBan(playerUUID, unbanTime));
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return expiringBans;
    }

    public static ExpiringBan getExpiringBan(UUID playerUUID) {
        try {
            DbRow row = DB.getFirstRow("SELECT * FROM `md_bans` WHERE `playerUUID` = ? AND `length` > ?", playerUUID.toString(), Utils.getTimestamp());
            return row != null ? new ExpiringBan(playerUUID, row.getLong("length")) : null;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
