package org.kkaemok.reAbility.system;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.kkaemok.reAbility.ability.AbilityBase;
import org.kkaemok.reAbility.ability.AbilityManager;
import org.kkaemok.reAbility.data.PlayerData;
import org.kkaemok.reAbility.utils.AbilityTags;
import org.kkaemok.reAbility.utils.InventoryUtils;

public class SneakSkillListener implements Listener {
    private final AbilityManager abilityManager;

    public SneakSkillListener(AbilityManager abilityManager) {
        this.abilityManager = abilityManager;
    }

    @EventHandler
    public void onToggleSneak(PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) return;

        Player player = event.getPlayer();
        if (tryRemoveJokerIllusion(player)) return;
        PlayerData data = abilityManager.getPlayerData(player.getUniqueId());
        if (data == null) return;

        String abilityName = data.getAbilityName();
        if (abilityName == null) return;

        AbilityBase ability = abilityManager.getAbilityByName(abilityName);
        if (ability == null) return;

        ability.onSneakSkill(player);
    }

    private boolean tryRemoveJokerIllusion(Player player) {
        if (!player.getScoreboardTags().contains(AbilityTags.JOKER_ILLUSION)) return false;

        PotionEffect effect = player.getPotionEffect(PotionEffectType.WEAKNESS);
        if (effect == null || effect.getAmplifier() < 99) {
            player.removeScoreboardTag(AbilityTags.JOKER_ILLUSION);
            return false;
        }

        if (player.getInventory().getItemInMainHand().getType() != Material.EMERALD) return false;
        if (!InventoryUtils.consume(player, Material.EMERALD, 1)) return false;

        player.removePotionEffect(PotionEffectType.WEAKNESS);
        player.removeScoreboardTag(AbilityTags.JOKER_ILLUSION);
        player.sendMessage("나약함 효과가 해제되었습니다.");
        return true;
    }
}
