package org.kkaemok.reAbility.ability.list;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.kkaemok.reAbility.ReAbility;
import org.kkaemok.reAbility.ability.AbilityBase;
import org.kkaemok.reAbility.ability.AbilityGrade;
import org.kkaemok.reAbility.ability.SkillCost;
import org.kkaemok.reAbility.system.SpectatorLockListener;
import org.kkaemok.reAbility.utils.SkillParticles;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Ghost extends AbilityBase {
    private final ReAbility plugin;
    private final Map<UUID, Long> skillCooldown = new HashMap<>();
    private static final double DEFAULT_NEARBY_RANGE = 10.0;

    public Ghost(ReAbility plugin) {
        this.plugin = plugin;
        startGhostTask();
    }

    @Override public String getName() { return "GHOST"; }
    @Override public String getDisplayName() { return "유령"; }
    @Override public AbilityGrade getGrade() { return AbilityGrade.A; }

    @Override
    public String[] getDescription() {
        return new String[]{
                "24시간 스피드 2 효과 획득.",
                "몹 처치 시 5초 동안 자유 비행 가능.",
                "주변 10칸 내 플레이어가 있으면 투명(갑옷 포함).",
                "스킬 {고스트}: 다이아 50개 소모, 20초 관전자",
                "종료 후 힘 1 효과 획득 (쿨타임 5분)."
        };
    }

    @Override
    public void onActivate(Player player) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, PotionEffect.INFINITE_DURATION, 1, false, false));
    }

    @Override
    public void onDeactivate(Player player) {
        player.removePotionEffect(PotionEffectType.SPEED);
        player.removePotionEffect(PotionEffectType.STRENGTH);
        player.setAllowFlight(false);
        showPlayerToAll(player);
        player.removeScoreboardTag(SpectatorLockListener.SPECTATOR_LOCK_TAG);
        if (player.getGameMode() == GameMode.SPECTATOR) {
            player.setGameMode(GameMode.SURVIVAL);
        }
    }

    private void startGhostTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                double range = plugin.getAbilityConfigManager()
                        .getDouble(getName(), "stats.nearby-range", DEFAULT_NEARBY_RANGE);
                double rangeSquared = range * range;

                for (Player ghost : Bukkit.getOnlinePlayers()) {
                    if (!isHasAbility(ghost)) continue;
                    if (ghost.getGameMode() == GameMode.SPECTATOR) continue;

                    var ghostLoc = ghost.getLocation();

                    for (Player other : Bukkit.getOnlinePlayers()) {
                        if (ghost.equals(other)) continue;
                        if (!ghost.getWorld().equals(other.getWorld())) continue;

                        if (ghostLoc.distanceSquared(other.getLocation()) <= rangeSquared) {
                            other.hidePlayer(plugin, ghost);
                        } else {
                            other.showPlayer(plugin, ghost);
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 10L);
    }

    private void showPlayerToAll(Player ghost) {
        for (Player online : Bukkit.getOnlinePlayers()) {
            online.showPlayer(plugin, ghost);
        }
    }

    @EventHandler
    public void onKill(EntityDeathEvent event) {
        if (event.getEntity().getKiller() == null) return;
        Player player = event.getEntity().getKiller();
        if (!isHasAbility(player)) return;

        player.setAllowFlight(true);
        player.sendMessage(Component.text("[!] 몹을 처치하여 5초 동안 비행이 가능합니다!", NamedTextColor.AQUA));

        new BukkitRunnable() {
            @Override
            public void run() {
                if (player.isOnline()) {
                    player.setAllowFlight(false);
                    player.sendMessage(Component.text("[!] 비행 시간이 종료되었습니다.", NamedTextColor.GRAY));
                }
            }
        }.runTaskLater(plugin, 100L);
    }

    @Override
    public void onSneakSkill(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        SkillCost cost = plugin.getAbilityConfigManager()
                .getSkillCost(getName(), "ghost", Material.DIAMOND, 50);
        if (!cost.matchesHand(item)) return;

        long now = System.currentTimeMillis();
        if (now < skillCooldown.getOrDefault(player.getUniqueId(), 0L)) {
            player.sendMessage(Component.text("쿨타임 중입니다. (5분)", NamedTextColor.RED));
            return;
        }

        if (!cost.consumeFromHand(player)) return;
        long cooldownMs = plugin.getAbilityConfigManager()
                .getLong(getName(), "skills.ghost.cooldown-ms", 300000L);
        int durationTicks = plugin.getAbilityConfigManager()
                .getInt(getName(), "skills.ghost.duration-ticks", 400);
        skillCooldown.put(player.getUniqueId(), now + cooldownMs);

        Bukkit.broadcast(Component.text("[!] 유령 " + player.getName() + "이(가) {고스트}를 사용했습니다!",
                NamedTextColor.GRAY));
        SkillParticles.ghostSkill(player);

        GameMode prevMode = player.getGameMode();
        player.setGameMode(GameMode.SPECTATOR);
        player.addScoreboardTag(SpectatorLockListener.SPECTATOR_LOCK_TAG);
        player.playSound(player.getLocation(), Sound.ENTITY_GHAST_WARN, 1.0f, 0.5f);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (player.isOnline()) {
                    player.setGameMode(prevMode);
                    player.removeScoreboardTag(SpectatorLockListener.SPECTATOR_LOCK_TAG);
                    player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 600, 0));
                    player.sendMessage(Component.text("[!] 유령 상태가 종료되었습니다. 힘 1 효과를 획득합니다.",
                            NamedTextColor.GOLD));
                }
            }
        }.runTaskLater(plugin, durationTicks);
    }

    private boolean isHasAbility(Player player) {
        return getName().equals(plugin.getAbilityManager().getPlayerData(player.getUniqueId()).getAbilityName());
    }
}
