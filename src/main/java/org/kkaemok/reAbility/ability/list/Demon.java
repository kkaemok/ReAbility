package org.kkaemok.reAbility.ability.list;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.entity.Trident;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.kkaemok.reAbility.ReAbility;
import org.kkaemok.reAbility.ability.AbilityBase;
import org.kkaemok.reAbility.ability.AbilityGrade;
import org.kkaemok.reAbility.guild.GuildData;
import org.kkaemok.reAbility.guild.GuildManager;
import org.kkaemok.reAbility.utils.SkillParticles;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class Demon extends AbilityBase {
    private static final long HELL_RETURN_DELAY_TICKS = 2400L; // 2 minutes
    private static final int NETHER_SAFE_RANGE = 3000;
    private static final int NETHER_SAFE_ATTEMPTS = 96;
    private static final int NETHER_ROOF_Y_LIMIT = 120;

    private final ReAbility plugin;
    private final GuildManager guildManager;
    private final Random random = new Random();
    private final Map<UUID, Long> hellCooldown = new HashMap<>();
    private final Map<UUID, Long> staffCooldown = new HashMap<>();
    private final Map<UUID, Long> invincibleUntil = new HashMap<>();
    private final Map<UUID, Boolean> prevAllowFlight = new HashMap<>();

    public Demon(ReAbility plugin) {
        this.plugin = plugin;
        this.guildManager = plugin.getGuildManager();
        startNetherTask();
    }

    @Override public String getName() { return "DEMON"; }
    @Override public String getDisplayName() { return "악마"; }
    @Override public AbilityGrade getGrade() { return AbilityGrade.S; }

    @Override
    public String[] getDescription() {
        return new String[]{
                "지옥에 있을 시 힘 4, 저항 2, 화염저항 획득.",
                "스킬 {지옥}: 네더라이트 주괴로 타격 시 자신/상대 지옥 이동",
                "2분 후 오버월드 원위치 복귀, 상대 구속 3, 나약 3 (3초) + 자신 30초 비행 (쿨타임 1시간)",
                "스킬 {사탄의 지팡이}: 삼지창 명중 시 주변 3칸 번개 25회",
                "3초 무적 + 이후 재생2/스피드3 30초 (쿨타임 1분)"
        };
    }

    @Override
    public void onDeactivate(Player player) {
        invincibleUntil.remove(player.getUniqueId());
        player.removePotionEffect(PotionEffectType.REGENERATION);
        player.removePotionEffect(PotionEffectType.SPEED);
        Boolean prev = prevAllowFlight.remove(player.getUniqueId());
        if (prev != null && !prev && player.getGameMode() == GameMode.SURVIVAL) {
            player.setAllowFlight(false);
            player.setFlying(false);
        }
    }

    private void startNetherTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (!isHasAbility(player)) continue;
                    if (player.getWorld().getEnvironment() == World.Environment.NETHER) {
                        player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 40, 3, false, false));
                        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 40, 1, false, false));
                        player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 40, 0, false, false));
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    @EventHandler
    public void onHellStrike(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        if (!isHasAbility(player)) return;
        if (!(event.getEntity() instanceof Player target)) return;
        if (isSameGuild(player, target)) return;
        if (player.getInventory().getItemInMainHand().getType() != Material.NETHERITE_INGOT) return;

        long now = System.currentTimeMillis();
        if (now < hellCooldown.getOrDefault(player.getUniqueId(), 0L)) {
            player.sendMessage(Component.text("지옥 쿨타임입니다.", NamedTextColor.RED));
            return;
        }

        World nether = player.getWorld().getEnvironment() == World.Environment.NETHER
                ? player.getWorld()
                : Bukkit.getWorlds().stream()
                .filter(w -> w.getEnvironment() == World.Environment.NETHER)
                .findFirst().orElse(null);

        if (nether == null) {
            player.sendMessage(Component.text("지옥 월드를 찾을 수 없습니다.", NamedTextColor.RED));
            return;
        }

        hellCooldown.put(player.getUniqueId(), now + 3600000);
        Location base = player.getLocation().clone();
        Location targetBase = target.getLocation().clone();
        SkillParticles.demonHell(base);
        SkillParticles.demonHell(targetBase);
        Location dest = findSafeNetherLocation(nether, base);
        if (dest == null) {
            player.sendMessage(Component.text("네더 안전 위치를 찾지 못했습니다. 잠시 후 다시 시도해주세요.", NamedTextColor.RED));
            return;
        }

        player.teleport(dest);
        target.teleport(dest);
        SkillParticles.demonHell(dest);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            returnToOriginalLocation(player, base);
            returnToOriginalLocation(target, targetBase);
        }, HELL_RETURN_DELAY_TICKS);

        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 2, false, false));
        target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 60, 2, false, false));

        if (!player.getAllowFlight()) {
            prevAllowFlight.put(player.getUniqueId(), false);
        }
        player.setAllowFlight(true);
        player.setFlying(true);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Boolean prev = prevAllowFlight.remove(player.getUniqueId());
            if (prev != null && !prev && player.isOnline() && player.getGameMode() == GameMode.SURVIVAL) {
                player.setFlying(false);
                player.setAllowFlight(false);
            }
        }, 600L);
    }

    @EventHandler
    public void onStaffHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Trident trident)) return;
        if (!(trident.getShooter() instanceof Player player)) return;
        if (!isHasAbility(player)) return;
        if (!(event.getHitEntity() instanceof Player target)) return;
        if (isSameGuild(player, target)) return;

        long now = System.currentTimeMillis();
        if (now < staffCooldown.getOrDefault(player.getUniqueId(), 0L)) return;

        staffCooldown.put(player.getUniqueId(), now + 60000);
        invincibleUntil.put(player.getUniqueId(), now + 3000);

        Location center = target.getLocation();
        SkillParticles.demonStaff(center);
        new BukkitRunnable() {
            int count = 0;

            @Override
            public void run() {
                if (count >= 25) {
                    cancel();
                    return;
                }
                double offsetX = (Math.random() * 6) - 3;
                double offsetZ = (Math.random() * 6) - 3;
                Location strike = center.clone().add(offsetX, 0, offsetZ);
                center.getWorld().strikeLightning(strike);
                count++;
            }
        }.runTaskTimer(plugin, 0L, 2L);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            invincibleUntil.remove(player.getUniqueId());
            player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 600, 1, false, false));
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 600, 2, false, false));
        }, 60L);
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        Long until = invincibleUntil.get(player.getUniqueId());
        if (until == null) return;
        if (System.currentTimeMillis() > until) {
            invincibleUntil.remove(player.getUniqueId());
            return;
        }
        event.setCancelled(true);
    }

    private void returnToOriginalLocation(Player player, Location origin) {
        if (origin == null || origin.getWorld() == null) return;
        if (!player.isOnline() || player.isDead()) return;
        Chunk chunk = origin.getChunk();
        if (!chunk.isLoaded()) {
            chunk.load(true);
        }
        player.teleport(origin);
    }

    private Location findSafeNetherLocation(World nether, Location center) {
        int baseX = center.getBlockX();
        int baseZ = center.getBlockZ();

        for (int i = 0; i < NETHER_SAFE_ATTEMPTS; i++) {
            int x = baseX + random.nextInt((NETHER_SAFE_RANGE * 2) + 1) - NETHER_SAFE_RANGE;
            int z = baseZ + random.nextInt((NETHER_SAFE_RANGE * 2) + 1) - NETHER_SAFE_RANGE;

            Chunk chunk = nether.getChunkAt(x >> 4, z >> 4);
            if (!chunk.isLoaded()) {
                chunk.load(true);
            }

            Block ground = nether.getHighestBlockAt(x, z);
            if (ground.getY() >= NETHER_ROOF_Y_LIMIT) continue;
            if (isUnsafeNetherGround(ground.getType())) continue;

            Location spawn = ground.getLocation().add(0.5, 1.1, 0.5);
            if (!spawn.getBlock().isPassable()) continue;
            if (!spawn.clone().add(0, 1, 0).getBlock().isPassable()) continue;
            return spawn;
        }
        return null;
    }

    private boolean isUnsafeNetherGround(Material type) {
        return switch (type) {
            case LAVA, WATER, FIRE, SOUL_FIRE, MAGMA_BLOCK, CAMPFIRE, SOUL_CAMPFIRE, BEDROCK -> true;
            default -> false;
        };
    }

    private boolean isHasAbility(Player player) {
        return getName().equals(plugin.getAbilityManager().getPlayerData(player.getUniqueId()).getAbilityName());
    }

    private boolean isSameGuild(Player p1, Player p2) {
        if (p1.equals(p2)) return true;
        GuildData g1 = guildManager.getGuildByMember(p1.getUniqueId());
        GuildData g2 = guildManager.getGuildByMember(p2.getUniqueId());
        return g1 != null && g2 != null && g1.name.equalsIgnoreCase(g2.name);
    }
}
