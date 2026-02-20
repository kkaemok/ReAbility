package org.kkaemok.reAbility.ability.list;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.kkaemok.reAbility.ReAbility;
import org.kkaemok.reAbility.ability.AbilityBase;
import org.kkaemok.reAbility.ability.AbilityGrade;
import org.kkaemok.reAbility.data.PlayerData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Puppy extends AbilityBase {
    private final ReAbility plugin;
    private final Map<UUID, BukkitTask> tasks = new HashMap<>();
    private final Map<String, Long> detectCooldowns = new HashMap<>();

    public Puppy(ReAbility plugin) {
        this.plugin = plugin;
    }

    @Override public String getName() { return "PUPPY"; }
    @Override public String getDisplayName() { return "강아지"; }
    @Override public AbilityGrade getGrade() { return AbilityGrade.C; }

    @Override
    public String[] getDescription() {
        return new String[]{
                "100칸 이내 플레이어의 움직임을 감지.",
                "주인을 지정할 수 있으며, 주인 곁에 있으면 힘 1 획득."
        };
    }

    @Override
    public void onActivate(Player player) {
        startTask(player);
    }

    @Override
    public void onDeactivate(Player player) {
        BukkitTask task = tasks.remove(player.getUniqueId());
        if (task != null) task.cancel();
        player.removePotionEffect(PotionEffectType.STRENGTH);
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (!isBlockMoved(event)) return;

        Player mover = event.getPlayer();
        for (Player watcher : Bukkit.getOnlinePlayers()) {
            if (watcher.equals(mover)) continue;
            if (!isHasAbility(watcher)) continue;
            if (!watcher.getWorld().equals(mover.getWorld())) continue;

            Location wLoc = watcher.getLocation();
            if (wLoc.distanceSquared(mover.getLocation()) > 10000) continue;

            long now = System.currentTimeMillis();
            String key = watcher.getUniqueId() + ":" + mover.getUniqueId();
            long next = detectCooldowns.getOrDefault(key, 0L);
            if (now < next) continue;

            detectCooldowns.put(key, now + 3000L);
            watcher.sendMessage(Component.text(mover.getName() + "님의 움직임이 감지되었습니다!", NamedTextColor.GRAY));
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

                UUID ownerId = getOwner(player);
                if (ownerId == null) return;

                Player owner = Bukkit.getPlayer(ownerId);
                if (owner == null || !owner.isOnline()) return;
                if (!owner.getWorld().equals(player.getWorld())) return;

                if (owner.getLocation().distanceSquared(player.getLocation()) <= 25) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 40, 0, false, false));
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
        tasks.put(player.getUniqueId(), task);
    }

    private UUID getOwner(Player player) {
        PlayerData data = plugin.getAbilityManager().getPlayerData(player.getUniqueId());
        return data != null ? data.getDogOwnerUuid() : null;
    }

    private boolean isHasAbility(Player player) {
        return getName().equals(plugin.getAbilityManager().getPlayerData(player.getUniqueId()).getAbilityName());
    }

    private boolean isBlockMoved(PlayerMoveEvent event) {
        if (event.getTo() == null) return false;
        if (event.getFrom().getWorld() != event.getTo().getWorld()) return true;
        return event.getFrom().getBlockX() != event.getTo().getBlockX()
                || event.getFrom().getBlockY() != event.getTo().getBlockY()
                || event.getFrom().getBlockZ() != event.getTo().getBlockZ();
    }
}
