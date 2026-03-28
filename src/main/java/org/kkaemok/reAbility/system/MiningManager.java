package org.kkaemok.reAbility.system;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.kkaemok.reAbility.ReAbility;
import org.kkaemok.reAbility.ability.AbilityManager;
import org.kkaemok.reAbility.data.PlayerData;

public class MiningManager implements Listener {

    private final ReAbility plugin;
    private final AbilityManager abilityManager;

    public MiningManager(ReAbility plugin, AbilityManager abilityManager) {
        this.plugin = plugin;
        this.abilityManager = abilityManager;
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Material blockType = event.getBlock().getType();

        if (blockType != Material.DIAMOND_ORE
                && blockType != Material.DEEPSLATE_DIAMOND_ORE
                && blockType != Material.ANCIENT_DEBRIS) {
            return;
        }

        PlayerData data = abilityManager.getPlayerData(player.getUniqueId());
        String ability = data.getAbilityName();
        boolean isMiner = ability != null && ability.equals("MINER");

        long now = System.currentTimeMillis();

        if (blockType == Material.DIAMOND_ORE || blockType == Material.DEEPSLATE_DIAMOND_ORE) {
            long resetIntervalMs = getResetIntervalMs("diamond", 72000L);
            if (now - data.getLastDiamondReset() >= resetIntervalMs) {
                data.resetDiamond();
            }

            int baseLimit = getLimit("diamond", 100);
            int minerBonus = getMinerBonus("diamond", 700);
            int limit = isMiner ? baseLimit + Math.max(0, minerBonus) : baseLimit;
            if (data.getMinedDiamond() >= limit) {
                event.setCancelled(true);
                player.sendActionBar(Component.text(
                        "다이아몬드 채굴 제한에 도달했습니다. (1시간마다 초기화)",
                        NamedTextColor.RED));
            } else {
                data.addMinedDiamond();
            }
        } else {
            long resetIntervalMs = getResetIntervalMs("ancient-debris", 216000L);
            if (now - data.getLastDebrisReset() >= resetIntervalMs) {
                data.resetDebris();
            }

            int baseLimit = getLimit("ancient-debris", 2);
            int minerBonus = getMinerBonus("ancient-debris", 7);
            int limit = isMiner ? baseLimit + Math.max(0, minerBonus) : baseLimit;
            if (data.getMinedDebris() >= limit) {
                event.setCancelled(true);
                player.sendActionBar(Component.text(
                        "고대 잔해 채굴 제한에 도달했습니다. (3시간마다 초기화)",
                        NamedTextColor.RED));
            } else {
                data.addMinedDebris();
            }
        }
    }

    private int getLimit(String key, int def) {
        return plugin.getConfig().getInt("mining-limits." + key + ".amount", def);
    }

    private int getMinerBonus(String key, int def) {
        return plugin.getConfig().getInt("mining-limits." + key + ".miner-bonus", def);
    }

    private long getResetIntervalMs(String key, long defTicks) {
        long ticks = plugin.getConfig().getLong("mining-limits." + key + ".reset-interval-ticks", defTicks);
        return Math.max(0L, ticks) * 50L;
    }
}
