package live.supeer.apied;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import org.yaml.snakeyaml.external.biz.base64Coder.Base64Coder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.NumberFormat;
import java.util.*;

public class Utils {

    public static String formattedMoney(Integer money) {
        NumberFormat formatter = NumberFormat.getInstance(Locale.US);
        formatter.setGroupingUsed(true);
        return formatter.format(money).replace(",", " ");
    }

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

    public static List<Location> stringToLocationList(String locationString) {
        List<Location> locations = new ArrayList<>();
        if (locationString == null || locationString.isEmpty()) {
            return locations;
        }
        String[] locationArray = locationString.split(",");
        for (String location : locationArray) {
            locations.add(stringToLocation(location));
        }
        return locations;
    }


    public static String locationListToString(List<Location> locations) {
        StringBuilder locationString = new StringBuilder();
        for (Location location : locations) {
            locationString.append(locationToString(location)).append(",");
        }
        return locationString.toString();
    }

    public static List<Integer> stringToIntegerList(String integerString) {
        List<Integer> integers = new ArrayList<>();
        if (integerString == null || integerString.isEmpty()) {
            return integers;
        }

        // Trim any leading or trailing commas
        integerString = integerString.trim();
        if (integerString.startsWith(",")) {
            integerString = integerString.substring(1);
        }
        if (integerString.endsWith(",")) {
            integerString = integerString.substring(0, integerString.length() - 1);
        }

        String[] integerArray = integerString.split(",");
        for (String integer : integerArray) {
            try {
                integers.add(Integer.parseInt(integer.trim()));
            } catch (NumberFormatException e) {
                throw new RuntimeException(e);
            }
        }
        return integers;
    }

    public static String integerListToString(List<Integer> integers) {
        StringBuilder integerString = new StringBuilder();
        for (Integer integer : integers) {
            integerString.append(integer).append(",");
        }
        return integerString.toString();
    }

    public static long getTimestamp() {
        return System.currentTimeMillis() / 1000L;
    }

    public static String toBase64(Inventory inventory) throws IllegalStateException {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream);

            // Write the size of the inventory
            dataOutput.writeInt(inventory.getSize());

            // Save every element in the list
            for (int i = 0; i < inventory.getSize(); i++) {
                dataOutput.writeObject(inventory.getItem(i));
            }

            // Serialize that array
            dataOutput.close();
            return Base64Coder.encodeLines(outputStream.toByteArray());
        } catch (Exception e) {
            throw new IllegalStateException("Unable to save item stacks.", e);
        }
    }

    public static Inventory fromBase64(String data) throws IOException {
        try {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64Coder.decodeLines(data));
            BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream);
            Inventory inventory = Bukkit.getServer().createInventory(null, dataInput.readInt());

            // Read the serialized inventory
            for (int i = 0; i < inventory.getSize(); i++) {
                inventory.setItem(i, (ItemStack) dataInput.readObject());
            }

            dataInput.close();
            return inventory;
        } catch (ClassNotFoundException e) {
            throw new IOException("Unable to decode class type.", e);
        }
    }

    public static ItemStack[] itemStackArrayFromBase64(String data) throws IOException {
        try {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64Coder.decodeLines(data));
            BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream);

            // Initialize an empty array with the original size
            int size = dataInput.readInt();
            ItemStack[] items = new ItemStack[size];

            // Read each item and place it in the correct slot
            for (int i = 0; i < size; i++) {
                int index = dataInput.readInt(); // Read the index
                items[index] = (ItemStack) dataInput.readObject(); // Place the item in the correct slot
            }

            dataInput.close();
            return items;
        } catch (ClassNotFoundException e) {
            throw new IOException("Unable to decode class type.", e);
        }
    }


    public static String itemStackArrayToBase64(ItemStack[] items) throws IllegalStateException {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream);

            // Count and write the number of non-null items
            int nonNullItemCount = 0;
            for (ItemStack item : items) {
                if (item != null) {
                    nonNullItemCount++;
                }
            }
            dataOutput.writeInt(nonNullItemCount);

            // Write only non-null items to the output stream
            for (ItemStack item : items) {
                if (item != null) {
                    dataOutput.writeInt(Arrays.asList(items).indexOf(item)); // Store the index
                    dataOutput.writeObject(item);
                }
            }

            dataOutput.close();
            return Base64Coder.encodeLines(outputStream.toByteArray());
        } catch (Exception e) {
            throw new IllegalStateException("Unable to save item stacks.", e);
        }
    }

}
