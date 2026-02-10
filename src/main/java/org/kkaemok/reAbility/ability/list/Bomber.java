package org.kkaemok.reAbility.ability.list;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.kkaemok.reAbility.ReAbility;
import org.kkaemok.reAbility.ability.AbilityBase;
import org.kkaemok.reAbility.ability.AbilityGrade;

public class Bomber extends AbilityBase {
    private final ReAbility plugin;

    public Bomber(ReAbility plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() { return "BOMBER"; }

    @Override
    public String getDisplayName() { return "자폭러"; }

    @Override
    public AbilityGrade getGrade() { return AbilityGrade.C; }

    @Override
    public String[] getDescription() {
        return new String[]{
                "사망 시 주변이 터지며 30~80의 폭발 피해를 입힘",
                "(각 피해 확률 2%). 폭발 피해 50% 감소."
        };
    }

    @Override
    public void onActivate(Player player) {}

    @Override
    public void onDeactivate(Player player) {}

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!isHasAbility(player)) return;

        if (event.getCause() == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION ||
                event.getCause() == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION) {
            event.setDamage(event.getDamage() * 0.5);
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player p = event.getEntity();
        if (!isHasAbility(p)) return;

        // 블록 파괴 없이 데미지만 주는 폭발.
        p.getWorld().createExplosion(p.getLocation(), 6.0f, false, false);
    }

    private boolean isHasAbility(Player player) {
        String ability = plugin.getAbilityManager().getPlayerData(player.getUniqueId()).getAbilityName();
        return getName().equals(ability);
    }
}
