package org.kkaemok.reAbility.utils;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class InventoryUtils {
    private InventoryUtils() {}

    public static boolean hasAtLeast(Player player, Material type, int amount) {
        if (amount <= 0) return true;
        return player.getInventory().containsAtLeast(new ItemStack(type), amount);
    }

    public static boolean consume(Player player, Material type, int amount) {
        if (amount <= 0) return true;
        if (!hasAtLeast(player, type, amount)) return false;
        player.getInventory().removeItem(new ItemStack(type, amount));
        return true;
    }
}
