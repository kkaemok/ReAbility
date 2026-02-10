package org.kkaemok.reAbility.system;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitRunnable;
import org.kkaemok.reAbility.ReAbility;
import org.kkaemok.reAbility.ability.AbilityManager;
import org.kkaemok.reAbility.data.PlayerData;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class TeleportManager implements Listener {
    private final ReAbility plugin;
    private final AbilityManager abilityManager;
    private final CombatManager combatManager;

    private final Map<UUID, UUID> tpaRequests = new HashMap<>();
    private final Map<UUID, Long> tpaCooldowns = new HashMap<>();
    private final Map<UUID, Long> rtpCooldowns = new HashMap<>();

    public TeleportManager(ReAbility plugin, AbilityManager abilityManager, CombatManager combatManager) {
        this.plugin = plugin;
        this.abilityManager = abilityManager;
        this.combatManager = combatManager;
    }

    public void requestTPA(Player sender, Player target) {
        if (combatManager.isInCombat(sender)) {
            sender.sendMessage("전투 중에는 TPA를 사용할 수 없습니다.");
            return;
        }

        long cooldown = getTpaCooldown(sender);
        if (isCooldown(sender, tpaCooldowns, cooldown)) {
            sender.sendMessage("TPA 쿨타임입니다. 남은 시간: " + getRemain(sender, tpaCooldowns, cooldown) + "초");
            return;
        }

        tpaRequests.put(target.getUniqueId(), sender.getUniqueId());
        sender.sendMessage(target.getName() + "님에게 TPA 요청을 보냈습니다.");
        target.sendMessage(sender.getName() + "님이 TPA 요청을 보냈습니다: /tpa 수락 또는 /tpa 거절");
    }

    public void acceptTPA(Player target) {
        UUID senderUuid = tpaRequests.remove(target.getUniqueId());
        if (senderUuid == null) {
            target.sendMessage("받은 TPA 요청이 없습니다.");
            return;
        }

        Player sender = Bukkit.getPlayer(senderUuid);
        if (sender == null) return;

        target.sendMessage("TPA 수락됨. 3초 후 이동합니다.");
        sender.sendMessage("TPA 수락됨. 3초 후 이동합니다. 움직이지 마세요.");

        new BukkitRunnable() {
            @Override
            public void run() {
                if (sender.isOnline() && target.isOnline()) {
                    if (combatManager.isInCombat(sender)) {
                        sender.sendMessage("전투가 시작되어 이동이 취소되었습니다.");
                        return;
                    }
                    sender.teleport(target.getLocation());
                    tpaCooldowns.put(sender.getUniqueId(), System.currentTimeMillis());
                    sender.sendMessage("이동 완료.");
                }
            }
        }.runTaskLater(plugin, 60L);
    }

    public void performRTP(Player player) {
        if (combatManager.isInCombat(player)) {
            player.sendMessage("전투 중에는 RTP를 사용할 수 없습니다.");
            return;
        }

        long cooldown = getRtpCooldown(player);
        if (isCooldown(player, rtpCooldowns, cooldown)) {
            player.sendMessage("RTP 쿨타임입니다. 남은 시간: " + getRemain(player, rtpCooldowns, cooldown) + "초");
            return;
        }

        PlayerData data = abilityManager.getPlayerData(player.getUniqueId());
        String abilityName = data.getAbilityName();
        boolean isSpaceRuler = "SPACE_RULER".equals(abilityName);
        int range = isSpaceRuler ? 30000 : 10000;

        Location loc = findSafeLocation(player, range);
        player.teleport(loc);
        rtpCooldowns.put(player.getUniqueId(), System.currentTimeMillis());
        player.sendMessage("랜덤 텔레포트 완료. 범위: " + range);

        if (isSpaceRuler) {
            GameMode prevMode = player.getGameMode();
            player.setGameMode(GameMode.SPECTATOR);
            player.sendMessage("RTP 후 30초 동안 관전자 상태가 됩니다.");

            new BukkitRunnable() {
                @Override
                public void run() {
                    if (player.isOnline()) {
                        player.setGameMode(prevMode);
                        Location safeLoc = player.getWorld().getHighestBlockAt(player.getLocation())
                                .getLocation().add(0.5, 1.1, 0.5);
                        player.teleport(safeLoc);
                        player.sendMessage("관전자 상태가 종료되었습니다.");
                    }
                }
            }.runTaskLater(plugin, 600L);
        }
    }

    private Location findSafeLocation(Player player, int range) {
        Random r = new Random();
        int x = r.nextInt(range * 2) - range;
        int z = r.nextInt(range * 2) - range;
        Block b = player.getWorld().getHighestBlockAt(x, z);

        if (b.getType() == Material.WATER || b.getType() == Material.LAVA) {
            return findSafeLocation(player, range);
        }
        return b.getLocation().add(0.5, 1.1, 0.5);
    }

    private long getTpaCooldown(Player p) {
        String ability = abilityManager.getPlayerData(p.getUniqueId()).getAbilityName();
        return "HOMELOVER".equals(ability) ? 60000L : 180000L;
    }

    private long getRtpCooldown(Player p) {
        String ability = abilityManager.getPlayerData(p.getUniqueId()).getAbilityName();
        return "SPACE_RULER".equals(ability) ? 60000L : 300000L;
    }

    private boolean isCooldown(Player p, Map<UUID, Long> map, long cd) {
        return map.containsKey(p.getUniqueId())
                && System.currentTimeMillis() - map.get(p.getUniqueId()) < cd;
    }

    private long getRemain(Player p, Map<UUID, Long> map, long cd) {
        if (!map.containsKey(p.getUniqueId())) return 0;
        long remain = (map.get(p.getUniqueId()) + cd - System.currentTimeMillis()) / 1000;
        return Math.max(0, remain);
    }
}
