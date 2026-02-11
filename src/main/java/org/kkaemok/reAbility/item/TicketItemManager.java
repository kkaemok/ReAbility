package org.kkaemok.reAbility.item;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.kkaemok.reAbility.ReAbility;
import org.kkaemok.reAbility.ability.RerollTicket;

import java.util.List;

public class TicketItemManager {
    private final NamespacedKey ticketKey;
    private final NamespacedKey keepDaysKey;

    public TicketItemManager(ReAbility plugin) {
        this.ticketKey = new NamespacedKey(plugin, "reroll_ticket_type");
        this.keepDaysKey = new NamespacedKey(plugin, "ability_keep_days");
    }

    public ItemStack createTicket(RerollTicket type) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(type.getName(), NamedTextColor.YELLOW));
            meta.lore(List.of(Component.text("우클릭하여 능력을 뽑습니다.", NamedTextColor.GRAY)));
            meta.getPersistentDataContainer().set(ticketKey, PersistentDataType.STRING, type.name());
            item.setItemMeta(meta);
        }
        return item;
    }

    public ItemStack createKeepTicket(int days) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("능력 유지권 (" + days + "일)", NamedTextColor.YELLOW));
            meta.lore(List.of(Component.text("우클릭 시 현재 능력 기간을 연장합니다.", NamedTextColor.GRAY)));
            meta.getPersistentDataContainer().set(keepDaysKey, PersistentDataType.INTEGER, days);
            item.setItemMeta(meta);
        }
        return item;
    }
}
