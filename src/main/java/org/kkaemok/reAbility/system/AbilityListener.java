package org.kkaemok.reAbility.system;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.kkaemok.reAbility.ability.AbilityManager;

public class AbilityListener implements Listener {

    private final AbilityManager abilityManager;

    public AbilityListener(AbilityManager abilityManager) {
        this.abilityManager = abilityManager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // 접속 시 능력 유효기간 확인 및 재부여 로직 실행
        abilityManager.handleJoin(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // 나갈 때 데이터 저장 (선택 사항, onDisable에서 일괄 저장하지만 안전을 위해)
        abilityManager.savePlayerData(event.getPlayer().getUniqueId());
    }
}