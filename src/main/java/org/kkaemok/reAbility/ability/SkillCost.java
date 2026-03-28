package org.kkaemok.reAbility.ability;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.kkaemok.reAbility.utils.InventoryUtils;

public class SkillCost {
    private final Material item;
    private final int amount;
    private final Material extraItem;
    private final int extraAmount;

    public SkillCost(Material item, int amount, Material extraItem, int extraAmount) {
        this.item = item == null ? Material.AIR : item;
        this.amount = amount;
        this.extraItem = extraItem == null ? Material.AIR : extraItem;
        this.extraAmount = extraAmount;
    }

    public Material getItem() { return item; }
    public int getAmount() { return amount; }
    public Material getExtraItem() { return extraItem; }
    public int getExtraAmount() { return extraAmount; }

    public boolean matchesHand(ItemStack stack) {
        if (item == Material.AIR) return true;
        if (stack == null || stack.getType() != item) return false;
        int required = Math.max(1, amount);
        return stack.getAmount() >= required;
    }

    public boolean consumeFromHand(Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (!matchesHand(hand)) return false;
        if (!hasExtra(player)) return false;

        if (item != Material.AIR && amount > 0) {
            hand.setAmount(hand.getAmount() - amount);
        }
        if (extraItem != Material.AIR && extraAmount > 0) {
            InventoryUtils.consume(player, extraItem, extraAmount);
        }
        return true;
    }

    public boolean consumeFromInventory(Player player) {
        if (item != Material.AIR && amount > 0 && !InventoryUtils.hasAtLeast(player, item, amount)) {
            return false;
        }
        if (!hasExtra(player)) return false;

        if (item != Material.AIR && amount > 0) {
            InventoryUtils.consume(player, item, amount);
        }
        if (extraItem != Material.AIR && extraAmount > 0) {
            InventoryUtils.consume(player, extraItem, extraAmount);
        }
        return true;
    }

    private boolean hasExtra(Player player) {
        if (extraItem == Material.AIR || extraAmount <= 0) return true;
        return InventoryUtils.hasAtLeast(player, extraItem, extraAmount);
    }
}
