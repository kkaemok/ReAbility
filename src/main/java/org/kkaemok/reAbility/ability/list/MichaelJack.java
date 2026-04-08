package org.kkaemok.reAbility.ability.list;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.kkaemok.reAbility.ReAbility;
import org.kkaemok.reAbility.ability.AbilityBase;
import org.kkaemok.reAbility.ability.AbilityGrade;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MichaelJack extends AbilityBase {
    private final ReAbility plugin;
    private final Map<UUID, Long> lastMovedAt = new HashMap<>();

    public MichaelJack(ReAbility plugin) {
        this.plugin = plugin;
        startMaintenanceTask();
    }

    @Override
    public String getName() {
        return "MICHAEL_JACK";
    }

    @Override
    public String getDisplayName() {
        return "마이클잭";
    }

    @Override
    public AbilityGrade getGrade() {
        return AbilityGrade.D;
    }

    @Override
    public String[] getDescription() {
        return new String[]{
                "뒤로 걸을 때 스피드 5 획득.",
                "구속/경직 효과에 면역.",
                "5초 동안 움직이지 않으면 허기 2를 5초 획득."
        };
    }

    @Override
    public void onActivate(Player player) {
        lastMovedAt.put(player.getUniqueId(), System.currentTimeMillis());
    }

    @Override
    public void onDeactivate(Player player) {
        lastMovedAt.remove(player.getUniqueId());
        player.removePotionEffect(PotionEffectType.SPEED);
        player.removePotionEffect(PotionEffectType.HUNGER);
        player.removePotionEffect(PotionEffectType.SLOWNESS);
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (!isHasAbility(event.getPlayer())) return;
        if (!isPositionChanged(event)) return;

        Player player = event.getPlayer();
        lastMovedAt.put(player.getUniqueId(), System.currentTimeMillis());

        Vector movement = event.getTo().toVector().subtract(event.getFrom().toVector()).setY(0);
        if (movement.lengthSquared() < 0.0001) return;

        Vector look = event.getTo().getDirection().setY(0);
        if (look.lengthSquared() < 0.0001) return;

        double dot = movement.normalize().dot(look.normalize());
        if (dot < -0.35) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 25, 4, false, false));
        }
    }

    private void startMaintenanceTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (!isHasAbility(player)) continue;

                    player.removePotionEffect(PotionEffectType.SLOWNESS);

                    long lastMove = lastMovedAt.getOrDefault(player.getUniqueId(), now);
                    if (now - lastMove >= 5000L) {
                        player.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, 100, 1, false, false));
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 10L);
    }

    private boolean isPositionChanged(PlayerMoveEvent event) {
        if (event.getTo() == null) return false;
        if (event.getFrom().getWorld() != event.getTo().getWorld()) return true;
        return event.getFrom().getX() != event.getTo().getX()
                || event.getFrom().getY() != event.getTo().getY()
                || event.getFrom().getZ() != event.getTo().getZ();
    }

    private boolean isHasAbility(Player player) {
        String ability = plugin.getAbilityManager().getPlayerData(player.getUniqueId()).getAbilityName();
        return getName().equals(ability);
    }
}
