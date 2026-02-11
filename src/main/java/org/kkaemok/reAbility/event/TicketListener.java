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
import org.kkaemok.reAbility.data.PlayerData;

public class TicketListener implements Listener {

    private final AbilityManager abilityManager;
    private final NamespacedKey ticketKey;
    private final NamespacedKey keepDaysKey;

    public TicketListener(ReAbility plugin, AbilityManager abilityManager) {
        this.abilityManager = abilityManager;
        this.ticketKey = new NamespacedKey(plugin, "reroll_ticket_type");
        this.keepDaysKey = new NamespacedKey(plugin, "ability_keep_days");
    }

    @EventHandler
    public void onTicketUse(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (item == null || item.getType() != Material.PAPER) return;
        if (!item.hasItemMeta()) return;

        var meta = item.getItemMeta();
        var pdc = meta.getPersistentDataContainer();
        String ticketTypeName = pdc.get(ticketKey, PersistentDataType.STRING);
        Integer keepDays = pdc.get(keepDaysKey, PersistentDataType.INTEGER);

        if (ticketTypeName == null && keepDays == null) return;
        event.setCancelled(true);

        if (ticketTypeName != null) {
            try {
                RerollTicket ticket = RerollTicket.valueOf(ticketTypeName);

                item.setAmount(item.getAmount() - 1);
                abilityManager.useRerollTicket(player, ticket);

            } catch (IllegalArgumentException e) {
                player.sendMessage("리롤권 타입이 올바르지 않습니다.");
            }
            return;
        }

        if (keepDays != null) {
            if (keepDays <= 0) {
                player.sendMessage("유지 일수가 올바르지 않습니다.");
                return;
            }

            PlayerData data = abilityManager.getPlayerData(player.getUniqueId());
            if (data.getAbilityName() == null || data.isExpired()) {
                player.sendMessage("현재 유지할 수 있는 능력이 없습니다.");
                return;
            }

            long addMillis = keepDays * 24L * 60 * 60 * 1000;
            data.setExpiryTime(data.getExpiryTime() + addMillis);
            abilityManager.savePlayerData(player.getUniqueId());

            item.setAmount(item.getAmount() - 1);
            player.sendMessage("능력 유지 기간을 " + keepDays + "일 연장했습니다.");
        }
    }
}
