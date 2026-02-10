package org.kkaemok.reAbility.system;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.kkaemok.reAbility.ability.AbilityManager;
import org.kkaemok.reAbility.data.PlayerData;

public class MiningManager implements Listener {

    private final AbilityManager abilityManager;

    public MiningManager(AbilityManager abilityManager) {
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
            if (now - data.getLastDiamondReset() >= 3600000) {
                data.resetDiamond();
            }

            int limit = isMiner ? 700 : 100;
            if (data.getMinedDiamond() >= limit) {
                event.setCancelled(true);
                player.sendActionBar(Component.text(
                        "다이아몬드 채굴 제한에 도달했습니다. (1시간마다 초기화)",
                        NamedTextColor.RED));
            } else {
                data.addMinedDiamond();
            }
        } else if (blockType == Material.ANCIENT_DEBRIS) {
            if (now - data.getLastDebrisReset() >= 10800000) {
                data.resetDebris();
            }

            int limit = isMiner ? 7 : 2;
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
}
