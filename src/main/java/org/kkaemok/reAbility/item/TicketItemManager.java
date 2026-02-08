package org.kkaemok.reAbility.item;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.kkaemok.reAbility.ReAbility;
import org.kkaemok.reAbility.ability.RerollTicket;

import java.util.Arrays;

public class TicketItemManager {
    private final ReAbility plugin;
    private final NamespacedKey ticketKey;

    public TicketItemManager(ReAbility plugin) {
        this.plugin = plugin;
        this.ticketKey = new NamespacedKey(plugin, "reroll_ticket_type");
    }

    // 리롤권 종이 아이템 생성
    public ItemStack createTicket(RerollTicket type) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§e" + type.getName());
            meta.setLore(Arrays.asList("§7우클릭하여 능력을 뽑습니다."));
            // 아이템에 어떤 티켓인지 데이터 저장
            meta.getPersistentDataContainer().set(ticketKey, PersistentDataType.STRING, type.name());
            item.setItemMeta(meta);
        }
        return item;
    }
}