package live.supeer.apied;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class VirtualInventory {
    private final List<Inventory> inventories;

    public VirtualInventory(List<Inventory> inventories) {
        this.inventories = inventories;
    }

    public boolean hasItems(ItemStack[] itemsToTake) {
        HashMap<ItemStack, Integer> itemsToTakeMap = stackToMap(itemsToTake);
        HashMap<ItemStack, Integer> inventoryMap = new HashMap<>();

        for (Inventory inventory : inventories) {
            inventoryMap.putAll(stackToMap(inventory.getContents()));
        }

        for (Map.Entry<ItemStack, Integer> entry : itemsToTakeMap.entrySet()) {
            if (inventoryMap.containsKey(entry.getKey())) {
                if (inventoryMap.get(entry.getKey()) < entry.getValue()) {
                    return false;
                }
            } else {
                return false;
            }
        }

        return true;
    }

    public boolean canTakeItems(ItemStack[] itemsToGive) {
        Map<Integer, StackWithAmount> lookaside = getAmountsMapping();
        for (ItemStack item : itemsToGive) {
            if (item == null) continue;
            int amountToAdd = item.getAmount();

            while (amountToAdd > 0) {
                int partialSpaceIndex = findSpace(item, lookaside, false);

                if (partialSpaceIndex == -1) {
                    int freeSpaceIndex = findSpace(item, lookaside, true);

                    if (freeSpaceIndex == -1) {
                        return false;
                    } else {
                        int toSet = amountToAdd;

                        if (amountToAdd > inventories.get(0).getMaxStackSize()) {
                            amountToAdd -= inventories.get(0).getMaxStackSize();
                            toSet = inventories.get(0).getMaxStackSize();
                        } else {
                            amountToAdd = 0;
                        }

                        lookaside.put(freeSpaceIndex, new StackWithAmount(toSet, item));
                    }
                } else {
                    StackWithAmount stackWithAmount = lookaside.get(partialSpaceIndex);
                    int partialAmount = stackWithAmount.getAmount();
                    int maxAmount = stackWithAmount.getStack().getMaxStackSize();

                    int toSet;
                    if (amountToAdd + partialAmount <= maxAmount) {
                        toSet = amountToAdd + partialAmount;
                        amountToAdd = 0;
                    } else {
                        toSet = maxAmount;
                        amountToAdd = amountToAdd + partialAmount - maxAmount;
                    }

                    lookaside.get(partialSpaceIndex).setAmount(toSet);
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

    private HashMap<ItemStack, Integer> stackToMap(ItemStack[] stacks) {
        HashMap<ItemStack, Integer> map = new HashMap<>();
        for (ItemStack stack : stacks) {
            if (stack != null) {
                map.merge(stack, stack.getAmount(), Integer::sum);
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