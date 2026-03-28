package org.kkaemok.reAbility.item;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.kkaemok.reAbility.ReAbility;
import org.kkaemok.reAbility.ability.RerollTicket;

import java.util.List;

public class TicketItemManager {
    private final NamespacedKey ticketKey;
    private final NamespacedKey keepDaysKey;
    private final NamespacedKey infiniteFireworkKey;

    public TicketItemManager(ReAbility plugin) {
        this.ticketKey = new NamespacedKey(plugin, "reroll_ticket_type");
        this.keepDaysKey = new NamespacedKey(plugin, "ability_keep_days");
        this.infiniteFireworkKey = new NamespacedKey(plugin, "infinite_firework");
    }

    public ItemStack createTicket(RerollTicket type) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(type.getName(), NamedTextColor.YELLOW));
            meta.lore(List.of(Component.text("우클릭하면 능력을 뽑습니다.", NamedTextColor.GRAY)));
            meta.getPersistentDataContainer().set(ticketKey, PersistentDataType.STRING, type.name());
            item.setItemMeta(meta);
        }
        return item;
    }

    public ItemStack createKeepTicket(int days) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("능력 유지권(" + days + "일)", NamedTextColor.YELLOW));
            meta.lore(List.of(Component.text("우클릭하면 현재 능력 기간을 연장합니다.", NamedTextColor.GRAY)));
            meta.getPersistentDataContainer().set(keepDaysKey, PersistentDataType.INTEGER, days);
            item.setItemMeta(meta);
        }
        return item;
    }

    public ItemStack createInfiniteFirework() {
        ItemStack item = new ItemStack(Material.FIREWORK_ROCKET);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("무한 폭죽", NamedTextColor.AQUA));
            meta.lore(List.of(Component.text("사용 시 폭죽이 소모되지 않습니다.", NamedTextColor.GRAY)));
            meta.getPersistentDataContainer().set(infiniteFireworkKey, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    public boolean isInfiniteFirework(ItemStack item) {
        if (item == null || item.getType() != Material.FIREWORK_ROCKET || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        return meta.getPersistentDataContainer().has(infiniteFireworkKey, PersistentDataType.BYTE);
    }
}
