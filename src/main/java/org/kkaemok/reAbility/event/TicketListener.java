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

        // 우클릭 체크 (공기나 블록 우클릭)
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (item == null || item.getType() != Material.PAPER) return;

        // 아이템의 NBT 데이터에서 리롤권 종류 확인
        if (item.hasItemMeta()) {
            String ticketTypeName = item.getItemMeta().getPersistentDataContainer().get(ticketKey, PersistentDataType.STRING);

            if (ticketTypeName != null) {
                event.setCancelled(true); // 종이 설치 방지

                try {
                    RerollTicket ticket = RerollTicket.valueOf(ticketTypeName);

                    // 아이템 1개 소모
                    item.setAmount(item.getAmount() - 1);

                    // 능력 리롤 실행 (등급 결정 -> 능력 배정 -> 시간 설정까지 한 번에!)
                    abilityManager.useRerollTicket(player, ticket);

                } catch (IllegalArgumentException e) {
                    player.sendMessage("§c잘못된 리롤권 데이터입니다.");
                }
            }
        }
    }
}