package org.kkaemok.reAbility.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.kkaemok.reAbility.ReAbility;
import org.kkaemok.reAbility.ability.AbilityManager;
import org.kkaemok.reAbility.ability.list.NecromancerSkill;
import org.kkaemok.reAbility.data.PlayerData;
import org.kkaemok.reAbility.utils.SkillParticles;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SoulCommand implements CommandExecutor, TabCompleter, Listener {
    private static final int SHOP_SIZE = 27;
    private static final String SHOP_TITLE = "영혼 상점";
    private static final List<NecromancerSkill> SHOP_ORDER = List.of(
            NecromancerSkill.SOUL_HARVEST,
            NecromancerSkill.DEATH_KNIGHT,
            NecromancerSkill.DEATH_INSTINCT,
            NecromancerSkill.SCULK_SOUL,
            NecromancerSkill.DEATH_FLOWER,
            NecromancerSkill.END_OF_WORLD
    );
    private static final int[] SHOP_SLOTS = {10, 11, 12, 14, 15, 16};

    private final ReAbility plugin;
    private final AbilityManager abilityManager;

    public SoulCommand(ReAbility plugin, AbilityManager abilityManager) {
        this.plugin = plugin;
        this.abilityManager = abilityManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
                             @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("콘솔에서는 사용할 수 없습니다.");
            return true;
        }
        if (!isNecromancer(player)) {
            player.sendMessage("네크로맨서 능력일 때만 사용할 수 있습니다.");
            return true;
        }

        PlayerData data = abilityManager.getPlayerData(player.getUniqueId());
        int expired = data.purgeExpiredSouls();
        if (expired > 0) {
            abilityManager.savePlayerData(player.getUniqueId());
        }

        if (args.length == 0) {
            sendStatus(player, data);
            player.sendMessage("사용법: /영혼 상점");
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "상점" -> openShop(player, data);
            case "상태" -> sendStatus(player, data);
            default -> player.sendMessage("사용법: /영혼 <상점|상태>");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (!(sender instanceof Player)) return List.of();
        if (args.length != 1) return List.of();
        String token = args[0];
        List<String> matches = new ArrayList<>();
        for (String candidate : List.of("상점", "상태")) {
            if (candidate.startsWith(token)) {
                matches.add(candidate);
            }
        }
        return matches;
    }

    @EventHandler(ignoreCancelled = true)
    public void onShopClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof SoulShopHolder)) return;
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!isNecromancer(player)) return;

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) return;

        NecromancerSkill skill = skillBySlot(event.getRawSlot());
        if (skill == null) return;

        PlayerData data = abilityManager.getPlayerData(player.getUniqueId());
        int availableSouls = data.getAvailableSouls();
        if (availableSouls <= 0) {
            player.sendMessage("투자할 영혼이 없습니다.");
            return;
        }

        int current = data.getSoulInvestment(skill.getKey());
        int required = skill.getUnlockCost();
        if (current >= required) {
            player.sendMessage(skill.getDisplayName() + "은(는) 이미 해금되었습니다.");
            return;
        }

        int amount;
        ClickType click = event.getClick();
        if (click.isShiftClick()) {
            amount = Math.min(availableSouls, required - current);
        } else if (click.isRightClick()) {
            amount = Math.min(10, required - current);
        } else {
            amount = 1;
        }

        amount = Math.min(amount, availableSouls);
        if (amount <= 0) {
            player.sendMessage("더 이상 투자할 수 없습니다.");
            return;
        }

        int consumed = data.consumeSouls(amount);
        if (consumed <= 0) {
            player.sendMessage("소비 가능한 영혼이 없습니다.");
            return;
        }

        int before = current;
        int after = data.addSoulInvestment(skill.getKey(), consumed);
        abilityManager.savePlayerData(player.getUniqueId());

        player.sendMessage(skill.getDisplayName() + "에 영혼 " + consumed + "개를 투자했습니다. (" + after + "/" + required + ")");
        if (before < required && after >= required) {
            SkillParticles.splusUnlock(player, required);
            player.sendMessage(Component.text("[해금] " + skill.getDisplayName(), NamedTextColor.LIGHT_PURPLE));
        }

        openShop(player, data);
    }

    @EventHandler(ignoreCancelled = true)
    public void onShopDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof SoulShopHolder) {
            event.setCancelled(true);
        }
    }

    private void sendStatus(Player player, PlayerData data) {
        player.sendMessage("============ 영혼 상태 ============");
        player.sendMessage("보유 영혼: " + data.getAvailableSouls());
        for (NecromancerSkill skill : SHOP_ORDER) {
            int invested = data.getSoulInvestment(skill.getKey());
            int req = skill.getUnlockCost();
            String status = invested >= req ? "해금" : (invested + "/" + req);
            player.sendMessage("- " + skill.getDisplayName() + ": " + status);
        }
        player.sendMessage("================================");
    }

    private void openShop(Player player, PlayerData data) {
        SoulShopHolder holder = new SoulShopHolder();
        Inventory inv = Bukkit.createInventory(holder, SHOP_SIZE, Component.text(SHOP_TITLE, NamedTextColor.DARK_PURPLE));
        holder.setInventory(inv);

        for (int i = 0; i < SHOP_ORDER.size() && i < SHOP_SLOTS.length; i++) {
            NecromancerSkill skill = SHOP_ORDER.get(i);
            inv.setItem(SHOP_SLOTS[i], createSkillItem(data, skill));
        }
        player.openInventory(inv);
    }

    private ItemStack createSkillItem(PlayerData data, NecromancerSkill skill) {
        ItemStack item = new ItemStack(skill.getIcon());
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        int invested = data.getSoulInvestment(skill.getKey());
        int required = skill.getUnlockCost();
        boolean unlocked = invested >= required;

        meta.displayName(Component.text(skill.getDisplayName(), unlocked ? NamedTextColor.LIGHT_PURPLE : NamedTextColor.GRAY));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("필요 영혼: " + required, NamedTextColor.GOLD));
        lore.add(Component.text("투자 영혼: " + invested, NamedTextColor.AQUA));
        lore.add(Component.text("상태: " + (unlocked ? "해금됨" : "잠김"), unlocked ? NamedTextColor.GREEN : NamedTextColor.RED));
        lore.add(Component.text("좌클릭: 1 투자 / 우클릭: 10 투자 / 쉬프트: 최대 투자", NamedTextColor.DARK_GRAY));
        meta.lore(lore);

        item.setItemMeta(meta);
        return item;
    }

    private NecromancerSkill skillBySlot(int rawSlot) {
        for (int i = 0; i < SHOP_SLOTS.length && i < SHOP_ORDER.size(); i++) {
            if (SHOP_SLOTS[i] == rawSlot) {
                return SHOP_ORDER.get(i);
            }
        }
        return null;
    }

    private boolean isNecromancer(Player player) {
        PlayerData data = abilityManager.getPlayerData(player.getUniqueId());
        return "NECROMANCER".equals(data.getAbilityName());
    }

    private static class SoulShopHolder implements InventoryHolder {
        private Inventory inventory;

        private void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        @NotNull
        public Inventory getInventory() {
            return inventory;
        }
    }
}
