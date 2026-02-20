package org.kkaemok.reAbility.ability.list;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.kkaemok.reAbility.ReAbility;
import org.kkaemok.reAbility.ability.AbilityBase;
import org.kkaemok.reAbility.ability.AbilityGrade;
import org.kkaemok.reAbility.system.SpectatorLockListener;
import org.kkaemok.reAbility.utils.InventoryUtils;

import java.util.*;

public class SpaceRuler extends AbilityBase {
    private final ReAbility plugin;
    private final Map<UUID, Long> rtpCooldown = new HashMap<>();
    private final Map<UUID, Long> skill1Cooldown = new HashMap<>();
    private final Map<UUID, Long> skill2Cooldown = new HashMap<>();
    private final Set<UUID> invinciblePlayers = new HashSet<>();
    private final Random random = new Random();

    public SpaceRuler(ReAbility plugin) {
        this.plugin = plugin;
    }

    @Override public String getName() { return "SPACE_RULER"; }
    @Override public String getDisplayName() { return "공간 지배자"; }
    @Override public AbilityGrade getGrade() { return AbilityGrade.S; }

    @Override
    public String[] getDescription() {
        return new String[]{
                "RTP 쿨타임 1분, 범위 ±30000으로 증가.",
                "RTP 직후 30초 동안 유령(관전자) 상태.",
                "스킬 {공간 이동}: 다이아 100 + 네더라이트 주괴 1 소모",
                "랜덤 플레이어 주변 1000칸 이내로 이동 (쿨타임 3시간)",
                "스킬 {공간 절단}: 다이아 50 소모",
                "30초 동안 무적 + 힘 3 (쿨타임 10분)"
        };
    }

    @Override
    public void onActivate(Player player) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, PotionEffect.INFINITE_DURATION, 0, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, PotionEffect.INFINITE_DURATION, 0, false, false));
    }

    @Override
    public void onDeactivate(Player player) {
        player.removePotionEffect(PotionEffectType.RESISTANCE);
        player.removePotionEffect(PotionEffectType.REGENERATION);
        if (player.getGameMode() == GameMode.SPECTATOR) {
            player.setGameMode(GameMode.SURVIVAL);
        }
        player.removeScoreboardTag(SpectatorLockListener.SPECTATOR_LOCK_TAG);
        invinciblePlayers.remove(player.getUniqueId());
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!isHasAbility(event.getPlayer())) return;

        String command = event.getMessage().toLowerCase();
        if (command.startsWith("/rtp") || command.startsWith("/randomtp")) {
            event.setCancelled(true);
            performCustomRTP(event.getPlayer());
        }
    }

    private void performCustomRTP(Player player) {
        long now = System.currentTimeMillis();
        if (now < rtpCooldown.getOrDefault(player.getUniqueId(), 0L)) {
            long left = (rtpCooldown.get(player.getUniqueId()) - now) / 1000;
            player.sendMessage(Component.text("RTP 쿨타임입니다. 남은 시간: " + left + "초", NamedTextColor.RED));
            return;
        }

        int x = random.nextInt(60001) - 30000;
        int z = random.nextInt(60001) - 30000;
        int y = player.getWorld().getHighestBlockYAt(x, z) + 1;

        Location targetLoc = new Location(player.getWorld(), x, y, z);
        player.teleport(targetLoc);

        rtpCooldown.put(player.getUniqueId(), now + 60000);

        player.setGameMode(GameMode.SPECTATOR);
        player.addScoreboardTag(SpectatorLockListener.SPECTATOR_LOCK_TAG);
        player.sendMessage(Component.text("[!] RTP 후 30초 동안 유령 상태가 됩니다.", NamedTextColor.AQUA));

        new BukkitRunnable() {
            @Override
            public void run() {
                if (player.isOnline()) {
                    player.setGameMode(GameMode.SURVIVAL);
                    player.removeScoreboardTag(SpectatorLockListener.SPECTATOR_LOCK_TAG);
                    Block b = player.getWorld().getHighestBlockAt(player.getLocation());
                    player.teleport(b.getLocation().add(0, 1, 0));
                    player.sendMessage(Component.text("[!] 유령 상태가 종료되었습니다.", NamedTextColor.GRAY));
                }
            }
        }.runTaskLater(plugin, 600L);
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player p && invinciblePlayers.contains(p.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @Override
    public void onSneakSkill(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        long now = System.currentTimeMillis();

        if (item.getType() == Material.DIAMOND
                && InventoryUtils.hasAtLeast(player, Material.DIAMOND, 100)
                && InventoryUtils.hasAtLeast(player, Material.NETHERITE_INGOT, 1)) {
            if (now < skill1Cooldown.getOrDefault(player.getUniqueId(), 0L)) {
                player.sendMessage(Component.text("공간 이동 쿨타임입니다.", NamedTextColor.RED));
                return;
            }

            if (!InventoryUtils.consume(player, Material.DIAMOND, 100)) return;
            if (!InventoryUtils.consume(player, Material.NETHERITE_INGOT, 1)) return;

            skill1Cooldown.put(player.getUniqueId(), now + 10800000);

            List<Player> targets = new ArrayList<>(Bukkit.getOnlinePlayers());
            if (targets.isEmpty()) {
                player.sendMessage(Component.text("이동할 대상이 없습니다.", NamedTextColor.RED));
                return;
            }

            Player target = targets.get(random.nextInt(targets.size()));

            int offsetX = random.nextInt(2001) - 1000;
            int offsetZ = random.nextInt(2001) - 1000;
            Location tLoc = target.getLocation().add(offsetX, 0, offsetZ);
            tLoc.setY(tLoc.getWorld().getHighestBlockYAt(tLoc) + 1);

            player.teleport(tLoc);
            broadcastSkill(player, "{공간 이동}");
            player.sendMessage(Component.text("[!] " + target.getName() + " 주변으로 이동했습니다.", NamedTextColor.LIGHT_PURPLE));
            return;
        }

        if (item.getType() == Material.DIAMOND && item.getAmount() >= 50) {
            if (now < skill2Cooldown.getOrDefault(player.getUniqueId(), 0L)) {
                player.sendMessage(Component.text("공간 절단 쿨타임입니다.", NamedTextColor.RED));
                return;
            }

            if (!InventoryUtils.consume(player, Material.DIAMOND, 50)) return;
            skill2Cooldown.put(player.getUniqueId(), now + 600000);

            invinciblePlayers.add(player.getUniqueId());
            player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 600, 2));

            broadcastSkill(player, "{공간 절단}");
            player.sendMessage(Component.text("[!] 30초 동안 무적 상태가 됩니다.", NamedTextColor.GOLD));

            new BukkitRunnable() {
                @Override
                public void run() {
                    invinciblePlayers.remove(player.getUniqueId());
                    if (player.isOnline()) {
                        player.sendMessage(Component.text("[!] 무적 상태가 종료되었습니다.", NamedTextColor.RED));
                    }
                }
            }.runTaskLater(plugin, 600L);
        }
    }

    private void broadcastSkill(Player player, String skillName) {
        Bukkit.broadcast(Component.text("==========", NamedTextColor.DARK_PURPLE));
        Bukkit.broadcast(Component.text("[S] 공간 지배자 " + player.getName() + "이(가) " + skillName + "을 사용했습니다!",
                NamedTextColor.LIGHT_PURPLE));
        Bukkit.broadcast(Component.text("==========", NamedTextColor.DARK_PURPLE));
    }

    private boolean isHasAbility(Player player) {
        return getName().equals(plugin.getAbilityManager().getPlayerData(player.getUniqueId()).getAbilityName());
    }
}
