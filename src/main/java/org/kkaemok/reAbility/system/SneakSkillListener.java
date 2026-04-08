package org.kkaemok.reAbility.system;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.block.Block;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.kkaemok.reAbility.ReAbility;
import org.kkaemok.reAbility.ability.AbilityBase;
import org.kkaemok.reAbility.ability.AbilityManager;
import org.kkaemok.reAbility.ability.list.Disruptor;
import org.kkaemok.reAbility.data.PlayerData;
import org.kkaemok.reAbility.utils.AbilityTags;
import org.kkaemok.reAbility.utils.InventoryUtils;

public class SneakSkillListener implements Listener {
    private static final String BLOCK_GUARD_ENABLED_PATH = "sneak-skill.block-look-guard.enabled";
    private static final String BLOCK_GUARD_MAX_DISTANCE_PATH = "sneak-skill.block-look-guard.max-distance";
    private static final String BLOCK_GUARD_MESSAGE_PATH = "sneak-skill.block-look-guard.message";

    private final ReAbility plugin;
    private final AbilityManager abilityManager;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public SneakSkillListener(ReAbility plugin, AbilityManager abilityManager) {
        this.plugin = plugin;
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

        if (!hasSneakSkill(ability)) return;
        if (shouldBlockSneakSkill(player, ability)) return;
        if (Disruptor.tryFailSkill(plugin, player)) return;

        ability.onSneakSkill(player);
    }

    private boolean shouldBlockSneakSkill(Player player, AbilityBase ability) {
        if (!hasSneakSkill(ability)) return false;
        if (!plugin.getConfig().getBoolean(BLOCK_GUARD_ENABLED_PATH, true)) return false;

        int maxDistance = Math.max(1, plugin.getConfig().getInt(BLOCK_GUARD_MAX_DISTANCE_PATH, 6));
        Block targetBlock = player.getTargetBlockExact(maxDistance);
        if (targetBlock == null || targetBlock.getType().isAir()) return false;

        String message = plugin.getConfig().getString(BLOCK_GUARD_MESSAGE_PATH, "블럭을 보고 있습니다!");
        if (message == null || message.isBlank()) {
            message = "블럭을 보고 있습니다!";
        }
        player.sendMessage(miniMessage.deserialize(message));
        return true;
    }

    private boolean hasSneakSkill(AbilityBase ability) {
        try {
            return ability.getClass()
                    .getMethod("onSneakSkill", Player.class)
                    .getDeclaringClass() != AbilityBase.class;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    private boolean tryRemoveJokerIllusion(Player player) {
        if (!player.getScoreboardTags().contains(AbilityTags.JOKER_ILLUSION)) return false;

        PotionEffect effect = player.getPotionEffect(PotionEffectType.WEAKNESS);
        if (effect == null || effect.getAmplifier() < 99) {
            player.removeScoreboardTag(AbilityTags.JOKER_ILLUSION);
            return false;
        }

        if (!InventoryUtils.consume(player, Material.EMERALD, 1)) return false;

        player.removePotionEffect(PotionEffectType.WEAKNESS);
        player.removeScoreboardTag(AbilityTags.JOKER_ILLUSION);
        player.sendMessage("나약함 효과가 해제되었습니다.");
        return true;
    }
}
