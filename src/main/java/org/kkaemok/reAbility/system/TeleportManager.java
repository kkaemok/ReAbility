package org.kkaemok.reAbility.system;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitRunnable;
import org.kkaemok.reAbility.ReAbility;
import org.kkaemok.reAbility.ability.AbilityManager;
import org.kkaemok.reAbility.data.PlayerData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class TeleportManager implements Listener {
    private static final int RTP_MAX_ATTEMPTS = 64;
    private static final int COUNTDOWN_SECONDS = 3;

    private final ReAbility plugin;
    private final AbilityManager abilityManager;
    private final CombatManager combatManager;
    private final Random random = new Random();

    // key: target UUID
    private final Map<UUID, TpaRequest> tpaRequests = new HashMap<>();
    // key: requester UUID, value: last successful TPA time
    private final Map<UUID, Long> tpaCooldowns = new HashMap<>();
    // key: player UUID, value: last successful RTP time
    private final Map<UUID, Long> rtpCooldowns = new HashMap<>();

    private enum TpaType {
        TO_TARGET,
        HERE
    }

    private record TpaRequest(UUID sender, TpaType type, long expiresAt) {
        private boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }

    public TeleportManager(ReAbility plugin, AbilityManager abilityManager, CombatManager combatManager) {
        this.plugin = plugin;
        this.abilityManager = abilityManager;
        this.combatManager = combatManager;
    }

    public void requestTPA(Player sender, Player target) {
        requestTPA(sender, target, TpaType.TO_TARGET);
    }

    public void requestTPAHere(Player sender, Player target) {
        requestTPA(sender, target, TpaType.HERE);
    }

    private void requestTPA(Player sender, Player target, TpaType type) {
        if (sender.equals(target)) {
            sender.sendMessage("자기 자신에게는 요청할 수 없습니다.");
            return;
        }
        if (combatManager.isInCombat(sender)) {
            sender.sendMessage("전투 중에는 TPA를 사용할 수 없습니다.");
            return;
        }

        long cooldown = getTpaCooldown(sender);
        if (isCooldown(sender, tpaCooldowns, cooldown)) {
            sender.sendMessage("TPA 쿨타임입니다. 남은 시간: " + getRemain(sender, tpaCooldowns, cooldown) + "초");
            return;
        }

        long timeoutMs = getTpaRequestTimeoutMs();
        tpaRequests.put(target.getUniqueId(), new TpaRequest(sender.getUniqueId(), type, System.currentTimeMillis() + timeoutMs));

        if (type == TpaType.TO_TARGET) {
            sender.sendMessage(target.getName() + "님에게 TPA 요청을 보냈습니다.");
            target.sendMessage(sender.getName() + "님이 TPA 요청을 보냈습니다. /tpaccept " + sender.getName()
                    + " 또는 /tpadeny " + sender.getName());
        } else {
            sender.sendMessage(target.getName() + "님에게 TPHere 요청을 보냈습니다.");
            target.sendMessage(sender.getName() + "님이 TPHere 요청을 보냈습니다. /tpaccept " + sender.getName()
                    + " 또는 /tpadeny " + sender.getName());
        }
    }

    public void acceptTPA(Player target, Player requester) {
        TpaRequest request = tpaRequests.get(target.getUniqueId());
        if (request == null || !request.sender.equals(requester.getUniqueId())) {
            target.sendMessage("받은 TPA 요청이 없습니다.");
            return;
        }
        if (request.isExpired()) {
            tpaRequests.remove(target.getUniqueId());
            target.sendMessage("TPA 요청이 만료되었습니다.");
            return;
        }

        Player sender = Bukkit.getPlayer(request.sender);
        if (sender == null) {
            tpaRequests.remove(target.getUniqueId());
            target.sendMessage("요청자가 오프라인입니다.");
            return;
        }

        tpaRequests.remove(target.getUniqueId());

        Player mover = request.type == TpaType.TO_TARGET ? sender : target;
        Player destinationPlayer = request.type == TpaType.TO_TARGET ? target : sender;
        Location destination = destinationPlayer.getLocation().clone();

        sender.sendMessage("TPA가 수락되었습니다. 3초 후 이동합니다.");
        target.sendMessage("TPA를 수락했습니다. 3초 후 이동합니다.");

        startCountdown(
                List.of(sender, target),
                "TPA 이동",
                () -> {
                    if (!sender.isOnline() || !target.isOnline()) return false;
                    if (combatManager.isInCombat(mover)) {
                        mover.sendMessage("전투가 시작되어 이동이 취소되었습니다.");
                        return false;
                    }
                    return true;
                },
                () -> {
                    mover.teleport(destination);
                    tpaCooldowns.put(sender.getUniqueId(), System.currentTimeMillis());
                    sender.sendMessage("이동 완료.");
                    target.sendMessage("이동 완료.");
                }
        );
    }

    public void denyTPA(Player target, Player requester) {
        TpaRequest request = tpaRequests.get(target.getUniqueId());
        if (request == null || !request.sender.equals(requester.getUniqueId())) {
            target.sendMessage("받은 TPA 요청이 없습니다.");
            return;
        }
        if (request.isExpired()) {
            tpaRequests.remove(target.getUniqueId());
            target.sendMessage("TPA 요청이 만료되었습니다.");
            return;
        }

        tpaRequests.remove(target.getUniqueId());
        target.sendMessage("TPA 요청을 거절했습니다.");
        requester.sendMessage(target.getName() + "님이 TPA 요청을 거절했습니다.");
    }

    public void cancelTPA(Player sender, Player target) {
        if (target != null) {
            TpaRequest request = tpaRequests.get(target.getUniqueId());
            if (request == null || !request.sender.equals(sender.getUniqueId()) || request.isExpired()) {
                if (request != null && request.isExpired()) {
                    tpaRequests.remove(target.getUniqueId());
                }
                sender.sendMessage("취소할 TPA 요청이 없습니다.");
                return;
            }
            tpaRequests.remove(target.getUniqueId());
            sender.sendMessage(target.getName() + "님에게 보낸 TPA 요청을 취소했습니다.");
            target.sendMessage(sender.getName() + "님이 TPA 요청을 취소했습니다.");
            return;
        }

        UUID senderId = sender.getUniqueId();
        UUID removedTargetId = null;
        Iterator<Map.Entry<UUID, TpaRequest>> iterator = tpaRequests.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, TpaRequest> entry = iterator.next();
            TpaRequest request = entry.getValue();
            if (request.isExpired()) {
                iterator.remove();
                continue;
            }
            if (!request.sender.equals(senderId)) continue;
            removedTargetId = entry.getKey();
            iterator.remove();
            break;
        }

        if (removedTargetId == null) {
            sender.sendMessage("취소할 TPA 요청이 없습니다.");
            return;
        }

        Player targetPlayer = Bukkit.getPlayer(removedTargetId);
        if (targetPlayer != null) {
            sender.sendMessage(targetPlayer.getName() + "님에게 보낸 TPA 요청을 취소했습니다.");
            targetPlayer.sendMessage(sender.getName() + "님이 TPA 요청을 취소했습니다.");
        } else {
            sender.sendMessage("TPA 요청을 취소했습니다.");
        }
    }

    public List<String> getPendingRequesters(Player target) {
        TpaRequest request = tpaRequests.get(target.getUniqueId());
        if (request == null) return List.of();
        if (request.isExpired()) {
            tpaRequests.remove(target.getUniqueId());
            return List.of();
        }

        Player sender = Bukkit.getPlayer(request.sender);
        if (sender == null) return List.of();
        return List.of(sender.getName());
    }

    public List<String> getPendingTargets(Player sender) {
        UUID senderId = sender.getUniqueId();
        List<String> targets = new ArrayList<>();
        Iterator<Map.Entry<UUID, TpaRequest>> iterator = tpaRequests.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, TpaRequest> entry = iterator.next();
            TpaRequest request = entry.getValue();
            if (request.isExpired()) {
                iterator.remove();
                continue;
            }
            if (!request.sender.equals(senderId)) continue;
            Player target = Bukkit.getPlayer(entry.getKey());
            if (target != null) {
                targets.add(target.getName());
            }
        }
        return targets;
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

        Location destination = findSafeLocation(player, range);
        if (destination == null) {
            player.sendMessage("안전한 위치를 찾지 못했습니다. 잠시 후 다시 시도해주세요.");
            return;
        }

        player.sendMessage("RTP를 시작합니다. 3초 후 이동합니다.");
        startCountdown(
                List.of(player),
                "RTP 이동",
                () -> {
                    if (!player.isOnline()) return false;
                    if (combatManager.isInCombat(player)) {
                        player.sendMessage("전투가 시작되어 RTP가 취소되었습니다.");
                        return false;
                    }
                    return true;
                },
                () -> {
                    player.teleport(destination);
                    rtpCooldowns.put(player.getUniqueId(), System.currentTimeMillis());
                    player.sendMessage("랜덤 텔레포트 완료. 범위: " + range);

                    if (isSpaceRuler) {
                        startSpaceRulerSpectatorPhase(player);
                    }
                }
        );
    }

    private void startSpaceRulerSpectatorPhase(Player player) {
        GameMode prevMode = player.getGameMode();
        player.setGameMode(GameMode.SPECTATOR);
        player.sendMessage("RTP 후 30초 동안 관전자 상태입니다.");

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) return;

                player.setGameMode(prevMode);
                Location safeLoc = player.getWorld().getHighestBlockAt(player.getLocation()).getLocation().add(0.5, 1.1, 0.5);
                Chunk chunk = safeLoc.getChunk();
                if (!chunk.isLoaded()) {
                    chunk.load(true);
                }
                player.teleport(safeLoc);
                player.sendMessage("관전자 상태가 종료되었습니다.");
            }
        }.runTaskLater(plugin, 600L);
    }

    private Location findSafeLocation(Player player, int range) {
        var world = player.getWorld();
        for (int i = 0; i < RTP_MAX_ATTEMPTS; i++) {
            int x = random.nextInt((range * 2) + 1) - range;
            int z = random.nextInt((range * 2) + 1) - range;

            Chunk chunk = world.getChunkAt(x >> 4, z >> 4);
            if (!chunk.isLoaded()) {
                chunk.load(true);
            }

            Block ground = world.getHighestBlockAt(x, z);
            Material groundType = ground.getType();
            if (groundType == Material.WATER || groundType == Material.LAVA) {
                continue;
            }

            Location spawn = ground.getLocation().add(0.5, 1.1, 0.5);
            if (!spawn.getBlock().isPassable()) continue;
            if (!spawn.clone().add(0, 1, 0).getBlock().isPassable()) continue;
            return spawn;
        }
        return null;
    }

    private void startCountdown(List<Player> players, String title, CheckCondition condition, Runnable onComplete) {
        new BukkitRunnable() {
            private int remaining = COUNTDOWN_SECONDS;

            @Override
            public void run() {
                if (!condition.test()) {
                    cancel();
                    return;
                }

                if (remaining > 0) {
                    sendCountdownTick(players, title, remaining);
                    remaining--;
                    return;
                }

                clearActionBar(players);
                onComplete.run();
                cancel();
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void sendCountdownTick(List<Player> players, String title, int seconds) {
        for (Player player : players) {
            if (player == null || !player.isOnline()) continue;
            player.sendActionBar(Component.text(title + ": " + seconds + "초", NamedTextColor.GOLD));
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.6f);
        }
    }

    private void clearActionBar(List<Player> players) {
        for (Player player : players) {
            if (player == null || !player.isOnline()) continue;
            player.sendActionBar(Component.empty());
        }
    }

    private long getTpaCooldown(Player player) {
        String ability = abilityManager.getPlayerData(player.getUniqueId()).getAbilityName();
        long defaultSeconds = plugin.getConfig().getLong("teleport-settings.tpa-cooldown-default", 180L);
        long homeLoverSeconds = plugin.getConfig().getLong("teleport-settings.tpa-cooldown-homelover", 60L);
        long seconds = "HOMELOVER".equals(ability) ? homeLoverSeconds : defaultSeconds;
        return Math.max(0L, seconds) * 1000L;
    }

    private long getRtpCooldown(Player player) {
        String ability = abilityManager.getPlayerData(player.getUniqueId()).getAbilityName();
        long defaultSeconds = plugin.getConfig().getLong("teleport-settings.rtp-cooldown-default", 600L);
        long spaceSeconds = plugin.getConfig().getLong("teleport-settings.rtp-cooldown-space-lord", 60L);
        long seconds = "SPACE_RULER".equals(ability) ? spaceSeconds : defaultSeconds;
        return Math.max(0L, seconds) * 1000L;
    }

    private long getTpaRequestTimeoutMs() {
        long seconds = plugin.getConfig().getLong("teleport-settings.tpa-request-timeout-seconds", 60L);
        return Math.max(5L, seconds) * 1000L;
    }

    private boolean isCooldown(Player player, Map<UUID, Long> map, long cooldownMs) {
        Long last = map.get(player.getUniqueId());
        return last != null && System.currentTimeMillis() - last < cooldownMs;
    }

    private long getRemain(Player player, Map<UUID, Long> map, long cooldownMs) {
        Long last = map.get(player.getUniqueId());
        if (last == null) return 0L;
        long remain = (last + cooldownMs - System.currentTimeMillis()) / 1000L;
        return Math.max(0L, remain);
    }

    @FunctionalInterface
    private interface CheckCondition {
        boolean test();
    }
}
