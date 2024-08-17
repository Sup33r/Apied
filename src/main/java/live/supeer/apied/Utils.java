package live.supeer.apied;

import org.bukkit.Bukkit;
import org.bukkit.Location;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Utils {

    public static String formatLocation(Location location) {
        return "(["
                + location.getWorld().getName()
                + "]"
                + location.getBlockX()
                + ", "
                + location.getBlockY()
                + ", "
                + location.getBlockZ()
                + ")";
    }

    public static String locationToString(Location location) {
        if (location == null) {
            return null;
        }

        return location.getWorld().getName()
                + " "
                + location.getX()
                + " "
                + location.getY()
                + " "
                + location.getZ()
                + " "
                + location.getYaw()
                + " "
                + location.getPitch();
    }

    public static Location stringToLocation(String string) {
        if (string == null || string.isEmpty()) {
            return null;
        }

        String[] split = string.split(" ");
        return new Location(
                Bukkit.getWorld(split[0]),
                Double.parseDouble(split[1]),
                Double.parseDouble(split[2]),
                Double.parseDouble(split[3]),
                Float.parseFloat(split[4]),
                Float.parseFloat(split[5]));
    }

    public static List<UUID> stringToUUIDList(String playerString) {
        List<UUID> players = new ArrayList<>();
        if (playerString == null || playerString.isEmpty()) {
            return players;
        }
        String[] playerArray = playerString.split(",");
        for (String playerUUID : playerArray) {
            players.add(UUID.fromString(playerUUID));
        }
        return players;
    }


    public static String uuidListToString(List<UUID> players) {
        StringBuilder playerString = new StringBuilder();
        for (UUID player : players) {
            playerString.append(player.toString()).append(",");
        }
        return playerString.toString();
    }

    public static long getTimestamp() {
        return System.currentTimeMillis() / 1000L;
    }
}
