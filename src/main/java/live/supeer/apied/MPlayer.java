package live.supeer.apied;

import co.aikar.idb.DB;
import co.aikar.idb.DbRow;
import lombok.Getter;
import org.bukkit.Location;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Getter
public class MPlayer {
    private final UUID uuid;
    private String name;
    private final long firstJoin;
    private long latestJoin;
    private long latestLeave;
    private char[] flags;
    private int balance;
    private List<UUID> ignores;
    private String chatprefix;
    private String chatChannel;
    private final List<Home> homes = new ArrayList<>();
    private Location lastLocation;
    private Location backLocation;
    private boolean isBanned;
    private boolean modzoneAccess;

    public MPlayer(DbRow data) {
        this.uuid = UUID.fromString(data.getString("uuid"));
        this.name = data.getString("name");
        this.firstJoin = data.getLong("firstJoin") != null ? data.getLong("firstJoin") : 0L;
        this.latestJoin = data.getLong("latestJoin") != null ? data.getLong("latestJoin") : 0L;
        this.latestLeave = data.getLong("latestLeave") != null ? data.getLong("latestLeave") : 0L;
        this.flags = data.getString("flags") == null ? new char[0] : data.getString("flags").toCharArray();
        this.balance = data.getInt("balance");
        this.ignores = new ArrayList<>(Utils.stringToUUIDList(data.getString("ignores")));
        this.chatprefix = data.getString("chatprefix");
        this.chatChannel = data.getString("chatChannel");
        this.lastLocation = Optional.ofNullable(data.getString("location"))
                .map(Utils::stringToLocation)
                .orElse(null);

        this.backLocation = Optional.ofNullable(data.getString("backLocation"))
                .map(Utils::stringToLocation)
                .orElse(null);
        this.isBanned = MPlayerManager.isBanned(uuid);
        this.modzoneAccess = data.get("modzoneAccess");
        try {
            DB.getResults("SELECT * FROM `md_homes` WHERE `playerUUID` = ?", uuid.toString()).forEach(row -> homes.add(new Home(row)));
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void setLastLocation(Location lastLocation) {
        try {
            this.lastLocation = lastLocation;
            DB.executeUpdate("UPDATE `md_players` SET `location` = ? WHERE `uuid` = ?", Utils.locationToString(lastLocation), uuid.toString());
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void setBackLocation(Location backLocation) {
        try {
            this.backLocation = backLocation;
            DB.executeUpdate("UPDATE `md_players` SET `backLocation` = ? WHERE `uuid` = ?", Utils.locationToString(backLocation), uuid.toString());
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void setBalance(int newBalance, String json) {
        try {
            Database.createBalanceLogEntry(uuid, balance, newBalance, json);
            this.balance = newBalance;
            DB.executeUpdate("UPDATE `md_players` SET `balance` = ? WHERE `uuid` = ?", newBalance, uuid.toString());
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void addBalance(int amount, String json) {
        try {
            Database.createBalanceLogEntry(uuid, balance, balance + amount, json);
            this.balance += amount;
            DB.executeUpdate("UPDATE `md_players` SET `balance` = ? WHERE `uuid` = ?", balance + amount, uuid.toString());
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void removeBalance(int amount, String json) {
        try {
            Database.createBalanceLogEntry(uuid, balance, balance - amount, json);
            this.balance -= amount;
            DB.executeUpdate("UPDATE `md_players` SET `balance` = ? WHERE `uuid` = ?", balance, uuid.toString());
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean hasFlag(char needle) {
        if (this.flags == null) {
            return false;
        }

        for (char option : this.flags) {
            if (option == needle) {
                return true;
            }
        }
        return false;
    }

    public void setFlags(String flags) {
        if (flags == null || flags.isEmpty()) {
            this.flags = new char[0];
        } else {
            this.flags = flags.toCharArray();
        }
        DB.executeUpdateAsync("UPDATE `md_players` SET `flags` = ? WHERE `uuid` = ?", flags, uuid.toString());
    }

    public void setModzoneAccess(boolean modzoneAccess) {
        this.modzoneAccess = modzoneAccess;
        DB.executeUpdateAsync("UPDATE `md_players` SET `modzoneAccess` = ? WHERE `uuid` = ?", modzoneAccess, uuid.toString());
    }


    public void addIgnore(UUID playerUUID) {
        if (!ignores.contains(playerUUID)) {
            ignores.add(playerUUID);
            DB.executeUpdateAsync("UPDATE `md_players` SET `ignores` = ? WHERE `uuid` = ?", Utils.uuidListToString(ignores), uuid.toString());
        }
    }

    public void removeIgnore(UUID playerUUID) {
        if (ignores.contains(playerUUID)) {
            ignores.remove(playerUUID);
            DB.executeUpdateAsync("UPDATE `md_players` SET `ignores` = ? WHERE `uuid` = ?", Utils.uuidListToString(ignores), uuid.toString());
        }
    }

    public Home getHome(String name) {
        for (Home home : homes) {
            if (home.getName().equalsIgnoreCase(name)) {
                return home;
            }
        }
        return null;
    }

    public void addHome(Home home) {
        homes.add(home);
    }

    public void newHome(String name, Location location) {
        try {
            DB.executeUpdate("INSERT INTO `md_homes` (`playerUUID`, `homeName`, `location`) VALUES (?, ?, ?)", uuid.toString(), name, Utils.locationToString(location));
            homes.add(new Home(DB.getResults("SELECT * FROM `md_homes` WHERE `playerUUID` = ? AND `homeName` = ?", uuid.toString(), name).getFirst()));
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void removeHome(Home home) {
        homes.remove(home);
        DB.executeUpdateAsync("DELELTE FROM `md_homes` WHERE `playerUUID` = ? AND `homeName` = ?", uuid.toString(), home.getName());
    }

    public void setChatprefix(String chatprefix) {
        this.chatprefix = chatprefix;
        DB.executeUpdateAsync("UPDATE `md_players` SET `chatprefix` = ? WHERE `uuid` = ?", chatprefix, uuid.toString());
    }

    public void removeChatprefix() {
        this.chatprefix = null;
        DB.executeUpdateAsync("UPDATE `md_players` SET `chatprefix` = NULL WHERE `uuid` = ?", uuid.toString());
    }

    public void setLatestJoin(long latestJoin) {
        this.latestJoin = latestJoin;
        DB.executeUpdateAsync("UPDATE `md_players` SET `latestJoin` = ? WHERE `uuid` = ?", latestJoin, uuid.toString());
    }

    public void setLatestLeave(long latestLeave) {
        this.latestLeave = latestLeave;
        DB.executeUpdateAsync("UPDATE `md_players` SET `latestLeave` = ? WHERE `uuid` = ?", latestLeave, uuid.toString());
    }

    public void changeName(String name) {
        this.name = name;
        DB.executeUpdateAsync("UPDATE `md_players` SET `name` = ? WHERE `uuid` = ?", name, uuid.toString());
        try {
            DB.executeInsert("INSERT INTO `md_namechanges` (`playerUUID`, `playerName`, `dateTime`) VALUES (?, ?, ?)", uuid.toString(), name, Utils.getTimestamp());
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void setBanned(boolean banned) {
        this.isBanned = banned;
    }
}
