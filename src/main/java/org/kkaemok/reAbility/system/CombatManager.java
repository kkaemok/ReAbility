package org.kkaemok.reAbility.system;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CombatManager implements Listener {
    // 플레이어 UUID와 마지막 전투 시간 저장
    private final Map<UUID, Long> combatTime = new HashMap<>();
    private final long COMBAT_DURATION = 10 * 1000L; // 10초

    // 전투 중인지 확인
    public boolean isInCombat(Player player) {
        if (!combatTime.containsKey(player.getUniqueId())) return false;
        long lastCombat = combatTime.get(player.getUniqueId());
        return System.currentTimeMillis() - lastCombat < COMBAT_DURATION;
    }

    // 전투 시간 갱신
    public void setCombat(Player player) {
        combatTime.put(player.getUniqueId(), System.currentTimeMillis());
    }

    // 데미지 이벤트 발생 시 전투 상태로 전환
    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player attacker && event.getEntity() instanceof Player victim) {
            setCombat(attacker);
            setCombat(victim);
        }
    }
}