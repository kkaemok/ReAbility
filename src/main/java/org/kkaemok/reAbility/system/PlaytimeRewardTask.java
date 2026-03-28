package org.kkaemok.reAbility.system;

import org.bukkit.Bukkit;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.kkaemok.reAbility.ability.AbilityManager;
import org.kkaemok.reAbility.data.PlayerData;

public class PlaytimeRewardTask extends BukkitRunnable {
    private static final int TICKS_PER_HOUR = 20 * 60 * 60;
    private static final long COINS_PER_HOUR = 10L;

    private final AbilityManager abilityManager;

    public PlaytimeRewardTask(AbilityManager abilityManager) {
        this.abilityManager = abilityManager;
    }

    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerData data = abilityManager.getPlayerData(player.getUniqueId());
            long playTicks = player.getStatistic(Statistic.PLAY_ONE_MINUTE);
            long totalHours = playTicks / TICKS_PER_HOUR;
            long rewardedHours = data.getPlaytimeHoursRewarded();

            if (rewardedHours < 0) {
                data.setPlaytimeHoursRewarded(totalHours);
                abilityManager.savePlayerData(player.getUniqueId());
                continue;
            }

            long diff = totalHours - rewardedHours;
            if (diff <= 0) continue;

            data.addCoins(diff * COINS_PER_HOUR);
            data.setPlaytimeHoursRewarded(totalHours);
            abilityManager.savePlayerData(player.getUniqueId());
        }
    }
}
