package org.kkaemok.reAbility.ability.list;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.kkaemok.reAbility.ability.AbilityBase;
import org.kkaemok.reAbility.ability.AbilityGrade;

public class Bomber extends AbilityBase {
    @Override
    public String getName() { return "BOMBER"; }
    @Override
    public String getDisplayName() { return "자폭러"; }
    @Override
    public AbilityGrade getGrade() { return AbilityGrade.C; }
    @Override
    public String[] getDescription() { return new String[]{"폭발 피해를 50% 감소시켜 받습니다.", "사망 시 주변에 강력한 폭발을 일으킵니다."}; }

    @Override
    public void onActivate(Player player) {}
    @Override
    public void onDeactivate(Player player) {}

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player) {
            if (event.getCause() == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION ||
                    event.getCause() == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION) {
                event.setDamage(event.getDamage() * 0.5);
            }
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player p = event.getEntity();
        // 파괴 없이 데미지만 주는 폭발 (강도 6.0)
        p.getWorld().createExplosion(p.getLocation(), 6.0f, false, false);
    }
}