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
import org.kkaemok.reAbility.ability.SkillCost;
import org.kkaemok.reAbility.utils.SkillParticles;

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
            double baseHealth = plugin.getAbilityConfigManager()
                    .getDouble(getName(), "stats.max-health", 20.0);
            maxHealthAttr.setBaseValue(baseHealth);
        }
    }

    @Override
    public void onDeactivate(Player player) {
        player.removePotionEffect(PotionEffectType.STRENGTH);
        player.removePotionEffect(PotionEffectType.SPEED);

        AttributeInstance maxHealthAttr = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealthAttr != null) {
            double restoreHealth = plugin.getAbilityConfigManager()
                    .getDouble(getName(), "stats.restore-max-health", 40.0);
            maxHealthAttr.setBaseValue(restoreHealth);
        }
    }

    @EventHandler
    public void onAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker) || !isHasAbility(attacker)) return;

        double healRate = plugin.getAbilityConfigManager()
                .getDouble(getName(), "stats.heal-percent", 0.4);
        double healAmount = event.getFinalDamage() * healRate;
        AttributeInstance maxHealthAttr = attacker.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealthAttr != null) {
            double newHealth = Math.min(maxHealthAttr.getValue(), attacker.getHealth() + healAmount);
            attacker.setHealth(newHealth);
        }
    }

    @Override
    public void onSneakSkill(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        SkillCost cost = plugin.getAbilityConfigManager()
                .getSkillCost(getName(), "escape", Material.DIAMOND, 100);
        if (item.getType() != cost.getItem()) return;

        long now = System.currentTimeMillis();
        if (now < skillCooldown.getOrDefault(player.getUniqueId(), 0L)) {
            player.sendMessage(Component.text("쿨타임 중입니다.", NamedTextColor.RED));
            return;
        }

        if (!cost.consumeFromInventory(player)) return;
        long cooldownMs = plugin.getAbilityConfigManager()
                .getLong(getName(), "skills.escape.cooldown-ms", 10000L);
        int speedTicks = plugin.getAbilityConfigManager()
                .getInt(getName(), "skills.escape.speed-ticks", 300);
        int speedAmp = plugin.getAbilityConfigManager()
                .getInt(getName(), "skills.escape.speed-amplifier", 49);
        skillCooldown.put(player.getUniqueId(), now + cooldownMs);

        AttributeInstance maxHealthAttr = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealthAttr != null) {
            player.setHealth(maxHealthAttr.getValue());
        }

        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, speedTicks, speedAmp));
        player.sendMessage(Component.text("[!] 스킬 {난 햇빛을 피해 도망치는 것이다.} 발동!", NamedTextColor.YELLOW));
        SkillParticles.fighterEscape(player);
    }

    private boolean isHasAbility(Player player) {
        return getName().equals(plugin.getAbilityManager().getPlayerData(player.getUniqueId()).getAbilityName());
    }
}
