package org.kkaemok.reAbility.ability.list;

import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.kkaemok.reAbility.ReAbility;
import org.kkaemok.reAbility.ability.AbilityBase;
import org.kkaemok.reAbility.ability.AbilityGrade;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SoloLeveler extends AbilityBase {
    private static final double BASE_MAX_HEALTH = 40.0;

    private final ReAbility plugin;
    private final Map<UUID, BukkitTask> tasks = new HashMap<>();
    private final Set<UUID> bonusExpRecipients = ConcurrentHashMap.newKeySet();

    public SoloLeveler(ReAbility plugin) {
        this.plugin = plugin;
    }

    @Override public String getName() { return "SOLO_LEVELER"; }
    @Override public String getDisplayName() { return "나만 레벨업"; }
    @Override public AbilityGrade getGrade() { return AbilityGrade.C; }

    @Override
    public String[] getDescription() {
        return new String[]{
                "다른 사람이 얻는 경험치의 1/10 획득.",
                "레벨 10: 점프 강화 1",
                "레벨 20: 재생 1",
                "레벨 30: 저항 1",
                "레벨 50: 체력 5칸 증가",
                "레벨 100: 힘 1",
                "레벨 150: 저항 2, 체력 한줄 증가"
        };
    }

    @Override
    public void onActivate(Player player) {
        startTask(player);
        applyLevelEffects(player);
    }

    @Override
    public void onDeactivate(Player player) {
        BukkitTask task = tasks.remove(player.getUniqueId());
        if (task != null) task.cancel();

        player.removePotionEffect(PotionEffectType.JUMP_BOOST);
        player.removePotionEffect(PotionEffectType.REGENERATION);
        player.removePotionEffect(PotionEffectType.RESISTANCE);
        player.removePotionEffect(PotionEffectType.STRENGTH);

        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(BASE_MAX_HEALTH);
            if (player.getHealth() > BASE_MAX_HEALTH) player.setHealth(BASE_MAX_HEALTH);
        }
    }

    @EventHandler
    public void onExpChange(PlayerExpChangeEvent event) {
        if (event.getAmount() <= 0) return;

        Player source = event.getPlayer();
        if (bonusExpRecipients.remove(source.getUniqueId())) return;

        int bonus = event.getAmount() / 10;
        if (bonus <= 0) return;

        for (Player target : Bukkit.getOnlinePlayers()) {
            if (target.equals(source)) continue;
            if (!isHasAbility(target)) continue;
            bonusExpRecipients.add(target.getUniqueId());
            target.giveExp(bonus);
        }
    }

    private void startTask(Player player) {
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || !isHasAbility(player)) {
                    BukkitTask current = tasks.remove(player.getUniqueId());
                    if (current != null) current.cancel();
                    return;
                }
                applyLevelEffects(player);
            }
        }.runTaskTimer(plugin, 0L, 40L);
        tasks.put(player.getUniqueId(), task);
    }

    public static void applyLevelEffects(Player player) {
        int level = player.getLevel();

        int duration = 60;
        if (level >= 10) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, duration, 0, false, false));
        }
        if (level >= 20) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, duration, 0, false, false));
        }
        if (level >= 100) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, duration, 0, false, false));
        }
        if (level >= 150) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, duration, 1, false, false));
        } else if (level >= 30) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, duration, 0, false, false));
        }

        double extra = 0.0;
        if (level >= 50) extra += 10.0;
        if (level >= 150) extra += 20.0;

        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            double targetMax = BASE_MAX_HEALTH + extra;
            if (maxHealth.getBaseValue() != targetMax) {
                maxHealth.setBaseValue(targetMax);
                if (player.getHealth() > targetMax) player.setHealth(targetMax);
            }
        }
    }

    private boolean isHasAbility(Player player) {
        return getName().equals(plugin.getAbilityManager().getPlayerData(player.getUniqueId()).getAbilityName());
    }
}
