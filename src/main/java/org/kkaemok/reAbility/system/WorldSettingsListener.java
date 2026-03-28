package org.kkaemok.reAbility.system;

import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.kkaemok.reAbility.ReAbility;

public class WorldSettingsListener implements Listener {
    private final ReAbility plugin;

    public WorldSettingsListener(ReAbility plugin) {
        this.plugin = plugin;
    }

    // 플레이어 접속 시 기본 체력 설정
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        double maxHealthValue = plugin.getConfig().getDouble("world-settings.default-max-health", 40.0);
        if (maxHealthValue <= 0) {
            return;
        }

        // 기본 체력을 설정
        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(maxHealthValue);
        }
    }

    // 도끼 공격 데미지 보정 (검보다 2 낮음)
    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            Material weapon = player.getInventory().getItemInMainHand().getType();

            // 손에 든 무기가 도끼인지 확인
            if (weapon.name().endsWith("_AXE")) {
                double reduction = plugin.getConfig().getDouble("world-settings.axe-damage-reduction", 2.0);
                if (reduction <= 0) return;

                double originalDamage = event.getDamage();
                // 데미지를 감소 (최소 1 데미지는 적용)
                event.setDamage(Math.max(1.0, originalDamage - reduction));
            }
        }
    }
}
