package org.kkaemok.reAbility.ability.list;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.kkaemok.reAbility.ReAbility;
import org.kkaemok.reAbility.ability.AbilityBase;
import org.kkaemok.reAbility.ability.AbilityGrade;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Fighter extends AbilityBase {
    private final ReAbility plugin;
    private final Map<UUID, Long> skillCooldown = new HashMap<>();

    public Fighter(ReAbility plugin) { this.plugin = plugin; }

    @Override public String getName() { return "FIGHTER"; }
    @Override public String getDisplayName() { return "파이터"; }
    @Override public AbilityGrade getGrade() { return AbilityGrade.B; }

    @Override
    public String[] getDescription() {
        return new String[]{
                "24시간 힘 2 효과 획득, HP는 한 줄로 고정.",
                "입힌 피해의 40%만큼 회복.",
                "스킬 {난 햇빛을 피해 도망치는 것이다.}:",
                "다이아 100개 소모, 즉시 HP 전부 회복 + 15초 스피드 50"
        };
    }

    @Override
    public void onActivate(Player player) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, PotionEffect.INFINITE_DURATION, 1, false, false));

        AttributeInstance maxHealthAttr = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealthAttr != null) {
            maxHealthAttr.setBaseValue(20.0);
        }
    }

    @Override
    public void onDeactivate(Player player) {
        player.removePotionEffect(PotionEffectType.STRENGTH);

        AttributeInstance maxHealthAttr = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealthAttr != null) {
            maxHealthAttr.setBaseValue(40.0);
        }
    }

    @EventHandler
    public void onAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker) || !isHasAbility(attacker)) return;

        double healAmount = event.getFinalDamage() * 0.4;
        AttributeInstance maxHealthAttr = attacker.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealthAttr != null) {
            double newHealth = Math.min(maxHealthAttr.getValue(), attacker.getHealth() + healAmount);
            attacker.setHealth(newHealth);
        }
    }

    @Override
    public void onSneakSkill(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType() != Material.DIAMOND || item.getAmount() < 100) return;

        long now = System.currentTimeMillis();
        if (now < skillCooldown.getOrDefault(player.getUniqueId(), 0L)) {
            player.sendMessage(Component.text("쿨타임 중입니다.", NamedTextColor.RED));
            return;
        }

        item.setAmount(item.getAmount() - 100);
        skillCooldown.put(player.getUniqueId(), now + 10000);

        AttributeInstance maxHealthAttr = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealthAttr != null) {
            player.setHealth(maxHealthAttr.getValue());
        }

        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 300, 49));
        player.sendMessage(Component.text("[!] 스킬 {난 햇빛을 피해 도망치는 것이다.} 발동!", NamedTextColor.YELLOW));
    }

    private boolean isHasAbility(Player player) {
        return getName().equals(plugin.getAbilityManager().getPlayerData(player.getUniqueId()).getAbilityName());
    }
}
