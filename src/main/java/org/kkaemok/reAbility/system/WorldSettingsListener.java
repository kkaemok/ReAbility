package org.kkaemok.reAbility.system;

import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;

public class WorldSettingsListener implements Listener {

    // 플레이어 접속 시 체력 설정
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // 기본 체력을 2줄(40.0)로 설정
        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(40.0);
            player.setHealth(40.0); // 접속 시 풀피로 설정
        }
    }

    // 도끼 데미지 보정 (검보다 2 낮게)
    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            Material weapon = player.getInventory().getItemInMainHand().getType();

            // 손에 든 것이 도끼류인지 확인
            if (weapon.name().endsWith("_AXE")) {
                double originalDamage = event.getDamage();
                // 데미지를 2만큼 감소 (최소 1데미지는 들어가도록 설정)
                event.setDamage(Math.max(1.0, originalDamage - 2.0));
            }
        }
    }
}