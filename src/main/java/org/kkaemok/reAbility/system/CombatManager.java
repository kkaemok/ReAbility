package org.kkaemok.reAbility.system;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.scheduler.BukkitTask;
import org.kkaemok.reAbility.ReAbility;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CombatManager implements Listener {
    private static final int COMBAT_DURATION_SECONDS = 15;
    private static final Component COMBAT_END_ACTIONBAR = Component.text("전투 종료 (안전)", NamedTextColor.GREEN)
            .decorate(TextDecoration.BOLD);

    private final ReAbility plugin;
    private final Map<UUID, Integer> combatTime = new HashMap<>();
    private BukkitTask task;

    public CombatManager(ReAbility plugin) {
        this.plugin = plugin;
        startTask();
    }

    public boolean isInCombat(Player player) {
        Integer remaining = combatTime.get(player.getUniqueId());
        return remaining != null && remaining > 0;
    }

    public void setCombat(Player player) {
        combatTime.put(player.getUniqueId(), COMBAT_DURATION_SECONDS);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player attacker && event.getEntity() instanceof Player victim) {
            setCombat(attacker);
            setCombat(victim);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!isInCombat(player)) return;
        if (player.isOp()) return;

        event.setCancelled(true);
        player.sendMessage("§c§l전투 중에는 명령어를 사용할 수 없습니다!");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (!isInCombat(player)) return;
        if (player.isOp()) return;
        killForCombatLog(player, "§c§l전투 중 서버를 나가서 죽었습니다!");
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        clearCombat(event.getEntity());
    }

    private void startTask() {
        if (task != null) return;
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                UUID uuid = player.getUniqueId();
                Integer remaining = combatTime.get(uuid);
                if (remaining == null || remaining <= 0) continue;

                int next = remaining - 1;
                if (next <= 0) {
                    combatTime.remove(uuid);
                    player.sendActionBar(COMBAT_END_ACTIONBAR);
                    continue;
                }

                combatTime.put(uuid, next);
                Component actionBar = Component.text("전투 중... ", NamedTextColor.RED)
                        .append(Component.text(next + "초 남음", NamedTextColor.WHITE)
                                .decorate(TextDecoration.BOLD));
                player.sendActionBar(actionBar);
            }
        }, 20L, 20L);
    }

    private void killForCombatLog(Player player, String message) {
        player.sendMessage(message);
        clearCombat(player);
        if (player.getHealth() > 0.0) {
            player.setHealth(0.0);
        }
    }

    private void clearCombat(Player player) {
        combatTime.remove(player.getUniqueId());
    }
}