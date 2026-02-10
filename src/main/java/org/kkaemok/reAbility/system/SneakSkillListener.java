package org.kkaemok.reAbility.system;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.kkaemok.reAbility.ability.AbilityBase;
import org.kkaemok.reAbility.ability.AbilityManager;
import org.kkaemok.reAbility.data.PlayerData;

public class SneakSkillListener implements Listener {
    private final AbilityManager abilityManager;

    public SneakSkillListener(AbilityManager abilityManager) {
        this.abilityManager = abilityManager;
    }

    @EventHandler
    public void onToggleSneak(PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) return;

        Player player = event.getPlayer();
        PlayerData data = abilityManager.getPlayerData(player.getUniqueId());
        if (data == null) return;

        String abilityName = data.getAbilityName();
        if (abilityName == null) return;

        AbilityBase ability = abilityManager.getAbilityByName(abilityName);
        if (ability == null) return;

        ability.onSneakSkill(player);
    }
}
