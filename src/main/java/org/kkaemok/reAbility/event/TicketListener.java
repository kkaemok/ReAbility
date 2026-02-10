package org.kkaemok.reAbility.event;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.kkaemok.reAbility.ReAbility;
import org.kkaemok.reAbility.ability.AbilityManager;
import org.kkaemok.reAbility.ability.RerollTicket;

public class TicketListener implements Listener {

    private final AbilityManager abilityManager;
    private final NamespacedKey ticketKey;

    public TicketListener(ReAbility plugin, AbilityManager abilityManager) {
        this.abilityManager = abilityManager;
        this.ticketKey = new NamespacedKey(plugin, "reroll_ticket_type");
    }

    @EventHandler
    public void onTicketUse(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (item == null || item.getType() != Material.PAPER) return;

        if (item.hasItemMeta()) {
            String ticketTypeName = item.getItemMeta().getPersistentDataContainer()
                    .get(ticketKey, PersistentDataType.STRING);

            if (ticketTypeName != null) {
                event.setCancelled(true);

                try {
                    RerollTicket ticket = RerollTicket.valueOf(ticketTypeName);

                    item.setAmount(item.getAmount() - 1);
                    abilityManager.useRerollTicket(player, ticket);

                } catch (IllegalArgumentException e) {
                    player.sendMessage("리롤권 데이터가 올바르지 않습니다.");
                }
            }
        }
    }
}
