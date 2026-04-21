package org.kkaemok.reAbility.ability.list;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.kkaemok.reAbility.ReAbility;
import org.kkaemok.reAbility.ability.AbilityBase;
import org.kkaemok.reAbility.ability.AbilityGrade;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class GanggangYakyak extends AbilityBase {
    private static final double WEAKEN_RANGE = 10.0;
    private static final long EFFECT_COOLDOWN_MS = 1500L;

    private final ReAbility plugin;
    private final Map<UUID, Long> hitCooldown = new HashMap<>();

    public GanggangYakyak(ReAbility plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "GANGGANGYAKYAK";
    }

    @Override
    public String getDisplayName() {
        return "강강약약";
    }

    @Override
    public AbilityGrade getGrade() {
        return AbilityGrade.C;
    }

    @Override
    public String[] getDescription() {
        return new String[]{
                "A등급 이상 타격 시 추가 피해 +7, 구속 2 + 위더 3초 (쿨타임 1.5초).",
                "주변에 B등급 이하가 있으면 약화: 추가 피해 +4, 구속 1 (3초).",
                "약화 상태에서 피격 시 나약함 1 (5초)."
        };
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        Player attacker = getDamagingPlayer(event.getDamager());
        Player victim = event.getEntity() instanceof Player target ? target : null;

        if (attacker != null && victim != null && isHasAbility(attacker)) {
            if (isGradeAOrHigher(victim) && isCooldownReady(attacker)) {
                boolean weakened = isWeakened(attacker);

                event.setDamage(event.getDamage() + (weakened ? 4.0 : 7.0));
                victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, weakened ? 0 : 1, false, false));
                if (!weakened) {
                    victim.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 60, 0, false, false));
                }
                hitCooldown.put(attacker.getUniqueId(), System.currentTimeMillis() + EFFECT_COOLDOWN_MS);
            }
        }

        Player damaged = event.getEntity() instanceof Player p ? p : null;
        if (damaged == null || !isHasAbility(damaged)) return;
        if (!isWeakened(damaged)) return;

        damaged.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 100, 0, false, false));
    }

    private boolean isCooldownReady(Player player) {
        long now = System.currentTimeMillis();
        return now >= hitCooldown.getOrDefault(player.getUniqueId(), 0L);
    }

    private boolean isWeakened(Player owner) {
        double rangeSquared = WEAKEN_RANGE * WEAKEN_RANGE;
        for (Entity entity : owner.getNearbyEntities(WEAKEN_RANGE, WEAKEN_RANGE, WEAKEN_RANGE)) {
            if (!(entity instanceof Player nearby)) continue;
            if (nearby.equals(owner)) continue;
            if (!nearby.getWorld().equals(owner.getWorld())) continue;
            if (nearby.getLocation().distanceSquared(owner.getLocation()) > rangeSquared) continue;
            AbilityGrade grade = getPlayerAbilityGrade(nearby);
            if (grade == null) continue;
            if (grade == AbilityGrade.D || grade == AbilityGrade.C || grade == AbilityGrade.B) {
                return true;
            }
        }
        return false;
    }

    private boolean isGradeAOrHigher(Player player) {
        AbilityGrade grade = getPlayerAbilityGrade(player);
        if (grade == null) return false;
        return grade.isAtLeast(AbilityGrade.A);
    }

    private AbilityGrade getPlayerAbilityGrade(Player player) {
        String abilityName = plugin.getAbilityManager().getPlayerData(player.getUniqueId()).getAbilityName();
        if (abilityName == null) return null;
        AbilityBase ability = plugin.getAbilityManager().getAbilityByName(abilityName);
        return ability != null ? ability.getGrade() : null;
    }

    private Player getDamagingPlayer(Entity damager) {
        if (damager instanceof Player player) return player;
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) {
            return shooter;
        }
        return null;
    }

    private boolean isHasAbility(Player player) {
        String ability = plugin.getAbilityManager().getPlayerData(player.getUniqueId()).getAbilityName();
        return getName().equals(ability);
    }
}
