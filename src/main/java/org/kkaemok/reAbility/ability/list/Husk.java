package org.kkaemok.reAbility.ability.list;

import org.bukkit.entity.AbstractSkeleton;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.Stray;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.kkaemok.reAbility.ReAbility;
import org.kkaemok.reAbility.ability.AbilityBase;
import org.kkaemok.reAbility.ability.AbilityGrade;

public class Husk extends AbilityBase {
    private final ReAbility plugin;

    public Husk(ReAbility plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "HUSK";
    }

    @Override
    public String getDisplayName() {
        return "허스크";
    }

    @Override
    public AbilityGrade getGrade() {
        return AbilityGrade.D;
    }

    @Override
    public String[] getDescription() {
        return new String[]{
                "자신에게 타격받은 적은 허기 3을 10초 동안 받음.",
                "좀비/스켈레톤에게 공격받으면 재생 2를 3초 동안 획득."
        };
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        Player attacker = getDamagingPlayer(event.getDamager());
        if (attacker != null && isHasAbility(attacker) && event.getEntity() instanceof Player victim) {
            victim.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, 200, 2, false, false));
        }

        if (!(event.getEntity() instanceof Player player)) return;
        if (!isHasAbility(player)) return;
        if (!isZombieOrSkeletonDamage(event.getDamager())) return;

        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 60, 1, false, false));
    }

    private Player getDamagingPlayer(Entity damager) {
        if (damager instanceof Player player) return player;
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) {
            return shooter;
        }
        return null;
    }

    private boolean isZombieOrSkeletonDamage(Entity damager) {
        if (damager instanceof Zombie || damager instanceof Skeleton || damager instanceof Stray || damager instanceof AbstractSkeleton) {
            return true;
        }
        if (damager instanceof Projectile projectile) {
            return projectile.getShooter() instanceof Zombie
                    || projectile.getShooter() instanceof Skeleton
                    || projectile.getShooter() instanceof Stray
                    || projectile.getShooter() instanceof AbstractSkeleton;
        }
        return false;
    }

    private boolean isHasAbility(Player player) {
        return getName().equals(plugin.getAbilityManager().getPlayerData(player.getUniqueId()).getAbilityName());
    }
}
