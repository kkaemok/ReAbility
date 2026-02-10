package org.kkaemok.reAbility.system;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.kkaemok.reAbility.ability.AbilityManager;

public class AbilityListener implements Listener {

    private final AbilityManager abilityManager;

    public AbilityListener(AbilityManager abilityManager) {
        this.abilityManager = abilityManager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // 접속 시 능력 적용 및 만료 시 재추첨.
        abilityManager.handleJoin(event.getPlayer());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Bukkit.getScheduler().runTaskLater(JavaPlugin.getProvidingPlugin(getClass()),
                () -> abilityManager.handleJoin(event.getPlayer()), 1L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // 안전장치로 접속 종료 시 데이터 저장.
        abilityManager.savePlayerData(event.getPlayer().getUniqueId());
    }
}
