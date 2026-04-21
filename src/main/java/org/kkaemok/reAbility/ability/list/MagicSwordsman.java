package org.kkaemok.reAbility.ability.list;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.DragonFireball;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.kkaemok.reAbility.ReAbility;
import org.kkaemok.reAbility.ability.AbilityBase;
import org.kkaemok.reAbility.ability.AbilityGrade;
import org.kkaemok.reAbility.guild.GuildData;
import org.kkaemok.reAbility.guild.GuildManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public class MagicSwordsman extends AbilityBase {
    private static final long BLEED_STACK_DURATION_MS = 5000L;
    private static final double BLEED_DAMAGE_PER_SECOND = 5.0;
    private static final double SWORD_KNOCKBACK_POWER = 1.8;
    private static final int SWORD_KNOCKBACK_UP = 30;
    private static final long SHEATH_COOLDOWN_MS = 60_000L;
    private static final long PHANTOM_COOLDOWN_MS = 10_000L;
    private static final long BLINK_DEFAULT_COOLDOWN_MS = 10_000L;
    private static final long BLINK_MAGIC_FORM_COOLDOWN_MS = 5_000L;
    private static final long PERFECT_CIRCLE_COOLDOWN_MS = 30L * 60L * 1000L;
    private static final long PERFECT_CIRCLE_DURATION_MS = 6L * 60L * 1000L;
    private static final int PERFECT_CIRCLE_RADIUS = 300;
    private static final double MAGIC_ORB_DIRECT_DAMAGE = 20.0;
    private static final double MAGIC_ORB_SPLASH_DAMAGE = 15.0;
    private static final double MAGIC_ORB_SPLASH_RADIUS = 4.0;
    private static final int MAGIC_ORB_FAN_COUNT = 7;
    private static final double MAGIC_ORB_FAN_SPREAD = 42.0;
    private static final double MAGIC_ORB_SPEED = 2.8;
    private static final int OUTSIDE_PENALTY_HEARTS = 2;
    private static final int OUTSIDE_PENALTY_INTERVAL_MS = 2000;
    private static final double JUMP_ASCEND_VELOCITY = 1.05;
    private static final double JUMP_DESCEND_VELOCITY = -2.8;
    private static final double BLINK_LANDING_RANGE = 5.0;
    private static final double BLINK_LANDING_DAMAGE = 15.0;
    private static final int BLINK_LANDING_DEBUFF_TICKS = 20;
    private static final int PHANTOM_DEBUFF_TICKS = 30;
    private static final double PHANTOM_BONUS_DAMAGE = 10.0;
    private static final double PHANTOM_RANGE = 6.0;
    private static final Set<String> BLOCKED_TP_COMMANDS = Set.of(
            "home", "sethome", "tpa", "tphere", "tpaccept", "tpadeny", "tpacancel",
            "rtp", "warp", "spawn", "back", "tp"
    );
    private static final EnumSet<PlayerTeleportEvent.TeleportCause> BLOCKED_TELEPORT_CAUSES = EnumSet.of(
            PlayerTeleportEvent.TeleportCause.COMMAND,
            PlayerTeleportEvent.TeleportCause.PLUGIN,
            PlayerTeleportEvent.TeleportCause.ENDER_PEARL,
            PlayerTeleportEvent.TeleportCause.NETHER_PORTAL,
            PlayerTeleportEvent.TeleportCause.END_PORTAL,
            PlayerTeleportEvent.TeleportCause.END_GATEWAY,
            PlayerTeleportEvent.TeleportCause.SPECTATE
    );

    private final ReAbility plugin;
    private final GuildManager guildManager;
    private final Map<UUID, AttackForm> forms = new HashMap<>();
    private final Map<UUID, Long> sheathCooldown = new HashMap<>();
    private final Map<UUID, Long> phantomCooldown = new HashMap<>();
    private final Map<UUID, Long> blinkCooldown = new HashMap<>();
    private final Map<UUID, Long> perfectCircleCooldown = new HashMap<>();
    private final Map<UUID, List<BleedStack>> bleedStacks = new HashMap<>();
    private final Map<UUID, PerfectCircle> activeCircles = new HashMap<>();
    private final Map<UUID, PendingBlinkLanding> pendingBlinkLandings = new HashMap<>();
    private final Map<UUID, Long> ignoreFallUntil = new HashMap<>();
    private final Map<UUID, Boolean> groundedState = new HashMap<>();
    private final org.bukkit.NamespacedKey orbTagKey;
    private final org.bukkit.NamespacedKey orbOwnerKey;

    public MagicSwordsman(ReAbility plugin) {
        this.plugin = plugin;
        this.guildManager = plugin.getGuildManager();
        this.orbTagKey = new org.bukkit.NamespacedKey(plugin, "magic_swordsman_orb");
        this.orbOwnerKey = new org.bukkit.NamespacedKey(plugin, "magic_swordsman_orb_owner");
        startTickTask();
    }

    @Override
    public String getName() {
        return "MAGIC_SWORDSMAN";
    }

    @Override
    public String getDisplayName() {
        return "마검사";
    }

    @Override
    public AbilityGrade getGrade() {
        return AbilityGrade.S;
    }

    @Override
    public String[] getDescription() {
        return new String[]{
                "인첸트 테이블로 검술/마법구 2가지 일반공격 폼을 사용합니다.",
                "검술: 좌클릭 공격 시 적 넉백 + 출혈 5초(1초당 5피해, 중첩), 이후 마법구 폼으로 전환됩니다.",
                "마법구: 우클릭 시 부채꼴 고속 드래곤브레스 투사체를 발사해 직격 20/주변 15 피해를 줍니다.",
                "스킬 {칼집 소환}: 다이아 블럭 우클릭, 인첸트 테이블 즉시 획득 + 반경 20 적 구속3 5초 (쿨 1분).",
                "스킬 {팬텀}: 인첸트 테이블 들고 웅크리기, 전방위 검술 + 추가 10피해/구속5 1.5초 (쿨 10초).",
                "스킬 {완벽한 마법진}: 인첸트 테이블 설치 시 반경 300 마법진 전개(6분).",
                "마법진 내 TP/차원이동/비행 금지, 이탈 차단, 자신 버프/적 디버프 적용, 해제 후 쿨 30분.",
                "스킬 {점멸 전격}: 인첸트 테이블 들고 점프 시 상승-전방 마법구-급강하 착지 충격파 (쿨 10초/마법구 폼 5초)."
        };
    }

    @Override
    public void onActivate(Player player) {
        forms.put(player.getUniqueId(), AttackForm.SWORD);
        groundedState.put(player.getUniqueId(), isGrounded(player));
    }

    @Override
    public void onDeactivate(Player player) {
        UUID uuid = player.getUniqueId();
        forms.remove(uuid);
        sheathCooldown.remove(uuid);
        phantomCooldown.remove(uuid);
        blinkCooldown.remove(uuid);
        pendingBlinkLandings.remove(uuid);
        ignoreFallUntil.remove(uuid);
        groundedState.remove(uuid);
        bleedStacks.remove(uuid);

        PerfectCircle circle = activeCircles.remove(uuid);
        if (circle != null) {
            endCircle(circle, false);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSwordAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!(event.getEntity() instanceof Player target)) return;
        if (lacksAbility(attacker)) return;
        if (isSameGuild(attacker, target)) return;
        if (attacker.getInventory().getItemInMainHand().getType() != Material.ENCHANTING_TABLE) return;
        if (getForm(attacker) != AttackForm.SWORD) return;

        applySwordStrike(attacker, target);
        setForm(attacker, AttackForm.MAGIC);
    }

    @EventHandler(ignoreCancelled = true)
    public void onRightClick(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!event.getAction().isRightClick()) return;
        if (shouldIgnoreSneakRightClickBlock(event)) return;

        Player player = event.getPlayer();
        if (lacksAbility(player)) return;

        ItemStack hand = player.getInventory().getItemInMainHand();
        Material type = hand.getType();

        if (type == Material.DIAMOND_BLOCK) {
            useSheathSummon(player, hand);
            return;
        }

        if (type != Material.ENCHANTING_TABLE) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR) return;
        if (player.isSneaking()) return;
        if (getForm(player) != AttackForm.MAGIC) return;

        castMagicOrbFan(player);
        setForm(player, AttackForm.SWORD);
    }

    @Override
    public void onSneakSkill(Player player) {
        if (player.getInventory().getItemInMainHand().getType() != Material.ENCHANTING_TABLE) return;

        long now = System.currentTimeMillis();
        long until = phantomCooldown.getOrDefault(player.getUniqueId(), 0L);
        if (now < until) {
            player.sendMessage(Component.text("팬텀 쿨타임입니다.", NamedTextColor.RED));
            return;
        }

        phantomCooldown.put(player.getUniqueId(), now + PHANTOM_COOLDOWN_MS);
        executePhantom(player);
        setForm(player, AttackForm.MAGIC);
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location from = event.getFrom();
        Location to = event.getTo();

        handleJumpTrigger(player, from, to);
        handleBlinkLanding(player);

        Collection<PerfectCircle> circles = getCirclesContaining(from);
        if (circles.isEmpty()) return;

        for (PerfectCircle circle : circles) {
            if (isInsideCircle(circle, to)) continue;

            Location clamped = clampToBoundary(circle, to, from);
            event.setTo(clamped);
            player.sendActionBar(Component.text("완벽한 마법진 경계를 벗어날 수 없습니다.", NamedTextColor.RED));
            return;
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFallDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) return;

        Long until = ignoreFallUntil.get(player.getUniqueId());
        if (until == null) return;
        if (System.currentTimeMillis() > until) {
            ignoreFallUntil.remove(player.getUniqueId());
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPerfectCirclePlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (lacksAbility(player)) return;
        if (event.getBlockPlaced().getType() != Material.ENCHANTING_TABLE) return;

        UUID ownerId = player.getUniqueId();
        if (activeCircles.containsKey(ownerId)) {
            player.sendMessage(Component.text("이미 완벽한 마법진이 유지 중입니다.", NamedTextColor.RED));
            return;
        }

        long now = System.currentTimeMillis();
        long cooldownUntil = perfectCircleCooldown.getOrDefault(ownerId, 0L);
        if (now < cooldownUntil) {
            long remainMin = Math.max(1L, (cooldownUntil - now + 59_999L) / 60_000L);
            player.sendMessage(Component.text("완벽한 마법진 쿨타임: " + remainMin + "분", NamedTextColor.RED));
            return;
        }

        if (Disruptor.tryFailSkill(plugin, player)) return;
        startPerfectCircle(player, event.getBlockPlaced().getLocation());
    }

    @EventHandler(ignoreCancelled = true)
    public void onPerfectCircleBreak(BlockBreakEvent event) {
        if (event.getBlock().getType() != Material.ENCHANTING_TABLE) return;
        PerfectCircle circle = findCircleByTable(event.getBlock().getLocation());
        if (circle == null) return;
        activeCircles.remove(circle.ownerId);
        endCircle(circle, true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (event.blockList().isEmpty()) return;
        endCirclesForExplodedBlocks(event.blockList());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (event.blockList().isEmpty()) return;
        endCirclesForExplodedBlocks(event.blockList());
    }

    @EventHandler(ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (!BLOCKED_TELEPORT_CAUSES.contains(event.getCause())) return;
        if (isOutsideAllCircles(event.getFrom())) return;

        event.setCancelled(true);
        event.getPlayer().sendMessage(Component.text("완벽한 마법진 안에서는 TP가 금지됩니다.", NamedTextColor.RED));
    }

    @EventHandler(ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent event) {
        if (isOutsideAllCircles(event.getFrom())) return;
        event.setCancelled(true);
        event.getPlayer().sendMessage(Component.text("완벽한 마법진 안에서는 차원이동이 금지됩니다.", NamedTextColor.RED));
    }

    @EventHandler(ignoreCancelled = true)
    public void onToggleFlight(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();
        if (isOutsideAllCircles(player.getLocation())) return;
        if (player.getGameMode() == GameMode.SPECTATOR) return;
        event.setCancelled(true);
        player.setFlying(false);
        player.setAllowFlight(false);
        player.sendMessage(Component.text("완벽한 마법진 안에서는 비행할 수 없습니다.", NamedTextColor.RED));
    }

    @EventHandler(ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (isOutsideAllCircles(player.getLocation())) return;

        String message = event.getMessage();
        if (!message.startsWith("/")) return;

        String raw = message.substring(1).trim();
        if (raw.isEmpty()) return;
        String root = raw.split("\\s+")[0].toLowerCase(Locale.ROOT);
        if (!isTeleportCommand(root)) return;

        event.setCancelled(true);
        player.sendMessage(Component.text("완벽한 마법진 안에서는 TP 명령을 사용할 수 없습니다.", NamedTextColor.RED));
    }

    @EventHandler(ignoreCancelled = true)
    public void onMagicOrbHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof DragonFireball orb)) return;
        if (isNotMagicOrb(orb)) return;

        UUID ownerId = parseOwnerId(orb);
        Player owner = ownerId == null ? null : Bukkit.getPlayer(ownerId);
        Location impact = getImpactLocation(event, orb);

        Player directHit = event.getHitEntity() instanceof Player p ? p : null;
        applyMagicOrbExplosion(owner, directHit, impact);
        orb.remove();

        Bukkit.getScheduler().runTaskLater(plugin, () -> removeNearbyDragonClouds(impact), 1L);
    }

    @EventHandler(ignoreCancelled = true)
    public void onMagicOrbCollisionDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof DragonFireball orb)) return;
        if (isNotMagicOrb(orb)) return;
        event.setCancelled(true);
    }

    private void startTickTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                tickBleedStacks();
                tickPerfectCircles();
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private void handleJumpTrigger(Player player, Location from, Location to) {
        UUID uuid = player.getUniqueId();
        boolean wasGrounded = groundedState.getOrDefault(uuid, isGrounded(player));
        boolean nowGrounded = isGrounded(player);
        groundedState.put(uuid, nowGrounded);

        if (lacksAbility(player)) return;
        if (player.getInventory().getItemInMainHand().getType() != Material.ENCHANTING_TABLE) return;
        if (!wasGrounded || nowGrounded) return;
        if (to.getY() <= from.getY()) return;

        long now = System.currentTimeMillis();
        long cooldownMs = getForm(player) == AttackForm.MAGIC
                ? BLINK_MAGIC_FORM_COOLDOWN_MS
                : BLINK_DEFAULT_COOLDOWN_MS;
        long cooldownUntil = blinkCooldown.getOrDefault(uuid, 0L);
        if (now < cooldownUntil) return;
        if (Disruptor.tryFailSkill(plugin, player)) return;

        blinkCooldown.put(uuid, now + cooldownMs);
        triggerBlink(player);
    }

    private void triggerBlink(Player player) {
        UUID uuid = player.getUniqueId();
        pendingBlinkLandings.put(uuid, new PendingBlinkLanding(System.currentTimeMillis() + 250L));
        ignoreFallUntil.put(uuid, System.currentTimeMillis() + 3500L);

        player.setVelocity(player.getVelocity().setX(0).setZ(0).setY(JUMP_ASCEND_VELOCITY));

        Vector horizontal = player.getLocation().getDirection().setY(0);
        if (horizontal.lengthSquared() < 1.0E-4) {
            horizontal = new Vector(0, 0, 1);
        }
        horizontal.normalize();
        launchMagicOrb(player, horizontal);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            if (!pendingBlinkLandings.containsKey(uuid)) return;
            Vector vel = player.getVelocity();
            player.setVelocity(new Vector(vel.getX(), JUMP_DESCEND_VELOCITY, vel.getZ()));
        }, 2L);
    }

    private void handleBlinkLanding(Player player) {
        PendingBlinkLanding pending = pendingBlinkLandings.get(player.getUniqueId());
        if (pending == null) return;

        if (!pending.leftGround && !isGrounded(player)) {
            pending.leftGround = true;
            return;
        }

        if (!pending.leftGround) return;
        if (System.currentTimeMillis() < pending.activateAt) return;
        if (!isGrounded(player)) return;

        pendingBlinkLandings.remove(player.getUniqueId());
        executeBlinkLandingBurst(player);
    }

    private void executeBlinkLandingBurst(Player caster) {
        for (Entity entity : caster.getNearbyEntities(BLINK_LANDING_RANGE, BLINK_LANDING_RANGE, BLINK_LANDING_RANGE)) {
            if (!(entity instanceof Player target)) continue;
            if (isSameGuild(caster, target)) continue;

            target.damage(BLINK_LANDING_DAMAGE, caster);
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, BLINK_LANDING_DEBUFF_TICKS, 2, false, false));
            target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, BLINK_LANDING_DEBUFF_TICKS, 2, false, false));

            Vector knock = target.getLocation().toVector().subtract(caster.getLocation().toVector());
            knock.setY(0);
            if (knock.lengthSquared() < 1.0E-4) {
                knock = caster.getLocation().getDirection().setY(0);
            }
            if (knock.lengthSquared() < 1.0E-4) {
                knock = new Vector(0, 0, 1);
            }
            target.setVelocity(knock.normalize().multiply(1.6).setY(0.25));
        }
    }

    private void useSheathSummon(Player caster, ItemStack hand) {
        long now = System.currentTimeMillis();
        long until = sheathCooldown.getOrDefault(caster.getUniqueId(), 0L);
        if (now < until) {
            caster.sendMessage(Component.text("칼집 소환 쿨타임입니다.", NamedTextColor.RED));
            return;
        }
        if (hand.getAmount() < 1) return;
        if (Disruptor.tryFailSkill(plugin, caster)) return;

        hand.setAmount(hand.getAmount() - 1);
        Map<Integer, ItemStack> leftovers = caster.getInventory().addItem(new ItemStack(Material.ENCHANTING_TABLE, 1));
        if (!leftovers.isEmpty()) {
            for (ItemStack item : leftovers.values()) {
                caster.getWorld().dropItemNaturally(caster.getLocation(), item);
            }
        }

        sheathCooldown.put(caster.getUniqueId(), now + SHEATH_COOLDOWN_MS);
        for (Entity entity : caster.getNearbyEntities(20, 20, 20)) {
            if (!(entity instanceof Player target)) continue;
            if (isSameGuild(caster, target)) continue;
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 2, false, false));
        }
        caster.sendMessage(Component.text("[!] 칼집 소환 발동", NamedTextColor.AQUA));
    }

    private void executePhantom(Player caster) {
        for (Entity entity : caster.getNearbyEntities(PHANTOM_RANGE, PHANTOM_RANGE, PHANTOM_RANGE)) {
            if (!(entity instanceof Player target)) continue;
            if (isSameGuild(caster, target)) continue;

            applySwordStrike(caster, target);
            target.damage(PHANTOM_BONUS_DAMAGE, caster);
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, PHANTOM_DEBUFF_TICKS, 4, false, false));
        }
        caster.sendMessage(Component.text("[!] 팬텀 발동", NamedTextColor.LIGHT_PURPLE));
    }

    private void applySwordStrike(Player attacker, Player target) {
        Vector knock = target.getLocation().toVector().subtract(attacker.getLocation().toVector());
        knock.setY(0);
        if (knock.lengthSquared() < 1.0E-4) {
            knock = attacker.getLocation().getDirection().setY(0);
        }
        if (knock.lengthSquared() < 1.0E-4) {
            knock = new Vector(0, 0, 1);
        }
        target.setVelocity(knock.normalize().multiply(SWORD_KNOCKBACK_POWER).setY((double) SWORD_KNOCKBACK_UP / 100.0));
        addBleedStack(target, attacker);
    }

    private void addBleedStack(Player target, Player source) {
        UUID targetId = target.getUniqueId();
        List<BleedStack> stacks = bleedStacks.computeIfAbsent(targetId, k -> new ArrayList<>());
        stacks.add(new BleedStack(source.getUniqueId(), System.currentTimeMillis() + BLEED_STACK_DURATION_MS));
    }

    private void tickBleedStacks() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, List<BleedStack>>> iterator = bleedStacks.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, List<BleedStack>> entry = iterator.next();
            Player target = Bukkit.getPlayer(entry.getKey());
            if (target == null || !target.isOnline() || target.isDead()) {
                iterator.remove();
                continue;
            }

            List<BleedStack> stacks = entry.getValue();
            stacks.removeIf(stack -> now >= stack.expiresAt());
            if (stacks.isEmpty()) {
                iterator.remove();
                continue;
            }

            Map<UUID, Integer> byOwner = new HashMap<>();
            for (BleedStack stack : stacks) {
                byOwner.merge(stack.ownerId(), 1, Integer::sum);
            }

            for (Map.Entry<UUID, Integer> ownerDamage : byOwner.entrySet()) {
                int count = ownerDamage.getValue();
                if (count <= 0) continue;

                double damage = BLEED_DAMAGE_PER_SECOND * count;
                Player owner = Bukkit.getPlayer(ownerDamage.getKey());
                if (owner != null && owner.isOnline() && !owner.equals(target)) {
                    target.damage(damage, owner);
                } else {
                    target.damage(damage);
                }
            }
        }
    }

    private void castMagicOrbFan(Player caster) {
        Vector base = caster.getEyeLocation().getDirection().normalize();
        int count = Math.max(1, plugin.getAbilityConfigManager()
                .getInt(getName(), "stats.magic-orb-fan-count", MAGIC_ORB_FAN_COUNT));
        double spread = Math.max(0.0, plugin.getAbilityConfigManager()
                .getDouble(getName(), "stats.magic-orb-fan-spread", MAGIC_ORB_FAN_SPREAD));
        if (count == 1) {
            launchMagicOrb(caster, base);
            return;
        }

        double step = spread / (count - 1);
        double start = -spread / 2.0;
        for (int i = 0; i < count; i++) {
            double yawOffset = start + (step * i);
            Vector dir = rotateYaw(base, yawOffset).normalize();
            launchMagicOrb(caster, dir);
        }
    }

    private void launchMagicOrb(Player caster, Vector direction) {
        DragonFireball orb = caster.launchProjectile(DragonFireball.class);
        Vector normalized = direction.lengthSquared() < 1.0E-4 ? caster.getEyeLocation().getDirection().normalize() : direction.normalize();
        orb.setVelocity(normalized.multiply(MAGIC_ORB_SPEED));
        orb.setIsIncendiary(false);
        orb.setYield(0.0f);
        orb.getPersistentDataContainer().set(orbTagKey, PersistentDataType.BYTE, (byte) 1);
        orb.getPersistentDataContainer().set(orbOwnerKey, PersistentDataType.STRING, caster.getUniqueId().toString());
    }

    private void applyMagicOrbExplosion(Player owner, Player directHit, Location impact) {
        if (impact.getWorld() == null) return;

        impact.getWorld().spawnParticle(Particle.DRAGON_BREATH, impact, 40, 0.6, 0.4, 0.6, 0.0);
        impact.getWorld().spawnParticle(Particle.EXPLOSION, impact, 2, 0.2, 0.1, 0.2, 0.0);

        Set<UUID> damaged = new HashSet<>();
        if (directHit != null && owner != null && !isSameGuild(owner, directHit)) {
            directHit.damage(MAGIC_ORB_DIRECT_DAMAGE, owner);
            damaged.add(directHit.getUniqueId());
        } else if (directHit != null && owner == null) {
            directHit.damage(MAGIC_ORB_DIRECT_DAMAGE);
            damaged.add(directHit.getUniqueId());
        }

        double splashSq = MAGIC_ORB_SPLASH_RADIUS * MAGIC_ORB_SPLASH_RADIUS;
        for (Entity entity : impact.getWorld().getNearbyEntities(impact, MAGIC_ORB_SPLASH_RADIUS, MAGIC_ORB_SPLASH_RADIUS, MAGIC_ORB_SPLASH_RADIUS)) {
            if (!(entity instanceof Player target)) continue;
            if (damaged.contains(target.getUniqueId())) continue;
            if (target.getLocation().distanceSquared(impact) > splashSq) continue;
            if (owner != null && isSameGuild(owner, target)) continue;

            if (owner != null) {
                target.damage(MAGIC_ORB_SPLASH_DAMAGE, owner);
            } else {
                target.damage(MAGIC_ORB_SPLASH_DAMAGE);
            }
        }
    }

    private void removeNearbyDragonClouds(Location impact) {
        if (impact.getWorld() == null) return;
        for (Entity entity : impact.getWorld().getNearbyEntities(impact, 3, 3, 3)) {
            if (entity instanceof AreaEffectCloud cloud && cloud.getParticle() == Particle.DRAGON_BREATH) {
                cloud.remove();
            }
        }
    }

    private Location getImpactLocation(ProjectileHitEvent event, DragonFireball orb) {
        if (event.getHitEntity() != null) {
            return event.getHitEntity().getLocation().add(0, 1.0, 0);
        }
        if (event.getHitBlock() != null) {
            return event.getHitBlock().getLocation().add(0.5, 0.5, 0.5);
        }
        return orb.getLocation();
    }

    private void startPerfectCircle(Player owner, Location tableBlockLoc) {
        Location normalizedBlock = normalizeBlockLocation(tableBlockLoc);
        Location center = normalizedBlock.clone().add(0.5, 0.5, 0.5);

        PerfectCircle circle = new PerfectCircle(
                owner.getUniqueId(),
                normalizedBlock,
                center,
                PERFECT_CIRCLE_RADIUS,
                System.currentTimeMillis() + PERFECT_CIRCLE_DURATION_MS
        );
        activeCircles.put(owner.getUniqueId(), circle);
        owner.sendMessage(Component.text("[!] 완벽한 마법진 전개 (6분 유지)", NamedTextColor.GOLD));
    }

    private void tickPerfectCircles() {
        long now = System.currentTimeMillis();
        if (activeCircles.isEmpty()) return;

        Iterator<Map.Entry<UUID, PerfectCircle>> iterator = activeCircles.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, PerfectCircle> entry = iterator.next();
            PerfectCircle circle = entry.getValue();

            if (now >= circle.endsAt) {
                iterator.remove();
                endCircle(circle, true);
                continue;
            }

            if (!isEnchantingTableStillPresent(circle.tableBlockLocation)) {
                iterator.remove();
                endCircle(circle, true);
                continue;
            }

            applyCircleEffects(circle, now);
        }
    }

    private void applyCircleEffects(PerfectCircle circle, long now) {
        Player owner = Bukkit.getPlayer(circle.ownerId);
        double radiusSq = (double) circle.radius * circle.radius;

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!Objects.equals(player.getWorld(), circle.center.getWorld())) continue;

            boolean inside = player.getLocation().distanceSquared(circle.center) <= radiusSq;
            if (inside) {
                circle.trappedPlayers.add(player.getUniqueId());
                circle.outsidePenaltyAt.remove(player.getUniqueId());

                player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 40, 0, false, false));
                if (player.getGameMode() != GameMode.SPECTATOR) {
                    if (player.isFlying()) player.setFlying(false);
                    if (player.getAllowFlight()) player.setAllowFlight(false);
                }

                if (owner != null && owner.getUniqueId().equals(player.getUniqueId())) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 40, 0, false, false));
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 40, 0, false, false));
                    if (now >= circle.nextRegenPulseAt) {
                        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 60, 2, false, false));
                        circle.nextRegenPulseAt = now + 10_000L;
                    }
                } else if (owner != null && !isSameGuild(owner, player)) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 0, false, false));
                    player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 40, 0, false, false));
                }
                continue;
            }

            if (!circle.trappedPlayers.contains(player.getUniqueId())) continue;
            long penaltyAt = circle.outsidePenaltyAt.getOrDefault(player.getUniqueId(), 0L);
            if (now < penaltyAt) continue;

            double damage = OUTSIDE_PENALTY_HEARTS;
            if (owner != null && owner.isOnline() && !owner.equals(player)) {
                player.damage(damage, owner);
            } else {
                player.damage(damage);
            }
            circle.outsidePenaltyAt.put(player.getUniqueId(), now + OUTSIDE_PENALTY_INTERVAL_MS);
        }
    }

    private boolean isEnchantingTableStillPresent(Location blockLocation) {
        if (blockLocation.getWorld() == null) return false;
        return blockLocation.getBlock().getType() == Material.ENCHANTING_TABLE;
    }

    private void endCirclesForExplodedBlocks(List<org.bukkit.block.Block> blocks) {
        if (activeCircles.isEmpty()) return;

        List<PerfectCircle> toEnd = new ArrayList<>();
        for (org.bukkit.block.Block block : blocks) {
            if (block.getType() != Material.ENCHANTING_TABLE) continue;
            PerfectCircle circle = findCircleByTable(block.getLocation());
            if (circle != null) {
                toEnd.add(circle);
            }
        }

        for (PerfectCircle circle : toEnd) {
            activeCircles.remove(circle.ownerId);
            endCircle(circle, true);
        }
    }

    private void endCircle(PerfectCircle circle, boolean startCooldown) {
        Player owner = Bukkit.getPlayer(circle.ownerId);
        if (startCooldown) {
            perfectCircleCooldown.put(circle.ownerId, System.currentTimeMillis() + PERFECT_CIRCLE_COOLDOWN_MS);
        }
        if (owner != null && owner.isOnline()) {
            owner.sendMessage(Component.text("[!] 완벽한 마법진 해제", NamedTextColor.GRAY));
        }
    }

    private PerfectCircle findCircleByTable(Location blockLocation) {
        Location normalized = normalizeBlockLocation(blockLocation);
        for (PerfectCircle circle : activeCircles.values()) {
            if (!Objects.equals(circle.tableBlockLocation.getWorld(), normalized.getWorld())) continue;
            if (circle.tableBlockLocation.getBlockX() != normalized.getBlockX()) continue;
            if (circle.tableBlockLocation.getBlockY() != normalized.getBlockY()) continue;
            if (circle.tableBlockLocation.getBlockZ() != normalized.getBlockZ()) continue;
            return circle;
        }
        return null;
    }

    private boolean isOutsideAllCircles(Location location) {
        for (PerfectCircle circle : activeCircles.values()) {
            if (isInsideCircle(circle, location)) return false;
        }
        return true;
    }

    private Collection<PerfectCircle> getCirclesContaining(Location location) {
        List<PerfectCircle> matched = new ArrayList<>();
        for (PerfectCircle circle : activeCircles.values()) {
            if (isInsideCircle(circle, location)) {
                matched.add(circle);
            }
        }
        return matched;
    }

    private boolean isInsideCircle(PerfectCircle circle, Location location) {
        if (location == null) return false;
        if (!Objects.equals(location.getWorld(), circle.center.getWorld())) return false;
        return location.distanceSquared(circle.center) <= (double) circle.radius * circle.radius;
    }

    private Location clampToBoundary(PerfectCircle circle, Location to, Location fallback) {
        if (!Objects.equals(circle.center.getWorld(), to.getWorld())) {
            return fallback;
        }

        Vector offset = to.toVector().subtract(circle.center.toVector());
        if (offset.lengthSquared() < 1.0E-4) {
            offset = fallback.toVector().subtract(circle.center.toVector());
        }
        if (offset.lengthSquared() < 1.0E-4) {
            offset = new Vector(1, 0, 0);
        }

        double maxDist = Math.max(1.0, circle.radius - 0.35);
        Vector clamped = offset.normalize().multiply(maxDist);
        Location result = circle.center.clone().add(clamped);
        result.setY(to.getY());
        result.setYaw(to.getYaw());
        result.setPitch(to.getPitch());
        return result;
    }

    private boolean isTeleportCommand(String root) {
        if (BLOCKED_TP_COMMANDS.contains(root)) return true;
        return root.startsWith("tp");
    }

    private AttackForm getForm(Player player) {
        return forms.getOrDefault(player.getUniqueId(), AttackForm.SWORD);
    }

    private void setForm(Player player, AttackForm form) {
        forms.put(player.getUniqueId(), form);
        if (form == AttackForm.MAGIC) {
            player.sendActionBar(Component.text("일반공격 폼: 마법구", NamedTextColor.AQUA));
        } else {
            player.sendActionBar(Component.text("일반공격 폼: 검술", NamedTextColor.GOLD));
        }
    }

    private Vector rotateYaw(Vector vector, double degrees) {
        double rad = Math.toRadians(degrees);
        double cos = Math.cos(rad);
        double sin = Math.sin(rad);
        double x = (vector.getX() * cos) - (vector.getZ() * sin);
        double z = (vector.getX() * sin) + (vector.getZ() * cos);
        return new Vector(x, vector.getY(), z);
    }

    private boolean isNotMagicOrb(DragonFireball orb) {
        Byte marker = orb.getPersistentDataContainer().get(orbTagKey, PersistentDataType.BYTE);
        return marker == null || marker != (byte) 1;
    }

    private UUID parseOwnerId(DragonFireball orb) {
        String raw = orb.getPersistentDataContainer().get(orbOwnerKey, PersistentDataType.STRING);
        if (raw == null || raw.isBlank()) {
            ProjectileSource shooter = orb.getShooter();
            if (shooter instanceof Player player) {
                return player.getUniqueId();
            }
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private Location normalizeBlockLocation(Location location) {
        return new Location(
                location.getWorld(),
                location.getBlockX(),
                location.getBlockY(),
                location.getBlockZ()
        );
    }

    private boolean isSameGuild(Player p1, Player p2) {
        if (p1.equals(p2)) return true;
        GuildData g1 = guildManager.getGuildByMember(p1.getUniqueId());
        GuildData g2 = guildManager.getGuildByMember(p2.getUniqueId());
        return g1 != null && g2 != null && g1.name.equalsIgnoreCase(g2.name);
    }

    private boolean lacksAbility(Player player) {
        return !getName().equals(plugin.getAbilityManager().getPlayerData(player.getUniqueId()).getAbilityName());
    }

    private enum AttackForm {
        SWORD,
        MAGIC
    }

    private boolean isGrounded(Player player) {
        World world = player.getWorld();
        Location location = player.getLocation();
        double x = location.getX();
        double y = location.getY() - 0.05;
        double z = location.getZ();
        return isSolidBelow(world, x - 0.3, y, z - 0.3)
                || isSolidBelow(world, x + 0.3, y, z - 0.3)
                || isSolidBelow(world, x - 0.3, y, z + 0.3)
                || isSolidBelow(world, x + 0.3, y, z + 0.3);
    }

    private boolean isSolidBelow(World world, double x, double y, double z) {
        return !world.getBlockAt((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z)).isPassable();
    }

    private record BleedStack(UUID ownerId, long expiresAt) {
    }

    private static final class PendingBlinkLanding {
        private final long activateAt;
        private boolean leftGround;

        private PendingBlinkLanding(long activateAt) {
            this.activateAt = activateAt;
            this.leftGround = false;
        }
    }

    private static final class PerfectCircle {
        private final UUID ownerId;
        private final Location tableBlockLocation;
        private final Location center;
        private final int radius;
        private final long endsAt;
        private long nextRegenPulseAt;
        private final Set<UUID> trappedPlayers = new HashSet<>();
        private final Map<UUID, Long> outsidePenaltyAt = new HashMap<>();

        private PerfectCircle(UUID ownerId, Location tableBlockLocation, Location center, int radius, long endsAt) {
            this.ownerId = ownerId;
            this.tableBlockLocation = tableBlockLocation;
            this.center = center;
            this.radius = radius;
            this.endsAt = endsAt;
            this.nextRegenPulseAt = System.currentTimeMillis();
        }
    }
}
