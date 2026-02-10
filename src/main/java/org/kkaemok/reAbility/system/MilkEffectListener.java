package org.kkaemok.reAbility.system;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.kkaemok.reAbility.ReAbility;
import org.kkaemok.reAbility.ability.AbilityBase;
import org.kkaemok.reAbility.ability.AbilityGrade;
import org.kkaemok.reAbility.ability.AbilityManager;
import org.kkaemok.reAbility.data.PlayerData;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MilkEffectListener implements Listener {
    private final ReAbility plugin;
    private final AbilityManager abilityManager;
    private final Map<UUID, Long> zombieWeaknessSuppressedUntil = new HashMap<>();

    public MilkEffectListener(ReAbility plugin, AbilityManager abilityManager) {
        this.plugin = plugin;
        this.abilityManager = abilityManager;
    }

    @EventHandler
    public void onConsume(PlayerItemConsumeEvent event) {
        if (event.isCancelled()) return;

        Player player = event.getPlayer();
        Material type = event.getItem().getType();

        if (type == Material.GOLDEN_APPLE) {
            if (isZombie(player)) {
                zombieWeaknessSuppressedUntil.put(player.getUniqueId(), System.currentTimeMillis() + 30000L);
            }
            return;
        }

        if (type != Material.MILK_BUCKET) return;

        Map<PotionEffectType, PotionEffect> snapshot = snapshotEffects(player.getActivePotionEffects());
        Bukkit.getScheduler().runTaskLater(plugin, () -> reapplyAbilityEffects(player, snapshot), 1L);
    }

    private void reapplyAbilityEffects(Player player, Map<PotionEffectType, PotionEffect> snapshot) {
        if (!player.isOnline()) return;

        PlayerData data = abilityManager.getPlayerData(player.getUniqueId());
        if (data == null) return;

        String abilityName = data.getAbilityName();
        if (abilityName == null) return;

        AbilityBase ability = abilityManager.getAbilityByName(abilityName);
        if (ability == null) return;

        AbilityGrade grade = ability.getGrade();
        if (grade == AbilityGrade.S || grade == AbilityGrade.SS) {
            applyEffect(player, PotionEffectType.RESISTANCE, PotionEffect.INFINITE_DURATION, 0);
            applyEffect(player, PotionEffectType.REGENERATION, PotionEffect.INFINITE_DURATION, 0);
        }

        switch (abilityName) {
            case "EATER", "HOMELOVER" -> applyEffect(player, PotionEffectType.REGENERATION, Integer.MAX_VALUE, 0);
            case "LIGHTER" -> applyEffect(player, PotionEffectType.FIRE_RESISTANCE, Integer.MAX_VALUE, 0);
            case "FIGHTER" -> applyEffect(player, PotionEffectType.STRENGTH, PotionEffect.INFINITE_DURATION, 1);
            case "GHOST" -> applyEffect(player, PotionEffectType.SPEED, PotionEffect.INFINITE_DURATION, 1);
            case "GUNSLINGER" -> applyEffect(player, PotionEffectType.RESISTANCE, PotionEffect.INFINITE_DURATION, 0);
            case "JOKER", "SPACE_RULER" -> {
                applyEffect(player, PotionEffectType.RESISTANCE, PotionEffect.INFINITE_DURATION, 0);
                applyEffect(player, PotionEffectType.REGENERATION, PotionEffect.INFINITE_DURATION, 0);
            }
            case "MINER" -> {
                applyEffect(player, PotionEffectType.HASTE, PotionEffect.INFINITE_DURATION, 1);
                if (player.getLocation().getBlockY() <= 0) {
                    applyEffect(player, PotionEffectType.RESISTANCE, 40, 1);
                    applyEffect(player, PotionEffectType.STRENGTH, 40, 0);
                    applyEffect(player, PotionEffectType.REGENERATION, 40, 0);
                }
            }
            case "WEREWOLF" -> {
                if (isNight(player)) {
                    applyEffect(player, PotionEffectType.STRENGTH, Integer.MAX_VALUE, 1);
                    applyEffect(player, PotionEffectType.RESISTANCE, Integer.MAX_VALUE, 1);
                }
            }
            case "ZOMBIE" -> {
                if (!isZombieWeaknessSuppressed(player)) {
                    applyEffect(player, PotionEffectType.WEAKNESS, Integer.MAX_VALUE, 0);
                }
                applyEffect(player, PotionEffectType.HUNGER, Integer.MAX_VALUE, 1);
            }
            default -> {}
        }

        reapplyExtraBuffs(player, abilityName, snapshot);
    }

    private void applyEffect(Player player, PotionEffectType type, int duration, int amplifier) {
        player.addPotionEffect(new PotionEffect(type, duration, amplifier, false, false));
    }

    private void reapplyExtraBuffs(Player player, String abilityName, Map<PotionEffectType, PotionEffect> snapshot) {
        switch (abilityName) {
            case "FIGHTER" -> reapplySnapshotEffect(player, snapshot, PotionEffectType.SPEED, 49);
            case "GHOST" -> reapplySnapshotEffect(player, snapshot, PotionEffectType.STRENGTH, 0);
            case "PHOENIX" -> {
                reapplySnapshotEffect(player, snapshot, PotionEffectType.REGENERATION, 1);
                reapplySnapshotEffect(player, snapshot, PotionEffectType.RESISTANCE, 1);
            }
            case "SPACE_RULER" -> reapplySnapshotEffect(player, snapshot, PotionEffectType.STRENGTH, 2);
            case "BEAUTY" -> {
                reapplySnapshotEffect(player, snapshot, PotionEffectType.REGENERATION, 0);
                reapplySnapshotEffect(player, snapshot, PotionEffectType.RESISTANCE, 0);
            }
            case "CHRONOS" -> {
                reapplySnapshotEffect(player, snapshot, PotionEffectType.STRENGTH, 2);
                reapplySnapshotEffect(player, snapshot, PotionEffectType.REGENERATION, 1);
            }
            case "LOVEBIRD" -> {
                reapplySnapshotEffect(player, snapshot, PotionEffectType.REGENERATION, 1);
                reapplySnapshotEffect(player, snapshot, PotionEffectType.STRENGTH, 0);
            }
            default -> {}
        }
    }

    private void reapplySnapshotEffect(Player player, Map<PotionEffectType, PotionEffect> snapshot,
                                       PotionEffectType type, int amplifier) {
        PotionEffect effect = snapshot.get(type);
        if (effect == null || effect.getAmplifier() != amplifier) return;
        player.addPotionEffect(effect);
    }

    private Map<PotionEffectType, PotionEffect> snapshotEffects(Collection<PotionEffect> effects) {
        Map<PotionEffectType, PotionEffect> snapshot = new HashMap<>();
        for (PotionEffect effect : effects) {
            snapshot.put(effect.getType(), effect);
        }
        return snapshot;
    }

    private boolean isZombie(Player player) {
        PlayerData data = abilityManager.getPlayerData(player.getUniqueId());
        return data != null && "ZOMBIE".equals(data.getAbilityName());
    }

    private boolean isNight(Player player) {
        long time = player.getWorld().getTime();
        return time >= 13000 && time <= 23000;
    }

    private boolean isZombieWeaknessSuppressed(Player player) {
        Long until = zombieWeaknessSuppressedUntil.get(player.getUniqueId());
        if (until == null) return false;
        if (System.currentTimeMillis() >= until) {
            zombieWeaknessSuppressedUntil.remove(player.getUniqueId());
            return false;
        }
        return true;
    }
}
