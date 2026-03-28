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
import org.kkaemok.reAbility.ability.SkillCost;
import org.kkaemok.reAbility.utils.SkillParticles;

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
                "불사의 토템 20개 획득, HP 1칸 이하 시 30초간 죽음을 면함.",
                "스킬 {죽음 회피}: 다이아 50개 소모, 즉시 HP 5칸 회복",
                "재생 2 (10초), 저항 2 (1분) 획득 (쿨타임 1분 30초)."
        };
    }

    @Override
    public void onAcquire(Player player) {
        player.getInventory().addItem(new ItemStack(Material.TOTEM_OF_UNDYING, 20));
        player.sendMessage(Component.text("[!] 불사의 토템 20개를 획득했습니다.", NamedTextColor.GOLD));
    }

    @Override
    public void onDeactivate(Player player) {
        invincibilityTime.remove(player.getUniqueId());
        player.removePotionEffect(PotionEffectType.REGENERATION);
        player.removePotionEffect(PotionEffectType.RESISTANCE);
    }

    @EventHandler
    public void onFatalDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || !isHasAbility(player)) return;

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
        SkillCost cost = plugin.getAbilityConfigManager()
                .getSkillCost(getName(), "death_evade", Material.DIAMOND, 50);
        if (!cost.matchesHand(item)) return;

        long now = System.currentTimeMillis();
        if (now < skillCooldown.getOrDefault(player.getUniqueId(), 0L)) {
            player.sendMessage(Component.text("쿨타임 중입니다.", NamedTextColor.RED));
            return;
        }

        if (!cost.consumeFromHand(player)) return;
        long cooldownMs = plugin.getAbilityConfigManager()
                .getLong(getName(), "skills.death_evade.cooldown-ms", 90000L);
        double healAmount = plugin.getAbilityConfigManager()
                .getDouble(getName(), "skills.death_evade.heal-amount", 10.0);
        int regenTicks = plugin.getAbilityConfigManager()
                .getInt(getName(), "skills.death_evade.regen-ticks", 200);
        int resistanceTicks = plugin.getAbilityConfigManager()
                .getInt(getName(), "skills.death_evade.resistance-ticks", 1200);
        skillCooldown.put(player.getUniqueId(), now + cooldownMs);

        double maxHealth = player.getAttribute(Attribute.MAX_HEALTH).getValue();
        player.setHealth(Math.min(maxHealth, player.getHealth() + healAmount));
        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, regenTicks, 1));
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, resistanceTicks, 1));

        player.playSound(player.getLocation(), Sound.ITEM_TOTEM_USE, 1.0f, 1.0f);
        player.sendMessage(Component.text("[!] 스킬 {죽음 회피} 발동!", NamedTextColor.GOLD));
        SkillParticles.phoenixEvade(player);
    }

    private boolean isHasAbility(Player player) {
        return getName().equals(plugin.getAbilityManager().getPlayerData(player.getUniqueId()).getAbilityName());
    }
}
