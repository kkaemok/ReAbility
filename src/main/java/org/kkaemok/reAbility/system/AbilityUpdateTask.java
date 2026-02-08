package org.kkaemok.reAbility.system;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.kkaemok.reAbility.ability.AbilityGrade;
import org.kkaemok.reAbility.ability.AbilityManager;
import org.kkaemok.reAbility.data.PlayerData;

public class AbilityUpdateTask extends BukkitRunnable {

    private final AbilityManager abilityManager;

    public AbilityUpdateTask(AbilityManager abilityManager) {
        this.abilityManager = abilityManager;
    }

    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerData data = abilityManager.getPlayerData(player.getUniqueId());

            // 능력이 있고, 만료 시간이 지났다면
            if (data.getAbilityName() != null && data.isExpired()) {
                // 새로운 능력 자동 부여 (D등급)
                abilityManager.assignAbilityByGrade(player, AbilityGrade.D);

                // 메시지 및 사운드 출력 (최신 Component 방식)
                player.sendMessage(Component.text("[!] ", NamedTextColor.GOLD)
                        .append(Component.text("능력 유지 기간이 만료되어 새로운 능력이 배정되었습니다!", NamedTextColor.WHITE)));

                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
            }
        }
    }
}