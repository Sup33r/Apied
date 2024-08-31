package live.supeer.apied;

import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class VirtualInventory {
    private final List<Inventory> inventories;

    public VirtualInventory(List<Inventory> inventories) {
        this.inventories = inventories;
    }

    public void sortInventory() {
        Map<String, ItemStack> consolidatedItems = new HashMap<>();

        // Consolidate all items in the inventories
        for (Inventory inventory : inventories) {
            for (ItemStack item : inventory.getContents()) {
                if (item != null && item.getType() != Material.AIR) {
                    String key = getItemKey(item);
                    if (consolidatedItems.containsKey(key)) {
                        ItemStack existingStack = consolidatedItems.get(key);
                        int newAmount = existingStack.getAmount() + item.getAmount();
                        if (newAmount <= item.getMaxStackSize()) {
                            existingStack.setAmount(newAmount);
                        } else {
                            existingStack.setAmount(item.getMaxStackSize());
                            item.setAmount(newAmount - item.getMaxStackSize());
                            consolidatedItems.put(key + "_overflow_" + UUID.randomUUID(), item);
                        }
                    } else {
                        consolidatedItems.put(key, item.clone());
                    }
                }
            }
        }

        // Clear the inventories
        for (Inventory inventory : inventories) {
            inventory.clear();
        }

        // Refill the inventories with sorted, consolidated items
        List<ItemStack> sortedItems = new ArrayList<>(consolidatedItems.values());
        sortedItems.sort(Comparator.comparing(ItemStack::getType));

        Iterator<ItemStack> iterator = sortedItems.iterator();
        for (Inventory inventory : inventories) {
            while (inventory.firstEmpty() != -1 && iterator.hasNext()) {
                inventory.addItem(iterator.next());
            }
        }
    }

    private String getItemKey(ItemStack item) {
        // Generate a unique key for each item type, considering item meta if necessary
        return item.getType().toString() + (item.hasItemMeta() ? item.getItemMeta().toString() : "");
    }


    public ItemStack[] getContents() {
        List<ItemStack> contents = new ArrayList<>();
        for (Inventory inventory : inventories) {
            contents.addAll(Arrays.asList(inventory.getContents()));
        }
        return contents.toArray(new ItemStack[0]);
    }

    public boolean hasItems(ItemStack[] isItemsToTake) {
        // Create a backup of the items to take (deep clone)
        ItemStack[] isBackup = getBackupItemStack(isItemsToTake);

        // Convert the required items and the inventory contents into maps
        HashMap<ItemStack, Integer> mItemsToTake = StackToMap(isBackup);
        HashMap<ItemStack, Integer> mInventory = new HashMap<>();

        // Combine the contents of all inventories into a single map
        for (Inventory inventory : inventories) {
            HashMap<ItemStack, Integer> inventoryMap = StackToMap(inventory.getContents());
            for (Map.Entry<ItemStack, Integer> entry : inventoryMap.entrySet()) {
                mInventory.put(entry.getKey(), mInventory.getOrDefault(entry.getKey(), 0) + entry.getValue());
            }
        }

        // Compare the required items map with the combined inventory map
        for (Map.Entry<ItemStack, Integer> entry : mItemsToTake.entrySet()) {
            if (mInventory.containsKey(entry.getKey())) {
                if (mInventory.get(entry.getKey()) < entry.getValue()) {
                    return false; // Not enough of this item
                }
            } else {
                return false; // Item not found
            }
        }

        return true; // All items are present in sufficient quantity
    }

    public static ItemStack[] getBackupItemStack(ItemStack[] isOriginal) {
        if(isOriginal == null)
            return null;
        ItemStack[] isBackup = new ItemStack[isOriginal.length];
        for(int i = 0; i < isOriginal.length; i++){
            if(isOriginal[i] != null) {
                isBackup[i] = getBackupSingleItemStack(isOriginal[i]);
            }
        }
        return isBackup;
    }

    public static ItemStack getBackupSingleItemStack(ItemStack isOriginal) {
        if(isOriginal == null)
            return isOriginal;
        return isOriginal.clone();
    }

    public static HashMap<ItemStack, Integer> StackToMap(ItemStack[] isStacks) {
        ItemStack[] isBackup = getBackupItemStack(isStacks);
        HashMap<ItemStack, Integer> mReturn = new HashMap<>();
        if(isBackup == null)
            return mReturn;
        int tempAmount;
        for (ItemStack itemStack : isBackup) {
            if (itemStack == null) continue;
            tempAmount = itemStack.getAmount();
            itemStack.setAmount(1);
            if (mReturn.containsKey(itemStack)) {
                tempAmount += mReturn.get(itemStack);
                mReturn.remove(itemStack);
            }
            mReturn.put(itemStack, tempAmount);
        }
        return mReturn;
    }

    public boolean canTakeItems(ItemStack[] itemsToGive) {
        // Create a lookaside map to simulate the inventory state without modifying the actual inventory
        Map<Integer, StackWithAmount> lookaside = getAmountsMapping();

        for (ItemStack item : itemsToGive) {
            if (item == null) continue;
            int amountToAdd = item.getAmount();

            while (amountToAdd > 0) {
                // First, try to find a partial stack that can accommodate the items
                int partialSpaceIndex = findSpace(item, lookaside, false);

                if (partialSpaceIndex == -1) {
                    // If no partial stack is available, find an empty slot
                    int freeSpaceIndex = findSpace(item, lookaside, true);

                    if (freeSpaceIndex == -1) {
                        // If no empty slot is available, return false
                        return false;
                    } else {
                        // Calculate how much can be added to the empty slot
                        int toSet = Math.min(amountToAdd, item.getMaxStackSize());
                        amountToAdd -= toSet;

                        // Simulate placing the items in the empty slot in the lookaside map
                        lookaside.put(freeSpaceIndex, new StackWithAmount(toSet, item.clone()));
                    }
                } else {
                    // If a partial stack was found, try to add to it
                    StackWithAmount stackWithAmount = lookaside.get(partialSpaceIndex);
                    int partialAmount = stackWithAmount.getAmount();
                    int maxAmount = stackWithAmount.getStack().getMaxStackSize();

                    // Calculate the new stack size
                    int toSet = Math.min(maxAmount, partialAmount + amountToAdd);
                    amountToAdd -= (toSet - partialAmount);

                    // Update the lookaside map with the new amount in the stack
                    stackWithAmount.setAmount(toSet);
                }
            }
        }

        return true;
    }


    private int findSpace(ItemStack item, Map<Integer, StackWithAmount> lookaside, boolean findEmpty) {
        int length = inventories.stream().mapToInt(Inventory::getSize).sum();
        List<ItemStack> stacks = new ArrayList<>();
        for (Inventory inventory : inventories) {
            stacks.addAll(Arrays.asList(inventory.getContents()));
        }

        if (item == null) {
            return -1;
        }

        for (int i = 0; i < stacks.size(); i++) {
            boolean contains = lookaside.containsKey(i);
            if (findEmpty && !contains) {
                return i;
            } else if (!findEmpty && contains) {
                StackWithAmount compareWith = lookaside.get(i);
                if (compareWith != null) {
                    ItemStack compareWithStack = compareWith.getStack();
                    if (compareWith.getAmount() < compareWithStack.getMaxStackSize() && compareWithStack.isSimilar(item)) {
                        return i;
                    }
                }
            }
        }

        return -1;
    }

    private Map<Integer, StackWithAmount> getAmountsMapping() {
        Map<Integer, StackWithAmount> map = new LinkedHashMap<>();
        int index = 0;
        for (Inventory inventory : inventories) {
            ItemStack[] stacks = inventory.getContents();
            for (ItemStack stack : stacks) {
                if (stack != null)
                    map.put(index++, new StackWithAmount(stack.getAmount(), stack));
            }
        }
        return map;
    }

    private static class StackWithAmount {
        private int amount;
        private ItemStack stack;

        private StackWithAmount(int amount, ItemStack stack) {
            this.amount = amount;
            this.stack = stack;
        }

        private int getAmount() {
            return amount;
        }

        private void setAmount(int amount) {
            this.amount = amount;
        }

        private ItemStack getStack() {
            return stack;
        }

        private void setStack(ItemStack stack) {
            this.stack = stack;
        }
    }
}