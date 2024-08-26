package live.supeer.apied;

import co.aikar.idb.BukkitDB;
import co.aikar.idb.DB;

import java.sql.SQLException;
import java.util.UUID;

public class Database {

    public static void initialize() {
        try {
            BukkitDB.createHikariDatabase(
                    Apied.getInstance(),
                    Apied.configuration.getSqlUsername(),
                    Apied.configuration.getSqlPassword(),
                    Apied.configuration.getSqlDatabase(),
                    Apied.configuration.getSqlHost()
                            + ":"
                            + Apied.configuration.getSqlPort());
            createTables();
        } catch (Exception e) {
            Apied.getInstance().getLogger().warning("Failed to initialize database, disabling plugin.\nError: " + e.getMessage());
            Apied.getInstance().getServer().getPluginManager().disablePlugin(Apied.getInstance());
        }
    }

//    public static void synchronize() {
//        try {
//            CityDatabase.initDBSync();
//        } catch (Exception exception) {
//            exception.printStackTrace();
//            plugin.getLogger().warning("Failed to synchronize database, disabling plugin.");
//            plugin.getServer().getPluginManager().disablePlugin(plugin);
//        }
//    }

    public static String sqlString(String string) {
        return string == null ? "NULL" : "'" + string.replace("\\", "\\\\").replace("'", "\\'") + "'";
    }

    public static void createTables() {
        try {

            DB.executeUpdate(
                    """
                              CREATE TABLE IF NOT EXISTS `md_players` (
                                `uuid` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
                                `name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
                                `firstJoin` bigint(30) DEFAULT NULL,
                                `latestJoin` bigint(30) DEFAULT NULL,
                                `latestLeave` bigint(30) DEFAULT NULL,
                                `flags` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
                                `balance` int(25) NOT NULL DEFAULT 0,
                                `ignores` text COLLATE utf8mb4_unicode_ci,
                                `chatprefix` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
                                `chatChannel` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
                                `location` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
                                `backLocation` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
                                PRIMARY KEY (`uuid`)
                              ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;""");
            DB.executeUpdate(
                    """
                              CREATE TABLE IF NOT EXISTS `md_balancelogs` (
                                `logId` int(11) NOT NULL AUTO_INCREMENT,
                                `playerUUID` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
                                `dateTime` bigint(30) DEFAULT NULL,
                                `balanceBefore` int(25) NOT NULL,
                                `balanceAfter` int(25) NOT NULL,
                                `jsonLog` text NOT NULL,
                                PRIMARY KEY (logId)
                              ) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;""");
            DB.executeUpdate(
                    """
                              CREATE TABLE IF NOT EXISTS `md_homes` (
                                `playerUUID` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
                                `homeName` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
                                `location` varchar(255) COLLATE utf8mb4_unicode_ci,
                                PRIMARY KEY (`playerUUID`, `homeName`)
                              ) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;""");
            DB.executeUpdate(
                    """
                              CREATE TABLE IF NOT EXISTS `md_bans` (
                                `playerUUID` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
                                `placerUUID` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
                                `placeTime` bigint(30) DEFAULT NULL,
                                `length` bigint(30) DEFAULT NULL,
                                `reason` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
                                PRIMARY KEY (`playerUUID`, `placeTime`)
                              ) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;""");
            DB.executeUpdate(
                    """
                              CREATE TABLE IF NOT EXISTS `md_chests` (
                                `id` int(11) NOT NULL AUTO_INCREMENT,
                                `location` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
                                `type` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
                                `dateTime` bigint(30) DEFAULT NULL,
                                `ownerUUID` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
                                `sharedPlayers` text COLLATE utf8mb4_unicode_ci,
                                `shops` text COLLATE utf8mb4_unicode_ci,
                                PRIMARY KEY (`id`)
                              ) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;""");
            DB.executeUpdate(
                    """
                              CREATE TABLE IF NOT EXISTS `md_archive` (
                                `id` int(11) NOT NULL AUTO_INCREMENT,
                                `dateTime` bigint(30) DEFAULT NULL,
                                `owner` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
                                `archiver` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
                                `items` text COLLATE utf8mb4_unicode_ci,
                                PRIMARY KEY (`id`)
                              ) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;""");
            DB.executeUpdate(
                    """
                              CREATE TABLE IF NOT EXISTS `md_shops` (
                                `id` int(11) NOT NULL AUTO_INCREMENT,
                                `ownerUUID` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
                                `signLocation` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
                                `chestLocations` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
                                `type` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
                                `signSide` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
                                `maxPlayerUses` smallint(6) DEFAULT NULL,
                                `maxDailyUses` smallint(6) DEFAULT NULL,
                                `maxPlayerDailyUses` smallint(6) DEFAULT NULL,
                                `maxUses` smallint(6) DEFAULT NULL,
                                `dateTime` bigint(30) DEFAULT NULL,
                                `balance` bigint(30) DEFAULT NULL,
                                `items` text COLLATE utf8mb4_unicode_ci,
                                `removed` tinyint(1) DEFAULT '0',
                                PRIMARY KEY (`id`)
                              ) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;""");
            DB.executeUpdate(
                    """
                              CREATE TABLE IF NOT EXISTS `md_shoptransactions` (
                                `id` int(11) NOT NULL AUTO_INCREMENT,
                                `shopId` int(11) NOT NULL,
                                `uuid` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
                                `dateTime` bigint(30) DEFAULT NULL,
                                PRIMARY KEY (`id`)
                              ) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;""");
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public static void createBalanceLogEntry(UUID uuid, int balanceBefore, int balanceAfter, String reason) {
        try {
            DB.executeUpdate("INSERT INTO md_balancelogs (playerUUID, dateTime, balanceBefore, balanceAfter, jsonLog) VALUES (?, ?, ?, ?, ?)", uuid.toString(), Utils.getTimestamp(), balanceBefore, balanceAfter, Database.sqlString(reason));
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

}
