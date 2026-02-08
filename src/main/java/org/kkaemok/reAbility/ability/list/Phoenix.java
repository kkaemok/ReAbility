package org.kkaemok.reAbility.ability.list;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.kkaemok.reAbility.ReAbility;
import org.kkaemok.reAbility.ability.AbilityBase;
import org.kkaemok.reAbility.ability.AbilityGrade;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Phoenix extends AbilityBase {
    private final ReAbility plugin;
    private final Map<UUID, Long> skillCooldown = new HashMap<>();
    private final Map<UUID, Long> invincibilityTime = new HashMap<>();

    public Phoenix(ReAbility plugin) { this.plugin = plugin; }

    @Override public String getName() { return "PHOENIX"; }
    @Override public String getDisplayName() { return "불사조"; }
    @Override public AbilityGrade getGrade() { return AbilityGrade.B; }

    @Override
    public String[] getDescription() {
        return new String[]{
                "§e[패시브] §f불사의 토템 20개를 획득하며, HP가 1칸 이하가 될 시 30초간 죽음을 면합니다.",
                "§6[스킬: 죽음 회피] §f다이아몬드 50개를 들고 웅크릴 시",
                "§f즉시 HP 5칸 회복, 재생2(10초), 저항2(1분) 획득",
                "§c[쿨타임] §f1분 30초"
        };
    }

    @Override
    public void onActivate(Player player) {
        // 토템 20개 지급
        player.getInventory().addItem(new ItemStack(Material.TOTEM_OF_UNDYING, 20));
        player.sendMessage(Component.text("[!] 불사조의 가호로 토템 20개를 획득했습니다.", NamedTextColor.GOLD));
    }

    @EventHandler
    public void onFatalDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || !isHasAbility(player)) return;

        // HP가 2(1칸) 이하로 내려갈 때 30초간 죽음 방지 (재사용 대기시간은 기획에 없으므로 1회성 혹은 로직 필요)
        if (player.getHealth() - event.getFinalDamage() <= 2.0) {
            if (!invincibilityTime.containsKey(player.getUniqueId())) {
                event.setDamage(0);
                player.setHealth(2.0);
                invincibilityTime.put(player.getUniqueId(), System.currentTimeMillis() + 30000);
                player.sendMessage(Component.text("[!] 30초 동안 죽음을 면합니다!", NamedTextColor.RED));
            } else if (invincibilityTime.get(player.getUniqueId()) > System.currentTimeMillis()) {
                event.setCancelled(true);
            }
        }
    }

    @Override
    public void onSneakSkill(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType() != Material.DIAMOND || item.getAmount() < 50) return;

        long now = System.currentTimeMillis();
        if (now < skillCooldown.getOrDefault(player.getUniqueId(), 0L)) {
            player.sendMessage(Component.text("쿨타임 중입니다.", NamedTextColor.RED));
            return;
        }

        // 소모 및 발동
        item.setAmount(item.getAmount() - 50);
        skillCooldown.put(player.getUniqueId(), now + 90000); // 1분 30초

        double maxHealth = player.getAttribute(Attribute.MAX_HEALTH).getValue();
        player.setHealth(Math.min(maxHealth, player.getHealth() + 10.0)); // 5칸(10) 회복
        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 200, 1)); // 10초
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 1200, 1)); // 1분

        player.playSound(player.getLocation(), Sound.ITEM_TOTEM_USE, 1.0f, 1.0f);
        player.sendMessage(Component.text("[!] 스킬 {죽음 회피} 발동!", NamedTextColor.GOLD));
    }

    private boolean isHasAbility(Player player) {
        return getName().equals(plugin.getAbilityManager().getPlayerData(player.getUniqueId()).getAbilityName());
    }
}