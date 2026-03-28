package org.kkaemok.reAbility.event;

import com.destroystokyo.paper.event.player.PlayerElytraBoostEvent;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.kkaemok.reAbility.ReAbility;
import org.kkaemok.reAbility.item.TicketItemManager;

public class InfiniteFireworkListener implements Listener {
    private final ReAbility plugin;
    private final TicketItemManager itemManager;

    public InfiniteFireworkListener(ReAbility plugin, TicketItemManager itemManager) {
        this.plugin = plugin;
        this.itemManager = itemManager;
    }

    @EventHandler(ignoreCancelled = true)
    public void onElytraBoost(PlayerElytraBoostEvent event) {
        if (itemManager.isInfiniteFirework(event.getItemStack())) {
            event.setShouldConsume(false);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onGroundUse(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack item = event.getItem();
        if (!itemManager.isInfiniteFirework(item)) return;

        Player player = event.getPlayer();
        EquipmentSlot hand = event.getHand();
        if (hand == null) return;

        int before = item.getAmount();
        int slot = hand == EquipmentSlot.HAND ? player.getInventory().getHeldItemSlot() : 40;
        ItemStack snapshot = item.clone();

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            ItemStack current = player.getInventory().getItem(slot);
            if (current == null || current.getType() == Material.AIR) {
                snapshot.setAmount(before);
                player.getInventory().setItem(slot, snapshot);
                return;
            }
            if (!itemManager.isInfiniteFirework(current)) return;
            if (current.getAmount() < before) {
                current.setAmount(before);
                player.getInventory().setItem(slot, current);
            }
        });
    }
}
