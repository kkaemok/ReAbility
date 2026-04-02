package org.kkaemok.reAbility.ability.list;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.kkaemok.reAbility.ReAbility;
import org.kkaemok.reAbility.ability.AbilityBase;
import org.kkaemok.reAbility.ability.AbilityGrade;
import org.kkaemok.reAbility.ability.SkillCost;
import org.kkaemok.reAbility.guild.GuildData;
import org.kkaemok.reAbility.guild.GuildManager;
import org.kkaemok.reAbility.utils.SkillParticles;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Archangel extends AbilityBase {
    private final ReAbility plugin;
    private final GuildManager guildManager;
    private final Map<UUID, Long> soulCooldown = new HashMap<>();
    private final Map<UUID, Long> spearCooldown = new HashMap<>();
    private final Map<UUID, Long> noAttackUntil = new HashMap<>();
    private final Map<UUID, Location> respawnLocations = new HashMap<>();
    private final Map<UUID, UUID> pendingDonor = new HashMap<>();
    private final Map<UUID, Long> spearWeaknessUntil = new HashMap<>();
    private final Map<UUID, Long> wingbeatActiveUntil = new HashMap<>();
    private final Map<UUID, Boolean> previousAllowFlight = new HashMap<>();
    private final Map<UUID, Boolean> previousFlying = new HashMap<>();
    private final Map<UUID, BukkitTask> soulFlightTasks = new HashMap<>();

    public Archangel(ReAbility plugin) {
        this.plugin = plugin;
        this.guildManager = plugin.getGuildManager();
    }

    @Override public String getName() { return "ARCHANGEL"; }
    @Override public String getDisplayName() { return "대천사"; }
    @Override public AbilityGrade getGrade() { return AbilityGrade.SS; }

    @Override
    public String[] getDescription() {
        return new String[]{
                "플레이어 한 명의 HP 5칸을 빼앗아 원하는 사람에게 부여.",
                "자신의 HP 한 줄 증가, 재생2/저항2 상시.",
                "패시브 {대천사의 영혼}: 사망 시 즉시 부활 (쿨타임 24시간)",
                "부활 후 10초 비행, 공격 불가.",
                "스킬 {심판의 창}: 네더라이트 창 우클릭",
                "10칸 앞 범위 적 HP 10 고정 피해, 처치 시 쿨타임 초기화",
                "스킬 {천사의 날개짓}: 깃털 64개 우클릭, 1분간 바람 효과"
        };
    }

    @Override
    public void onActivate(Player player) {
        applyArchangelHealth(player);
        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, PotionEffect.INFINITE_DURATION, 1, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, PotionEffect.INFINITE_DURATION, 1, false, false));
    }

    @Override
    public void onDeactivate(Player player) {
        UUID uuid = player.getUniqueId();
        endSoulFlightLock(player, uuid);
        pendingDonor.remove(uuid);
        respawnLocations.remove(uuid);
        wingbeatActiveUntil.remove(uuid);

        removeArchangelHealth(player);
        player.removePotionEffect(PotionEffectType.REGENERATION);
        player.removePotionEffect(PotionEffectType.RESISTANCE);
        player.removePotionEffect(PotionEffectType.SPEED);
    }

    @EventHandler
    public void onSelectTarget(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Player angel = event.getPlayer();
        if (!isHasAbility(angel)) return;
        if (!angel.isSneaking()) return;
        if (!(event.getRightClicked() instanceof Player target)) return;
        if (isAttackLocked(angel)) {
            angel.sendMessage(Component.text("부활 후 안정화 중에는 공격형 능력을 사용할 수 없습니다.", NamedTextColor.RED));
            return;
        }
        event.setCancelled(true);

        UUID key = angel.getUniqueId();
        UUID donor = pendingDonor.get(key);
        if (donor == null) {
            pendingDonor.put(key, target.getUniqueId());
            angel.sendMessage(Component.text("[!] 체력을 빼앗을 대상을 선택했습니다.", NamedTextColor.GOLD));
            return;
        }

        Player donorPlayer = Bukkit.getPlayer(donor);
        pendingDonor.remove(key);
        if (donorPlayer == null || !donorPlayer.isOnline()) {
            angel.sendMessage(Component.text("대상이 오프라인입니다.", NamedTextColor.RED));
            return;
        }
        if (donor.equals(target.getUniqueId())) {
            angel.sendMessage(Component.text("체력을 빼앗을 대상과 부여 대상은 서로 달라야 합니다.", NamedTextColor.RED));
            return;
        }

        double donorHealth = donorPlayer.getHealth();
        donorPlayer.setHealth(Math.max(0.0, donorHealth - 10.0));
        if (target.getAttribute(Attribute.MAX_HEALTH) != null) {
            target.setHealth(Math.min(target.getAttribute(Attribute.MAX_HEALTH).getValue(), target.getHealth() + 10.0));
        } else {
            target.setHealth(Math.max(0.0, target.getHealth() + 10.0));
        }
        angel.sendMessage(Component.text("[!] 체력 이전이 완료되었습니다.", NamedTextColor.GREEN));
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (!isHasAbility(player)) return;

        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        if (now < soulCooldown.getOrDefault(uuid, 0L)) return;

        long soulCooldownMs = plugin.getAbilityConfigManager()
                .getLong(getName(), "skills.soul.cooldown-ms", 86400000L);
        soulCooldown.put(uuid, now + soulCooldownMs);
        event.setKeepInventory(true);
        event.getDrops().clear();
        event.setKeepLevel(true);
        event.setDroppedExp(0);
        respawnLocations.put(uuid, player.getLocation());

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            if (player.isDead()) {
                player.spigot().respawn();
            }
        });
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (isHasAbility(player)) {
            Location loc = respawnLocations.remove(player.getUniqueId());
            if (loc != null) {
                event.setRespawnLocation(loc);
                UUID uuid = player.getUniqueId();
                long noAttackMs = plugin.getAbilityConfigManager()
                        .getLong(getName(), "skills.soul.no-attack-ms", 10000L);
                startSoulFlightLock(player, uuid, noAttackMs);
            }
        }

        Long weaknessUntil = spearWeaknessUntil.remove(player.getUniqueId());
        if (weaknessUntil != null && System.currentTimeMillis() < weaknessUntil) {
            int ticks = (int) Math.max(1, (weaknessUntil - System.currentTimeMillis()) / 50);
            player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, ticks, 1, false, false));
        }
    }

    @EventHandler
    public void onNoAttack(EntityDamageByEntityEvent event) {
        Player attacker = getAttacker(event.getDamager());
        if (attacker == null) return;
        if (!isAttackLocked(attacker)) return;
        event.setCancelled(true);
    }

    @EventHandler
    public void onNoAttackProjectile(ProjectileLaunchEvent event) {
        ProjectileSource shooter = event.getEntity().getShooter();
        if (!(shooter instanceof Player attacker)) return;
        if (!isAttackLocked(attacker)) return;
        event.setCancelled(true);
    }

    @EventHandler
    public void onRightClick(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Player player = event.getPlayer();
        if (!isHasAbility(player)) return;
        if (!event.getAction().isRightClick()) return;
        if (shouldIgnoreSneakRightClickBlock(event)) return;

        ItemStack item = player.getInventory().getItemInMainHand();
        long now = System.currentTimeMillis();

        if (isNetheriteSpear(item.getType())) {
            if (isAttackLocked(player)) {
                player.sendMessage(Component.text("부활 후 안정화 중에는 공격형 능력을 사용할 수 없습니다.", NamedTextColor.RED));
                return;
            }
            if (now < spearCooldown.getOrDefault(player.getUniqueId(), 0L)) {
                player.sendMessage(Component.text("심판의 창 쿨타임입니다.", NamedTextColor.RED));
                return;
            }
            double spearRange = plugin.getAbilityConfigManager()
                    .getDouble(getName(), "skills.spear.range", 22.0);
            Player victim = getSpearVictim(player, spearRange);
            if (victim == null) {
                player.sendMessage(Component.text("플레이어가 없습니다.", NamedTextColor.RED));
                return;
            }

            long cooldownMs = plugin.getAbilityConfigManager()
                    .getLong(getName(), "skills.spear.cooldown-ms", 180000L);
            spearCooldown.put(player.getUniqueId(), now + cooldownMs);

            Location strikeLoc = victim.getLocation().clone().add(0, 1.0, 0);
            SkillParticles.archangelSpear(player, strikeLoc);
            boolean killed = false;

            double hp = victim.getHealth();
            if (hp <= 10.0) {
                victim.getInventory().addItem(new ItemStack(Material.GOLDEN_APPLE, 5));
                victim.setHealth(0.0);
                spearWeaknessUntil.put(victim.getUniqueId(), now + 600000);
                killed = true;
            } else {
                victim.setHealth(hp - 10.0);
                victim.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 1200, 0, false, false));
                victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 1200, 3, false, false));
                victim.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 1200, 0, false, false));
                victim.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 1200, 1, false, false));
            }

            if (killed) {
                if (player.getAttribute(Attribute.MAX_HEALTH) != null) {
                    player.setHealth(player.getAttribute(Attribute.MAX_HEALTH).getValue());
                }
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 1200, 2, false, false));
                spearCooldown.remove(player.getUniqueId());
            }
            return;
        }

        SkillCost wingCost = plugin.getAbilityConfigManager()
                .getSkillCost(getName(), "wingbeat", Material.FEATHER, 64);
        if (item.getType() != wingCost.getItem()) return;
        if (isAttackLocked(player)) {
            player.sendMessage(Component.text("부활 후 안정화 중에는 공격형 능력을 사용할 수 없습니다.", NamedTextColor.RED));
            return;
        }

        long wingDurationTicks = plugin.getAbilityConfigManager()
                .getLong(getName(), "skills.wingbeat.duration-ticks", 1200L);
        long wingDurationMs = Math.max(1L, wingDurationTicks) * 50L;
        Long activeUntil = wingbeatActiveUntil.get(player.getUniqueId());
        if (activeUntil != null && now < activeUntil) {
            player.sendMessage(Component.text("천사의 날개짓이 이미 발동 중입니다.", NamedTextColor.RED));
            return;
        }

        if (!wingCost.consumeFromHand(player)) return;
        wingbeatActiveUntil.put(player.getUniqueId(), now + wingDurationMs);
        SkillParticles.archangelWingbeat(player);

        new BukkitRunnable() {
            long tick = 0L;
            final Map<UUID, Location> lastLoc = new HashMap<>();

            @Override
            public void run() {
                if (tick >= wingDurationTicks || !player.isOnline() || player.isDead() || !isHasAbility(player)) {
                    wingbeatActiveUntil.remove(player.getUniqueId());
                    cancel();
                    return;
                }

                for (Player target : Bukkit.getOnlinePlayers()) {
                    if (!target.getWorld().equals(player.getWorld())) continue;
                    if (target.getLocation().distanceSquared(player.getLocation()) > 2025) continue;

                    if (isSameGuild(player, target)) {
                        target.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 40, 2, false, false));
                        target.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 40, 2, false, false));
                        target.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 40, 0, false, false));
                    } else {
                        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 0, false, false));
                        target.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 40, 0, false, false));
                        target.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, 40, 5, false, false));
                        target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 40, 0, false, false));

                        Location prev = lastLoc.get(target.getUniqueId());
                        Location nowLoc = target.getLocation();
                        if (prev != null) {
                            Vector move = nowLoc.toVector().subtract(prev.toVector()).setY(0);
                            if (move.lengthSquared() >= 1.0) {
                                Vector forward = nowLoc.getDirection().setY(0);
                                if (forward.lengthSquared() > 0.0001
                                        && move.clone().normalize().dot(forward.normalize()) > 0.3
                                        && Math.random() < 0.3) {
                                    target.setVelocity(move.normalize().multiply(-2.0));
                                }
                            }
                        }
                        lastLoc.put(target.getUniqueId(), nowLoc);
                    }
                }

                if (tick % 300 == 0 && tick > 0) {
                    for (Player target : Bukkit.getOnlinePlayers()) {
                        if (isSameGuild(player, target)) continue;
                        if (!target.getWorld().equals(player.getWorld())) continue;
                        if (target.getLocation().distanceSquared(player.getLocation()) > 2025) continue;
                        target.damage(50.0, player);
                    }
                }
                tick += 20;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void applyArchangelHealth(Player player) {
        AttributeInstance attr = player.getAttribute(Attribute.MAX_HEALTH);
        if (attr == null) return;
        removeArchangelHealth(player);
        attr.addModifier(new AttributeModifier(getArchangelHealthKey(), 20.0, AttributeModifier.Operation.ADD_NUMBER));
    }

    private void removeArchangelHealth(Player player) {
        AttributeInstance attr = player.getAttribute(Attribute.MAX_HEALTH);
        if (attr == null) return;
        NamespacedKey key = getArchangelHealthKey();
        attr.getModifiers().stream()
                .filter(mod -> mod.getKey().equals(key))
                .forEach(attr::removeModifier);
    }

    private NamespacedKey getArchangelHealthKey() {
        return new NamespacedKey(plugin, "archangel-health");
    }

    private boolean isAttackLocked(Player player) {
        Long until = noAttackUntil.get(player.getUniqueId());
        if (until == null) return false;
        if (System.currentTimeMillis() > until) {
            noAttackUntil.remove(player.getUniqueId());
            return false;
        }
        return true;
    }

    private void startSoulFlightLock(Player player, UUID uuid, long lockMillis) {
        long effectiveLockMs = Math.max(50L, lockMillis);
        long lockEnd = System.currentTimeMillis() + effectiveLockMs;
        long lockTicks = Math.max(1L, effectiveLockMs / 50L);

        previousAllowFlight.put(uuid, player.getAllowFlight());
        previousFlying.put(uuid, player.isFlying());
        noAttackUntil.put(uuid, lockEnd);
        cancelSoulFlightTask(uuid);

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || player.isDead() || !isHasAbility(player)) {
                    noAttackUntil.remove(uuid);
                    cancelSoulFlightTask(uuid);
                    return;
                }

                Long until = noAttackUntil.get(uuid);
                if (until == null || System.currentTimeMillis() > until) {
                    cancelSoulFlightTask(uuid);
                    return;
                }

                if (!player.getAllowFlight()) {
                    player.setAllowFlight(true);
                }
                if (!player.isFlying()) {
                    player.setFlying(true);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
        soulFlightTasks.put(uuid, task);

        Bukkit.getScheduler().runTaskLater(plugin, () -> endSoulFlightLock(player, uuid), lockTicks);
    }

    private void endSoulFlightLock(Player player, UUID uuid) {
        noAttackUntil.remove(uuid);
        cancelSoulFlightTask(uuid);

        Boolean restoreAllow = previousAllowFlight.remove(uuid);
        Boolean restoreFlying = previousFlying.remove(uuid);
        if (!player.isOnline()) return;

        boolean creativeLike = player.getGameMode() == GameMode.CREATIVE
                || player.getGameMode() == GameMode.SPECTATOR;
        if (creativeLike) return;

        player.setAllowFlight(restoreAllow != null && restoreAllow);
        player.setFlying(restoreFlying != null && restoreFlying && player.getAllowFlight());
    }

    private void cancelSoulFlightTask(UUID uuid) {
        BukkitTask task = soulFlightTasks.remove(uuid);
        if (task != null) {
            task.cancel();
        }
    }

    private Player getAttacker(Entity damager) {
        if (damager instanceof Player player) return player;
        if (damager instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Player player) return player;
        }
        return null;
    }

    private Player getSpearVictim(Player caster, double range) {
        RayTraceResult trace = caster.getWorld().rayTraceEntities(
                caster.getEyeLocation(),
                caster.getEyeLocation().getDirection().normalize(),
                Math.max(1.0, range),
                1.2,
                entity -> entity instanceof Player target
                        && !target.equals(caster)
                        && !target.isDead()
                        && target.getGameMode() != GameMode.SPECTATOR
                        && caster.hasLineOfSight(target)
                        && !isSameGuild(caster, target)
        );
        if (trace == null || !(trace.getHitEntity() instanceof Player target)) {
            return null;
        }
        return target;
    }

    private boolean isNetheriteSpear(Material type) {
        if (type == null || type.isAir()) return false;
        Material spearMat = getConfiguredSpearMaterial();
        if (type == spearMat) return true;
        return type == Material.TRIDENT && "NETHERITE_SPEAR".equals(spearMat.name());
    }

    private Material getConfiguredSpearMaterial() {
        Material defaultMat = Material.matchMaterial("NETHERITE_SPEAR");
        if (defaultMat == null) {
            defaultMat = Material.TRIDENT;
        }
        return plugin.getAbilityConfigManager().getMaterial(getName(), "skills.spear.item", defaultMat);
    }

    private boolean isSameGuild(Player p1, Player p2) {
        if (p1.equals(p2)) return true;
        GuildData g1 = guildManager.getGuildByMember(p1.getUniqueId());
        GuildData g2 = guildManager.getGuildByMember(p2.getUniqueId());
        return g1 != null && g2 != null && g1.name.equalsIgnoreCase(g2.name);
    }

    private boolean isHasAbility(Player player) {
        return getName().equals(plugin.getAbilityManager().getPlayerData(player.getUniqueId()).getAbilityName());
    }
}
