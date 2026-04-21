package org.kkaemok.reAbility.ability.list;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.kkaemok.reAbility.ReAbility;
import org.kkaemok.reAbility.ability.AbilityBase;
import org.kkaemok.reAbility.ability.AbilityGrade;
import org.kkaemok.reAbility.guild.GuildData;
import org.kkaemok.reAbility.guild.GuildManager;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class Fairy extends AbilityBase {
    private static final int FLOWER_COST = 5;
    private static final long DEFAULT_FLOWER_COOLDOWN_MS = 15000L;
    private static final int DEFAULT_FLOWER_DURATION_TICKS = 200;
    private static final int DEFAULT_FLIGHT_RANGE = 3;
    private static final int DEFAULT_DESPERATE_FLIGHT_RANGE = 5;
    private static final double DEFAULT_DESPERATE_HEALTH_THRESHOLD = 10.0;
    private static final float DEFAULT_SLOW_FLY_SPEED = 0.05f;
    private static final float DEFAULT_FAST_FLY_SPEED = 0.10f;
    private static final float DEFAULT_NORMAL_FLY_SPEED = 0.10f;

    private final ReAbility plugin;
    private final GuildManager guildManager;
    private final Random random = new Random();
    private final Map<UUID, Long> flowerCooldown = new HashMap<>();
    private final Map<UUID, BukkitTask> passiveTasks = new HashMap<>();
    private final Map<UUID, Float> previousFlySpeed = new HashMap<>();

    public Fairy(ReAbility plugin) {
        this.plugin = plugin;
        this.guildManager = plugin.getGuildManager();
    }

    @Override
    public String getName() {
        return "FAIRY";
    }

    @Override
    public String getDisplayName() {
        return "요정";
    }

    @Override
    public AbilityGrade getGrade() {
        return AbilityGrade.B;
    }

    @Override
    public String[] getDescription() {
        return new String[]{
                "3칸 아래에 블럭이 있으면 느린 비행이 가능합니다.",
                "체력 5칸(10) 이하일 때 나약함 2를 얻고 비행 가능 범위가 5칸으로 늘어나며 빠른 비행을 합니다.",
                "스킬 {치유의 꽃}: 꽃 5개를 들고 길드원 우클릭(허공 우클릭 시 자신) 시",
                "10초 동안 무작위 버프 1개를 부여합니다. (힘2, 재생2, 저항2, 스피드3, 노란하트 10개, 쿨타임 15초)"
        };
    }

    @Override
    public void onActivate(Player player) {
        previousFlySpeed.putIfAbsent(player.getUniqueId(), player.getFlySpeed());
        startPassiveTask(player);
    }

    @Override
    public void onDeactivate(Player player) {
        UUID uuid = player.getUniqueId();
        BukkitTask task = passiveTasks.remove(uuid);
        if (task != null) {
            task.cancel();
        }

        flowerCooldown.remove(uuid);
        removeFairyWeakness(player);

        Float prevSpeed = previousFlySpeed.remove(uuid);
        player.setFlySpeed(prevSpeed != null ? clampFlySpeed(prevSpeed) : DEFAULT_NORMAL_FLY_SPEED);

        if (!isCreativeLike(player)) {
            player.setFlying(false);
            player.setAllowFlight(false);
        }
    }

    @EventHandler
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Player caster = event.getPlayer();
        if (!isHasAbility(caster)) return;
        if (!(event.getRightClicked() instanceof Player target)) return;
        if (!isFlower(caster.getInventory().getItemInMainHand().getType())) return;

        event.setCancelled(true);
        if (!isSameGuild(caster, target)) {
            caster.sendMessage(Component.text("길드원에게만 사용할 수 있습니다.", NamedTextColor.RED));
            return;
        }
        useHealingFlower(caster, target);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR) return;

        Player caster = event.getPlayer();
        if (!isHasAbility(caster)) return;
        useHealingFlower(caster, caster);
    }

    private void startPassiveTask(Player fairy) {
        UUID uuid = fairy.getUniqueId();
        BukkitTask existing = passiveTasks.remove(uuid);
        if (existing != null) {
            existing.cancel();
        }

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!fairy.isOnline() || !isHasAbility(fairy)) {
                    BukkitTask current = passiveTasks.get(uuid);
                    if (current != null && current.getTaskId() == this.getTaskId()) {
                        passiveTasks.remove(uuid);
                    }
                    cancel();
                    return;
                }
                if (fairy.isDead() || isCreativeLike(fairy)) {
                    removeFairyWeakness(fairy);
                    return;
                }
                applyPassive(fairy);
            }
        }.runTaskTimer(plugin, 0L, 2L);
        passiveTasks.put(uuid, task);
    }

    private void applyPassive(Player fairy) {
        int normalRange = Math.max(1, plugin.getAbilityConfigManager()
                .getInt(getName(), "stats.flight-range", DEFAULT_FLIGHT_RANGE));
        int desperateRange = Math.max(normalRange, plugin.getAbilityConfigManager()
                .getInt(getName(), "stats.desperate-flight-range", DEFAULT_DESPERATE_FLIGHT_RANGE));
        double desperateThreshold = Math.max(0.5, plugin.getAbilityConfigManager()
                .getDouble(getName(), "stats.desperate-health-threshold", DEFAULT_DESPERATE_HEALTH_THRESHOLD));
        float slowFlySpeed = clampFlySpeed((float) plugin.getAbilityConfigManager()
                .getDouble(getName(), "stats.slow-fly-speed", DEFAULT_SLOW_FLY_SPEED));
        float fastFlySpeed = clampFlySpeed((float) plugin.getAbilityConfigManager()
                .getDouble(getName(), "stats.fast-fly-speed", DEFAULT_FAST_FLY_SPEED));

        boolean desperate = fairy.getHealth() <= desperateThreshold;
        int allowedRange = desperate ? desperateRange : normalRange;
        boolean hasGround = hasBlockBelow(fairy, allowedRange);

        if (desperate) {
            fairy.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 40, 1, false, false, true));
        } else {
            removeFairyWeakness(fairy);
        }

        if (hasGround) {
            if (!fairy.getAllowFlight()) {
                fairy.setAllowFlight(true);
            }
            float speed = desperate ? fastFlySpeed : slowFlySpeed;
            if (Math.abs(fairy.getFlySpeed() - speed) > 0.0001f) {
                fairy.setFlySpeed(speed);
            }
            return;
        }

        if (fairy.isFlying()) {
            fairy.setFlying(false);
        }
        if (fairy.getAllowFlight()) {
            fairy.setAllowFlight(false);
        }
    }

    private boolean hasBlockBelow(Player player, int depth) {
        Location base = player.getLocation();
        for (int i = 1; i <= depth; i++) {
            if (!base.clone().subtract(0, i, 0).getBlock().isPassable()) {
                return true;
            }
        }
        return false;
    }

    private void useHealingFlower(Player caster, Player target) {
        ItemStack hand = caster.getInventory().getItemInMainHand();
        if (!isFlower(hand.getType())) return;

        if (hand.getAmount() < FLOWER_COST) {
            caster.sendMessage(Component.text("꽃이 5개 이상 필요합니다.", NamedTextColor.RED));
            return;
        }

        long now = System.currentTimeMillis();
        long cooldownUntil = flowerCooldown.getOrDefault(caster.getUniqueId(), 0L);
        if (now < cooldownUntil) {
            long remainSec = Math.max(1L, (cooldownUntil - now + 999L) / 1000L);
            caster.sendMessage(Component.text("치유의 꽃 쿨타임: " + remainSec + "초", NamedTextColor.RED));
            return;
        }

        if (Disruptor.tryFailSkill(plugin, caster)) return;

        hand.setAmount(hand.getAmount() - FLOWER_COST);
        long cooldownMs = plugin.getAbilityConfigManager()
                .getLong(getName(), "skills.healing-flower.cooldown-ms", DEFAULT_FLOWER_COOLDOWN_MS);
        int durationTicks = plugin.getAbilityConfigManager()
                .getInt(getName(), "skills.healing-flower.duration-ticks", DEFAULT_FLOWER_DURATION_TICKS);
        flowerCooldown.put(caster.getUniqueId(), now + cooldownMs);

        HealingFlowerEffect effect = HealingFlowerEffect.pick(random);
        effect.apply(target, durationTicks);

        if (caster.equals(target)) {
            caster.sendMessage(Component.text("[!] 치유의 꽃: " + effect.label + " 효과를 얻었습니다.", NamedTextColor.GREEN));
        } else {
            caster.sendMessage(Component.text("[!] 치유의 꽃: " + target.getName() + "에게 "
                    + effect.label + " 효과를 부여했습니다.", NamedTextColor.AQUA));
            target.sendMessage(Component.text("[!] " + caster.getName() + "의 치유의 꽃으로 "
                    + effect.label + " 효과를 얻었습니다.", NamedTextColor.GREEN));
        }
    }

    private boolean isFlower(Material type) {
        return Tag.FLOWERS.isTagged(type);
    }

    private void removeFairyWeakness(Player player) {
        PotionEffect weakness = player.getPotionEffect(PotionEffectType.WEAKNESS);
        if (weakness == null) return;
        if (weakness.getAmplifier() != 1) return;
        if (weakness.getDuration() > 60) return;
        player.removePotionEffect(PotionEffectType.WEAKNESS);
    }

    private float clampFlySpeed(float value) {
        return Math.max(-1.0f, Math.min(1.0f, value));
    }

    private boolean isCreativeLike(Player player) {
        return player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR;
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

    private enum HealingFlowerEffect {
        STRENGTH("힘 2", PotionEffectType.STRENGTH, 1),
        REGENERATION("재생 2", PotionEffectType.REGENERATION, 1),
        RESISTANCE("저항 2", PotionEffectType.RESISTANCE, 1),
        SPEED("스피드 3", PotionEffectType.SPEED, 2),
        ABSORPTION("노란하트 10개", PotionEffectType.ABSORPTION, 4);

        private static final HealingFlowerEffect[] VALUES = values();

        private final String label;
        private final PotionEffectType effectType;
        private final int amplifier;

        HealingFlowerEffect(String label, PotionEffectType effectType, int amplifier) {
            this.label = label;
            this.effectType = effectType;
            this.amplifier = amplifier;
        }

        private void apply(Player target, int durationTicks) {
            target.addPotionEffect(new PotionEffect(effectType, durationTicks, amplifier, false, false, true));
        }

        private static HealingFlowerEffect pick(Random random) {
            return VALUES[random.nextInt(VALUES.length)];
        }
    }
}
