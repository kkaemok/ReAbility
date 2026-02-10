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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Ghost extends AbilityBase {
    private final ReAbility plugin;
    private final Map<UUID, Long> skillCooldown = new HashMap<>();

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
                "몹 처치 시 10초 동안 자유 비행 가능.",
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
        player.setAllowFlight(false);
        showPlayerToAll(player);
    }

    private void startGhostTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (!isHasAbility(player)) continue;
                    if (player.getGameMode() == GameMode.SPECTATOR) continue;

                    boolean nearbyEnemy = false;
                    for (Player other : Bukkit.getOnlinePlayers()) {
                        if (player.equals(other)) continue;
                        if (player.getWorld().equals(other.getWorld())
                                && player.getLocation().distance(other.getLocation()) <= 10) {
                            nearbyEnemy = true;
                            break;
                        }
                    }

                    if (nearbyEnemy) {
                        hidePlayerFromAll(player);
                    } else {
                        showPlayerToAll(player);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 10L);
    }

    private void hidePlayerFromAll(Player ghost) {
        for (Player online : Bukkit.getOnlinePlayers()) {
            online.hidePlayer(plugin, ghost);
        }
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
        player.sendMessage(Component.text("[!] 몹을 처치하여 10초 동안 비행이 가능합니다!", NamedTextColor.AQUA));

        new BukkitRunnable() {
            @Override
            public void run() {
                if (player.isOnline()) {
                    player.setAllowFlight(false);
                    player.sendMessage(Component.text("[!] 비행 시간이 종료되었습니다.", NamedTextColor.GRAY));
                }
            }
        }.runTaskLater(plugin, 200L);
    }

    @Override
    public void onSneakSkill(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType() != Material.DIAMOND || item.getAmount() < 50) return;

        long now = System.currentTimeMillis();
        if (now < skillCooldown.getOrDefault(player.getUniqueId(), 0L)) {
            player.sendMessage(Component.text("쿨타임 중입니다. (5분)", NamedTextColor.RED));
            return;
        }

        item.setAmount(item.getAmount() - 50);
        skillCooldown.put(player.getUniqueId(), now + 300000);

        Bukkit.broadcast(Component.text("[!] 유령 " + player.getName() + "이(가) {고스트}를 사용했습니다!",
                NamedTextColor.GRAY));

        GameMode prevMode = player.getGameMode();
        player.setGameMode(GameMode.SPECTATOR);
        player.playSound(player.getLocation(), Sound.ENTITY_GHAST_WARN, 1.0f, 0.5f);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (player.isOnline()) {
                    player.setGameMode(prevMode);
                    player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 600, 0));
                    player.sendMessage(Component.text("[!] 유령 상태가 종료되었습니다. 힘 1 효과를 획득합니다.",
                            NamedTextColor.GOLD));
                }
            }
        }.runTaskLater(plugin, 400L);
    }

    private boolean isHasAbility(Player player) {
        return getName().equals(plugin.getAbilityManager().getPlayerData(player.getUniqueId()).getAbilityName());
    }
}
