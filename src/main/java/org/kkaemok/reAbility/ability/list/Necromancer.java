package org.kkaemok.reAbility.ability.list;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.WitherSkull;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.kkaemok.reAbility.ReAbility;
import org.kkaemok.reAbility.ability.AbilityBase;
import org.kkaemok.reAbility.ability.AbilityGrade;
import org.kkaemok.reAbility.data.PlayerData;
import org.kkaemok.reAbility.guild.GuildData;
import org.kkaemok.reAbility.utils.InventoryUtils;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class Necromancer extends AbilityBase {
    private static final long SOUL_TTL_MS = 60L * 60L * 1000L;
    private static final long DEATH_INSTINCT_WINDOW_MS = 3L * 60L * 1000L;
    private static final long WARDEN_WINDOW_MS = 10L * 60L * 1000L;
    private static final long WARDEN_WINDOW_ENHANCED_MS = 20L * 60L * 1000L;
    private static final long SCULK_COOLDOWN_MS = 5L * 60L * 60L * 1000L;
    private static final long DEATH_FLOWER_COOLDOWN_MS = 3L * 60L * 1000L;
    private static final long DEATH_FLOWER_INVINCIBLE_MS = 10L * 1000L;
    private static final long SOUL_KNIGHT_INVINCIBLE_MS = 60L * 1000L;
    private static final long SONIC_COOLDOWN_MS = 15L * 1000L;
    private static final long PAIN_TIME_INTERVAL_MS = 24L * 60L * 60L * 1000L;
    private static final long HEALTH_LEECH_RETURN_MS = 24L * 60L * 60L * 1000L;
    private static final double SOUL_KNIGHT_BONUS_HEALTH = 20.0;

    private static final long STAGE_ONE_MS = 60L * 60L * 1000L;
    private static final long STAGE_TWO_MS = 2L * 60L * 60L * 1000L;
    private static final long STAGE_THREE_MS = 5L * 60L * 60L * 1000L;
    private static final long STAGE_FOUR_MS = 24L * 60L * 60L * 1000L;
    private static final long STAGE_FIVE_MS = 44L * 60L * 60L * 1000L;

    private final ReAbility plugin;
    private final Random random = new Random();

    private final Map<UUID, Long> invincibleUntil = new HashMap<>();
    private final Map<UUID, Long> deathFlowerCooldown = new HashMap<>();
    private final Map<UUID, Long> wardenSoulUntil = new HashMap<>();
    private final Map<UUID, Long> sculkCooldown = new HashMap<>();
    private final Map<UUID, Long> sonicCooldown = new HashMap<>();
    private final Map<UUID, Long> lastDeathTime = new HashMap<>();
    private final Map<UUID, Location> lastDeathLocation = new HashMap<>();
    private final Map<UUID, Long> apocalypseStart = new HashMap<>();
    private final Map<UUID, Double> preWardenMaxHealth = new HashMap<>();
    private final Map<UUID, Long> wardenFormUntil = new HashMap<>();
    private final Map<UUID, Double> preSoulKnightMaxHealth = new HashMap<>();
    private final Map<UUID, Long> soulKnightHealthUntil = new HashMap<>();
    private final Map<UUID, Map<UUID, LeechState>> deathFlowerLeech = new HashMap<>();
    private final Map<UUID, Double> blackHpPool = new HashMap<>();
    private final Map<UUID, Long> painTimeNextUse = new HashMap<>();

    private final Map<UUID, UUID> projectileToCast = new HashMap<>();
    private final Map<UUID, JudgementCast> judgementCasts = new HashMap<>();

    private BukkitTask tickerTask;
    private boolean eternalNightForced;

    private static class LeechState {
        private int stolenSlots;
        private long lastDrainAt;
        private long expiresAt;
    }

    private static class JudgementCast {
        private final UUID ownerId;
        private int remainingProjectiles;
        private int hits;
        private final boolean fallbackOnMiss;

        private JudgementCast(UUID ownerId, int remainingProjectiles, boolean fallbackOnMiss) {
            this.ownerId = ownerId;
            this.remainingProjectiles = remainingProjectiles;
            this.fallbackOnMiss = fallbackOnMiss;
        }
    }

    private record NecromancerCarrier(Player player, int stage, double distanceSq) {}

    public Necromancer(ReAbility plugin) {
        this.plugin = plugin;
        startTicker();
    }

    @Override
    public String getName() {
        return "NECROMANCER";
    }

    @Override
    public String getDisplayName() {
        return "네크로맨서";
    }

    @Override
    public AbilityGrade getGrade() {
        return AbilityGrade.S_PLUS;
    }

    @Override
    public String[] getDescription() {
        return new String[]{
                "몹 처치 시 영혼을 획득하고 1시간이 지나면 사라집니다.",
                "/영혼 상점에서 영혼을 투자해 스킬을 해금할 수 있습니다.",
                "소울 나이트, 죽음 본능, 스컬크 소울, 죽음의 꽃, 세계의 종말을 사용합니다."
        };
    }

    @Override
    public void onActivate(Player player) {
        PlayerData data = plugin.getAbilityManager().getPlayerData(player.getUniqueId());
        ensureApocalypseStart(player, data);
    }

    @Override
    public void onDeactivate(Player player) {
        UUID uuid = player.getUniqueId();
        endWardenForm(player);
        restoreSoulKnightMaxHealth(player);

        invincibleUntil.remove(uuid);
        deathFlowerCooldown.remove(uuid);
        wardenSoulUntil.remove(uuid);
        sculkCooldown.remove(uuid);
        sonicCooldown.remove(uuid);
        lastDeathTime.remove(uuid);
        lastDeathLocation.remove(uuid);
        apocalypseStart.remove(uuid);
        blackHpPool.remove(uuid);
        painTimeNextUse.remove(uuid);
        deathFlowerLeech.remove(uuid);
        player.setAbsorptionAmount(0.0);
    }

    @Override
    public void onSneakSkill(Player player) {
        if (!isHasAbility(player)) return;
        PlayerData data = plugin.getAbilityManager().getPlayerData(player.getUniqueId());
        if (!hasSkill(data, NecromancerSkill.DEATH_INSTINCT)) return;
        tryDeathInstinctReturn(player);
    }

    @EventHandler(ignoreCancelled = true)
    public void onMobKill(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null || !isHasAbility(killer)) return;

        PlayerData data = plugin.getAbilityManager().getPlayerData(killer.getUniqueId());
        boolean boss = isBossMob(event.getEntity().getType());
        int gained = rollSoulGain(boss);
        int stage = getApocalypseStage(killer, data);
        if (stage >= 1) {
            gained *= 2;
        }

        data.addExpiringSouls(gained, SOUL_TTL_MS);
        plugin.getAbilityManager().savePlayerData(killer.getUniqueId());
        killer.sendActionBar(Component.text("영혼 +" + gained + " (보유: " + data.getAvailableSouls() + ")",
                NamedTextColor.DARK_PURPLE));

        if (event.getEntity().getType() == EntityType.WARDEN && hasSkill(data, NecromancerSkill.SCULK_SOUL)) {
            long window = stage >= 2 ? WARDEN_WINDOW_ENHANCED_MS : WARDEN_WINDOW_MS;
            wardenSoulUntil.put(killer.getUniqueId(), System.currentTimeMillis() + window);
            killer.sendMessage(Component.text("[네크로맨서] 워든의 영혼을 흡수할 준비가 되었습니다.",
                    NamedTextColor.AQUA));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;
        if (shouldIgnoreSneakRightClickBlock(event)) return;

        Player player = event.getPlayer();
        if (!isHasAbility(player)) return;

        PlayerData data = plugin.getAbilityManager().getPlayerData(player.getUniqueId());
        Material type = player.getInventory().getItemInMainHand().getType();

        if (type == Material.NETHERITE_INGOT && hasSkill(data, NecromancerSkill.DEATH_KNIGHT)) {
            event.setCancelled(true);
            castSoulKnight(player, data);
            return;
        }

        if (type == Material.DIAMOND_BLOCK && hasSkill(data, NecromancerSkill.SCULK_SOUL)) {
            event.setCancelled(true);
            activateSculkSoul(player, data);
            return;
        }

        if (type == Material.WITHER_ROSE && hasSkill(data, NecromancerSkill.DEATH_FLOWER)) {
            event.setCancelled(true);
            castDeathFlower(player, data);
            return;
        }

        if (type == Material.NETHERITE_HOE && isEliteUnlocked(player, data)) {
            event.setCancelled(true);
            castJudgement(player);
            return;
        }

        if (type == Material.AIR && isWardenForm(player)) {
            event.setCancelled(true);
            castSonicBoom(player);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!isTouchingWitherRose(player)) return;

        if (isHasAbility(player)) {
            PlayerData data = plugin.getAbilityManager().getPlayerData(player.getUniqueId());
            if (hasSkill(data, NecromancerSkill.DEATH_FLOWER)) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 120, 4, false, false));
            }
            return;
        }

        NecromancerCarrier carrier = findDeathFlowerCarrier(player);
        if (carrier == null) return;

        long now = System.currentTimeMillis();
        long interval = carrier.stage >= 2 ? 150L : 300L;

        Map<UUID, LeechState> map = deathFlowerLeech.computeIfAbsent(carrier.player.getUniqueId(), k -> new HashMap<>());
        map.entrySet().removeIf(entry -> entry.getValue().expiresAt <= now);
        if (!map.containsKey(player.getUniqueId()) && map.size() >= 3) return;

        LeechState state = map.computeIfAbsent(player.getUniqueId(), k -> new LeechState());
        if (state.stolenSlots >= 10) return;
        if (now - state.lastDrainAt < interval) return;

        state.lastDrainAt = now;
        state.expiresAt = now + HEALTH_LEECH_RETURN_MS;
        state.stolenSlots++;

        double before = player.getHealth();
        player.damage(2.0, carrier.player);
        if (player.getHealth() < before) {
            heal(carrier.player, 2.0);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!isHasAbility(player)) return;

        if (isInvincible(player)) {
            event.setCancelled(true);
            return;
        }

        PlayerData data = plugin.getAbilityManager().getPlayerData(player.getUniqueId());
        if (hasSkill(data, NecromancerSkill.END_OF_WORLD)) {
            double after = player.getHealth() - event.getFinalDamage();
            if (after <= 1.0) {
                resetApocalypse(player, "체력이 반 칸 이하가 되어 세계의 종말 카운트가 초기화되었습니다.");
            }
        }

        if (blackHpPool.containsKey(player.getUniqueId())) {
            Bukkit.getScheduler().runTask(plugin, () -> syncBlackHpFromAbsorption(player));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player attacker && isHasAbility(attacker)) {
            if (isWardenForm(attacker) && attacker.getInventory().getItemInMainHand().getType() == Material.AIR) {
                event.setDamage(40.0);
            }
        }

        if (!(event.getEntity() instanceof Player victim) || !isHasAbility(victim)) return;

        double pool = blackHpPool.getOrDefault(victim.getUniqueId(), 0.0);
        if (pool <= 0.0) return;

        LivingEntity source = resolveDamageSource(event.getDamager());
        if (source == null) return;
        if (source instanceof Player enemy && isSameGuild(victim, enemy)) return;

        source.damage(event.getFinalDamage() * 0.65, victim);
        source.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 1, false, false));
        source.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 100, 3, false, false));
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();

        if (isHasAbility(victim)) {
            PlayerData data = plugin.getAbilityManager().getPlayerData(victim.getUniqueId());
            if (hasSkill(data, NecromancerSkill.DEATH_INSTINCT)) {
                lastDeathTime.put(victim.getUniqueId(), System.currentTimeMillis());
                lastDeathLocation.put(victim.getUniqueId(), victim.getLocation().clone());
            }
            if (hasSkill(data, NecromancerSkill.END_OF_WORLD)) {
                resetApocalypse(victim, "사망하여 세계의 종말 카운트가 초기화되었습니다.");
            }
        }

        for (Player necro : Bukkit.getOnlinePlayers()) {
            if (necro.equals(victim)) continue;
            if (!isHasAbility(necro)) continue;

            PlayerData data = plugin.getAbilityManager().getPlayerData(necro.getUniqueId());
            if (!hasSkill(data, NecromancerSkill.DEATH_INSTINCT)) continue;
            if (isSameGuild(necro, victim)) continue;

            String worldName = switch (victim.getWorld().getEnvironment()) {
                case NETHER -> "네더";
                case THE_END -> "엔드";
                default -> "오버월드";
            };
            necro.sendMessage(Component.text("[죽음 본능] 적이 " + worldName + "에서 사망했습니다.",
                    NamedTextColor.GRAY));

            if (necro.getWorld().equals(victim.getWorld())) {
                necro.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, (4 * 60 + 44) * 20, 3, false, false));
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event) {
        UUID castId = projectileToCast.remove(event.getEntity().getUniqueId());
        if (castId == null) return;

        JudgementCast cast = judgementCasts.get(castId);
        if (cast == null) return;

        cast.remainingProjectiles = Math.max(0, cast.remainingProjectiles - 1);
        Player owner = Bukkit.getPlayer(cast.ownerId);

        if (event.getHitEntity() instanceof LivingEntity hit && owner != null) {
            if (!(hit instanceof Player target) || !isSameGuild(owner, target)) {
                hit.damage(44.0, owner);
                hit.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 14 * 20, 3, false, false));
                hit.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 14 * 20, 3, false, false));
                hit.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 14 * 20, 3, false, false));
                hit.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 10, false, false));
                cast.hits++;
            }
        }

        if (cast.remainingProjectiles <= 0) {
            finalizeJudgementCast(castId);
        }
    }

    private void castSoulKnight(Player player, PlayerData data) {
        int consumed = data.consumeAllSouls();
        if (consumed <= 0) {
            player.sendMessage("소모할 영혼이 없습니다.");
            return;
        }
        plugin.getAbilityManager().savePlayerData(player.getUniqueId());

        int durationTicks = 3 * 60 * 20;
        long durationMs = 3L * 60L * 1000L;
        int strengthAmplifier = 0;
        boolean regen = false;

        if (consumed >= 20) {
            strengthAmplifier = 1;
            regen = true;
        }
        if (consumed >= 100) {
            strengthAmplifier = 2;
        }
        if (consumed >= 300) {
            durationTicks = 5 * 60 * 20;
            durationMs = 5L * 60L * 1000L;
            player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, durationTicks, 2, false, false));
            invincibleUntil.put(player.getUniqueId(), System.currentTimeMillis() + SOUL_KNIGHT_INVINCIBLE_MS);
        }

        player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, durationTicks, strengthAmplifier, false, false));
        if (regen) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, durationTicks, 0, false, false));
        }

        if (consumed >= 50) {
            grantSoulKnightHealth(player, durationMs);
            heal(player, 20.0);
        }

        player.getWorld().spawnParticle(Particle.SCULK_SOUL, player.getLocation().add(0, 1, 0), 30, 0.4, 0.6, 0.4, 0.0);
        player.playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.8f, 1.4f);
        player.sendMessage("소울 나이트 발동! 영혼 " + consumed + "개를 소모했습니다.");
    }

    private void tryDeathInstinctReturn(Player player) {
        long now = System.currentTimeMillis();
        Long diedAt = lastDeathTime.get(player.getUniqueId());
        Location deathLoc = lastDeathLocation.get(player.getUniqueId());
        if (diedAt == null || deathLoc == null) return;
        if (now - diedAt > DEATH_INSTINCT_WINDOW_MS) return;

        if (!InventoryUtils.consume(player, Material.DIAMOND_BLOCK, 30)) {
            player.sendMessage("죽음 본능 발동에는 다이아몬드 블록 30개가 필요합니다.");
            return;
        }

        deathLoc.getChunk().load();
        player.teleport(deathLoc);
        lastDeathTime.remove(player.getUniqueId());
        lastDeathLocation.remove(player.getUniqueId());
        player.sendMessage(Component.text("[죽음 본능] 사망 위치로 이동했습니다.", NamedTextColor.DARK_RED));
    }

    private void activateSculkSoul(Player player, PlayerData data) {
        long now = System.currentTimeMillis();
        UUID uuid = player.getUniqueId();

        long cdUntil = sculkCooldown.getOrDefault(uuid, 0L);
        if (now < cdUntil) {
            player.sendMessage("스컬크 소울 쿨타임: " + formatRemain(cdUntil - now));
            return;
        }

        long soulUntil = wardenSoulUntil.getOrDefault(uuid, 0L);
        if (now > soulUntil) {
            player.sendMessage("워든 처치 후 영혼 흡수 가능 시간이 지났습니다.");
            return;
        }

        int stage = getApocalypseStage(player, data);
        int needDiamond = stage >= 2 ? 10 : 20;
        long durationMs = stage >= 2 ? 6L * 60L * 1000L : 5L * 60L * 1000L;
        if (!InventoryUtils.consume(player, Material.DIAMOND_BLOCK, needDiamond)) {
            player.sendMessage("다이아몬드 블록 " + needDiamond + "개가 필요합니다.");
            return;
        }

        startWardenForm(player, durationMs);
        wardenSoulUntil.remove(uuid);
        sculkCooldown.put(uuid, now + applyCooldownReduction(player, SCULK_COOLDOWN_MS));

        player.getWorld().spawnParticle(Particle.SCULK_CHARGE_POP, player.getLocation().add(0, 1, 0),
                60, 0.8, 0.8, 0.8, 0.1);
        player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_ROAR, 1.0f, 0.8f);
        player.sendMessage("스컬크 소울 발동! 워든의 힘을 흡수했습니다.");
    }

    private void castSonicBoom(Player player) {
        long now = System.currentTimeMillis();
        UUID uuid = player.getUniqueId();
        long cdUntil = sonicCooldown.getOrDefault(uuid, 0L);
        if (now < cdUntil) {
            player.sendMessage("음파 쿨타임: " + formatRemain(cdUntil - now));
            return;
        }

        Vector dir = player.getEyeLocation().getDirection().normalize();
        RayTraceResult trace = player.getWorld().rayTraceEntities(
                player.getEyeLocation(),
                dir,
                28.0,
                1.3,
                entity -> entity instanceof LivingEntity
                        && !entity.equals(player)
                        && (!(entity instanceof Player target) || !isSameGuild(player, target))
        );

        if (trace != null && trace.getHitEntity() instanceof LivingEntity hit) {
            hit.damage(30.0, player);
        }

        player.getWorld().spawnParticle(Particle.SONIC_BOOM, player.getEyeLocation(), 1, 0, 0, 0, 0);
        player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 1.0f, 1.0f);
        sonicCooldown.put(uuid, now + applyCooldownReduction(player, SONIC_COOLDOWN_MS));
    }

    private void castDeathFlower(Player player, PlayerData data) {
        long now = System.currentTimeMillis();
        UUID uuid = player.getUniqueId();
        long cdUntil = deathFlowerCooldown.getOrDefault(uuid, 0L);
        if (now < cdUntil) {
            player.sendMessage("죽음의 꽃 쿨타임: " + formatRemain(cdUntil - now));
            return;
        }

        if (!InventoryUtils.consume(player, Material.WITHER_ROSE, 5)) {
            player.sendMessage("죽음의 꽃 발동에는 위더 장미 5개가 필요합니다.");
            return;
        }

        RayTraceResult trace = player.getWorld().rayTraceEntities(
                player.getEyeLocation(),
                player.getEyeLocation().getDirection(),
                30.0,
                1.3,
                entity -> entity instanceof LivingEntity
                        && !entity.equals(player)
                        && (!(entity instanceof Player target) || !isSameGuild(player, target))
        );

        if (trace == null || !(trace.getHitEntity() instanceof LivingEntity target)) {
            player.sendMessage("바라보는 방향에 대상이 없습니다.");
            return;
        }

        Vector pull = player.getLocation().toVector().subtract(target.getLocation().toVector()).normalize().multiply(1.35);
        pull.setY(Math.max(0.35, pull.getY() + 0.2));
        target.setVelocity(pull);

        int removedTotems = 0;
        if (target instanceof Player targetPlayer) {
            removedTotems = removeTotems(targetPlayer, 3);
        }

        long cooldown = applyCooldownReduction(player, DEATH_FLOWER_COOLDOWN_MS);
        deathFlowerCooldown.put(uuid, now + cooldown);
        invincibleUntil.put(uuid, now + DEATH_FLOWER_INVINCIBLE_MS);
        player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 10 * 20, 1, false, false));
        player.playSound(player.getLocation(), Sound.ENTITY_WITHER_SHOOT, 1.0f, 0.9f);

        if (removedTotems > 0) {
            player.sendMessage("죽음의 꽃 발동! 불사의 토템 " + removedTotems + "개를 제거했습니다.");
        } else {
            player.sendMessage("죽음의 꽃 발동!");
        }
    }

    private void castJudgement(Player player) {
        PlayerData data = plugin.getAbilityManager().getPlayerData(player.getUniqueId());
        if (!isEliteUnlocked(player, data)) {
            player.sendMessage("세계의 종말 엘리트 스킬은 종말 카운트 44시간 이후 해금됩니다.");
            return;
        }

        ItemStack hoe = player.getInventory().getItemInMainHand();
        if (hoe.getType() != Material.NETHERITE_HOE) return;
        if (!consumeDurability(hoe, 200)) {
            player.sendMessage("내구도 200 이상 남은 네더라이트 괭이가 필요합니다.");
            return;
        }

        spawnJudgementVolley(player, 14, 0.15, 70.0, true, false);
        player.playSound(player.getLocation(), Sound.ENTITY_WITHER_AMBIENT, 1.0f, 0.6f);
        player.sendMessage("종말의 심판 발동!");
    }

    private void spawnJudgementVolley(Player owner, int amount, double speed, double spread,
                                      boolean fallbackOnMiss, boolean homing) {
        if (amount <= 0) return;
        UUID castId = UUID.randomUUID();
        JudgementCast cast = new JudgementCast(owner.getUniqueId(), amount, fallbackOnMiss);
        judgementCasts.put(castId, cast);

        Vector base = owner.getEyeLocation().getDirection().normalize();
        double step = amount == 1 ? 0.0 : spread / (amount - 1);
        double begin = -spread / 2.0;

        for (int i = 0; i < amount; i++) {
            double angle = begin + (step * i);
            Vector direction = rotateAroundY(base, angle).normalize();

            WitherSkull skull = owner.launchProjectile(WitherSkull.class);
            skull.setVelocity(direction.multiply(speed));
            skull.setCharged(true);
            skull.setIsIncendiary(false);
            skull.setYield(2.0f);
            projectileToCast.put(skull.getUniqueId(), castId);

            if (homing) {
                startHoming(skull, owner, speed);
            }
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> finalizeJudgementCast(castId), 20L * 12L);
    }

    private void finalizeJudgementCast(UUID castId) {
        JudgementCast cast = judgementCasts.remove(castId);
        if (cast == null) return;

        projectileToCast.entrySet().removeIf(entry -> entry.getValue().equals(castId));
        if (!cast.fallbackOnMiss || cast.hits > 0) return;

        Player owner = Bukkit.getPlayer(cast.ownerId);
        if (owner == null || !owner.isOnline() || !isHasAbility(owner)) return;
        spawnJudgementVolley(owner, 4, 0.45, 24.0, false, true);
        owner.sendMessage("심판이 빗나가 추적 머리 4개를 발사합니다.");
    }

    private void startHoming(WitherSkull skull, Player owner, double speed) {
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            if (!skull.isValid() || skull.isDead()) {
                task.cancel();
                return;
            }

            LivingEntity nearest = null;
            double nearestSq = Double.MAX_VALUE;
            Location from = skull.getLocation();
            for (Entity entity : skull.getNearbyEntities(18, 18, 18)) {
                if (!(entity instanceof LivingEntity living)) continue;
                if (living.equals(owner)) continue;
                if (living instanceof Player target && isSameGuild(owner, target)) continue;
                double distSq = living.getLocation().distanceSquared(from);
                if (distSq < nearestSq) {
                    nearestSq = distSq;
                    nearest = living;
                }
            }

            if (nearest == null) return;
            Vector desired = nearest.getLocation().add(0, 1.0, 0).toVector().subtract(from.toVector()).normalize().multiply(speed);
            Vector current = skull.getVelocity();
            Vector next = current.multiply(0.6).add(desired.multiply(0.4));
            skull.setVelocity(next);
        }, 1L, 1L);
    }

    private void startWardenForm(Player player, long durationMs) {
        UUID uuid = player.getUniqueId();
        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth == null) return;

        preWardenMaxHealth.putIfAbsent(uuid, maxHealth.getBaseValue());
        maxHealth.setBaseValue(100.0);
        heal(player, 100.0);
        wardenFormUntil.put(uuid, System.currentTimeMillis() + durationMs);
    }

    private void endWardenForm(Player player) {
        UUID uuid = player.getUniqueId();
        wardenFormUntil.remove(uuid);

        Double original = preWardenMaxHealth.remove(uuid);
        if (original == null) return;

        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth == null) return;
        maxHealth.setBaseValue(original);
        if (player.getHealth() > original) {
            player.setHealth(original);
        }
    }

    private boolean isWardenForm(Player player) {
        long until = wardenFormUntil.getOrDefault(player.getUniqueId(), 0L);
        return System.currentTimeMillis() < until;
    }

    private void grantSoulKnightHealth(Player player, long durationMs) {
        UUID uuid = player.getUniqueId();
        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth == null) return;

        preSoulKnightMaxHealth.putIfAbsent(uuid, maxHealth.getBaseValue());
        double base = preSoulKnightMaxHealth.get(uuid);
        double target = Math.max(maxHealth.getBaseValue(), base + SOUL_KNIGHT_BONUS_HEALTH);
        maxHealth.setBaseValue(target);
        soulKnightHealthUntil.put(uuid, System.currentTimeMillis() + durationMs);
    }

    private void restoreSoulKnightMaxHealth(Player player) {
        UUID uuid = player.getUniqueId();
        soulKnightHealthUntil.remove(uuid);

        Double base = preSoulKnightMaxHealth.remove(uuid);
        if (base == null) return;

        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth == null) return;
        maxHealth.setBaseValue(base);
        if (player.getHealth() > base) {
            player.setHealth(base);
        }
    }

    private void startTicker() {
        if (tickerTask != null) {
            tickerTask.cancel();
        }

        tickerTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis();
            boolean forceNight = false;

            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!isHasAbility(player)) continue;

                UUID uuid = player.getUniqueId();
                PlayerData data = plugin.getAbilityManager().getPlayerData(uuid);

                int purged = data.purgeExpiredSouls();
                if (purged > 0) {
                    plugin.getAbilityManager().savePlayerData(uuid);
                }

                Long soulKnightUntil = soulKnightHealthUntil.get(uuid);
                if (soulKnightUntil != null && now >= soulKnightUntil && !isWardenForm(player)) {
                    restoreSoulKnightMaxHealth(player);
                }

                Long wardenUntil = wardenFormUntil.get(uuid);
                if (wardenUntil != null && now >= wardenUntil) {
                    endWardenForm(player);
                }

                if (invincibleUntil.getOrDefault(uuid, 0L) <= now) {
                    invincibleUntil.remove(uuid);
                }

                if (hasSkill(data, NecromancerSkill.END_OF_WORLD)) {
                    ensureApocalypseStart(player, data);
                    int stage = getApocalypseStage(player, data);
                    if (stage >= 0) {
                        applyMournerTime(player);
                    }
                    if (stage >= 3) {
                        applyPhantomZone(player);
                    }
                    if (stage >= 4) {
                        forceNight = true;
                    }
                    if (stage >= 5) {
                        triggerPainTime(player, now);
                    }
                }

                syncBlackHpFromAbsorption(player);
            }

            cleanupLeech(now);
            syncEternalNight(forceNight);
        }, 20L, 20L);
    }

    private void cleanupLeech(long now) {
        Iterator<Map.Entry<UUID, Map<UUID, LeechState>>> outer = deathFlowerLeech.entrySet().iterator();
        while (outer.hasNext()) {
            Map.Entry<UUID, Map<UUID, LeechState>> ownerEntry = outer.next();
            Map<UUID, LeechState> targets = ownerEntry.getValue();
            Iterator<Map.Entry<UUID, LeechState>> inner = targets.entrySet().iterator();
            while (inner.hasNext()) {
                Map.Entry<UUID, LeechState> targetEntry = inner.next();
                LeechState state = targetEntry.getValue();
                if (state.expiresAt > now) continue;

                Player target = Bukkit.getPlayer(targetEntry.getKey());
                if (target != null && target.isOnline()) {
                    heal(target, state.stolenSlots * 2.0);
                }
                inner.remove();
            }

            if (targets.isEmpty()) {
                outer.remove();
            }
        }
    }

    private void applyMournerTime(Player player) {
        long worldTime = player.getWorld().getTime();
        boolean night = worldTime >= 13000 && worldTime <= 23000;
        boolean dark = player.getLocation().getBlock().getLightLevel() <= 7;
        if (!night && !dark) return;

        player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 40, 2, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 40, 4, false, false));

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.equals(player)) continue;
            if (!isSameGuild(player, online)) continue;
            online.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 40, 0, false, false));
            online.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 40, 0, false, false));
        }
    }

    private void applyPhantomZone(Player owner) {
        for (Entity entity : owner.getNearbyEntities(10, 10, 10)) {
            if (!(entity instanceof Player enemy)) continue;
            if (isSameGuild(owner, enemy)) continue;

            enemy.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 40, 0, false, false));
            enemy.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 2, false, false));
            enemy.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 60, 1, false, false));
            enemy.damage(2.0, owner);
            heal(owner, 1.0);
        }
    }

    private void triggerPainTime(Player owner, long now) {
        UUID uuid = owner.getUniqueId();
        long next = painTimeNextUse.getOrDefault(uuid, 0L);
        if (now < next) return;
        painTimeNextUse.put(uuid, now + PAIN_TIME_INTERVAL_MS);

        double gained = 0.0;
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (target.equals(owner)) continue;
            if (isSameGuild(owner, target)) continue;

            double before = target.getHealth();
            target.damage(10.0, owner);
            gained += Math.min(10.0, before);
        }

        if (gained <= 0.0) return;
        double pool = blackHpPool.getOrDefault(uuid, 0.0) + gained;
        blackHpPool.put(uuid, pool);
        owner.setAbsorptionAmount(pool);
        owner.sendMessage(Component.text("[세계의 종말] 고통의 시간이 발동했습니다.", NamedTextColor.DARK_RED));
    }

    private void syncBlackHpFromAbsorption(Player player) {
        UUID uuid = player.getUniqueId();
        double now = player.getAbsorptionAmount();
        if (now <= 0.0) {
            blackHpPool.remove(uuid);
            return;
        }
        blackHpPool.put(uuid, now);
    }

    private void syncEternalNight(boolean forceNight) {
        if (forceNight) {
            for (World world : Bukkit.getWorlds()) {
                setDaylightCycle(world, false);
                world.setTime(18000L);
            }
            eternalNightForced = true;
            return;
        }

        if (!eternalNightForced) return;
        for (World world : Bukkit.getWorlds()) {
            setDaylightCycle(world, true);
        }
        eternalNightForced = false;
    }

    @SuppressWarnings({"unchecked", "removal"})
    private void setDaylightCycle(World world, boolean value) {
        GameRule<?> rule = GameRule.getByName("doDaylightCycle");
        if (rule == null || rule.getType() != Boolean.class) return;
        world.setGameRule((GameRule<Boolean>) rule, value);
    }

    private void ensureApocalypseStart(Player player, PlayerData data) {
        if (!hasSkill(data, NecromancerSkill.END_OF_WORLD)) return;
        apocalypseStart.putIfAbsent(player.getUniqueId(), System.currentTimeMillis());
    }

    private int getApocalypseStage(Player player, PlayerData data) {
        if (!hasSkill(data, NecromancerSkill.END_OF_WORLD)) return -1;
        long start = apocalypseStart.computeIfAbsent(player.getUniqueId(), key -> System.currentTimeMillis());
        long elapsed = System.currentTimeMillis() - start;

        if (elapsed >= STAGE_FIVE_MS) return 5;
        if (elapsed >= STAGE_FOUR_MS) return 4;
        if (elapsed >= STAGE_THREE_MS) return 3;
        if (elapsed >= STAGE_TWO_MS) return 2;
        if (elapsed >= STAGE_ONE_MS) return 1;
        return 0;
    }

    private void resetApocalypse(Player player, String message) {
        UUID uuid = player.getUniqueId();
        apocalypseStart.remove(uuid);
        painTimeNextUse.remove(uuid);
        blackHpPool.remove(uuid);
        player.setAbsorptionAmount(0.0);
        player.sendMessage(message);
    }

    private long applyCooldownReduction(Player player, long baseMs) {
        PlayerData data = plugin.getAbilityManager().getPlayerData(player.getUniqueId());
        int stage = getApocalypseStage(player, data);
        if (stage >= 2) {
            return Math.round(baseMs * 0.85);
        }
        return baseMs;
    }

    private boolean isEliteUnlocked(Player player, PlayerData data) {
        return hasSkill(data, NecromancerSkill.END_OF_WORLD) && getApocalypseStage(player, data) >= 5;
    }

    private boolean hasSkill(PlayerData data, NecromancerSkill skill) {
        return data.getSoulInvestment(skill.getKey()) >= skill.getUnlockCost();
    }

    private boolean isHasAbility(Player player) {
        return getName().equals(plugin.getAbilityManager().getPlayerData(player.getUniqueId()).getAbilityName());
    }

    private boolean isBossMob(EntityType type) {
        return type == EntityType.WARDEN || type == EntityType.WITHER || type == EntityType.ENDER_DRAGON;
    }

    private int rollSoulGain(boolean boss) {
        if (boss) {
            int r = random.nextInt(100);
            if (r < 60) return 50;
            if (r < 85) return 100;
            if (r < 96) return 150;
            return 444;
        }

        int r = random.nextInt(105);
        if (r < 80) return 1;
        if (r < 95) return 2;
        if (r < 100) return 3;
        if (r < 104) return 4;
        return 44;
    }

    private boolean isTouchingWitherRose(Player player) {
        Location loc = player.getLocation();
        Material feet = loc.getBlock().getType();
        Material below = loc.clone().add(0, -1, 0).getBlock().getType();
        return feet == Material.WITHER_ROSE || below == Material.WITHER_ROSE;
    }

    private NecromancerCarrier findDeathFlowerCarrier(Player target) {
        NecromancerCarrier best = null;
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!isHasAbility(online)) continue;
            if (online.equals(target)) continue;
            if (!online.getWorld().equals(target.getWorld())) continue;
            if (online.getLocation().distanceSquared(target.getLocation()) > 40 * 40) continue;

            PlayerData data = plugin.getAbilityManager().getPlayerData(online.getUniqueId());
            if (!hasSkill(data, NecromancerSkill.DEATH_FLOWER)) continue;
            if (isSameGuild(online, target)) continue;

            int stage = getApocalypseStage(online, data);
            double distSq = online.getLocation().distanceSquared(target.getLocation());
            if (best == null || distSq < best.distanceSq) {
                best = new NecromancerCarrier(online, stage, distSq);
            }
        }
        return best;
    }

    private boolean isInvincible(Player player) {
        return System.currentTimeMillis() < invincibleUntil.getOrDefault(player.getUniqueId(), 0L);
    }

    private boolean isSameGuild(Player p1, Player p2) {
        if (p1.equals(p2)) return true;
        GuildData g1 = plugin.getGuildManager().getGuildByMember(p1.getUniqueId());
        GuildData g2 = plugin.getGuildManager().getGuildByMember(p2.getUniqueId());
        return g1 != null && g2 != null && g1.name.equalsIgnoreCase(g2.name);
    }

    private LivingEntity resolveDamageSource(Entity damager) {
        if (damager instanceof LivingEntity living) {
            return living;
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof LivingEntity shooter) {
            return shooter;
        }
        return null;
    }

    private void heal(Player player, double amount) {
        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth == null) return;
        double target = Math.min(maxHealth.getValue(), player.getHealth() + Math.max(0.0, amount));
        player.setHealth(target);
    }

    private int removeTotems(Player player, int amount) {
        if (amount <= 0) return 0;
        int removed = 0;
        PlayerInventory inv = player.getInventory();

        ItemStack offHand = inv.getItemInOffHand();
        if (offHand.getType() == Material.TOTEM_OF_UNDYING) {
            int take = Math.min(amount, offHand.getAmount());
            removed += take;
            if (offHand.getAmount() == take) {
                inv.setItemInOffHand(new ItemStack(Material.AIR));
            } else {
                offHand.setAmount(offHand.getAmount() - take);
                inv.setItemInOffHand(offHand);
            }
        }

        for (int slot = 0; slot < 36 && removed < amount; slot++) {
            ItemStack item = inv.getItem(slot);
            if (item == null || item.getType() != Material.TOTEM_OF_UNDYING) continue;
            int take = Math.min(amount - removed, item.getAmount());
            removed += take;
            if (item.getAmount() == take) {
                inv.setItem(slot, null);
            } else {
                item.setAmount(item.getAmount() - take);
                inv.setItem(slot, item);
            }
        }
        return removed;
    }

    private boolean consumeDurability(ItemStack stack, int amount) {
        if (stack == null || stack.getType().getMaxDurability() <= 0 || amount <= 0) return false;
        if (!(stack.getItemMeta() instanceof Damageable damageable)) return false;

        int max = stack.getType().getMaxDurability();
        int currentDamage = damageable.getDamage();
        int remaining = max - currentDamage;
        if (remaining <= amount) return false;

        damageable.setDamage(currentDamage + amount);
        stack.setItemMeta(damageable);
        return true;
    }

    private Vector rotateAroundY(Vector vector, double degrees) {
        double radians = Math.toRadians(degrees);
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        double x = vector.getX() * cos - vector.getZ() * sin;
        double z = vector.getX() * sin + vector.getZ() * cos;
        return new Vector(x, vector.getY(), z);
    }

    private String formatRemain(long ms) {
        long total = Math.max(1L, ms / 1000L);
        long minutes = total / 60L;
        long seconds = total % 60L;
        if (minutes <= 0) return seconds + "초";
        return minutes + "분 " + seconds + "초";
    }
}
