package org.kkaemok.reAbility.ability.list;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.kkaemok.reAbility.ReAbility;
import org.kkaemok.reAbility.ability.AbilityBase;
import org.kkaemok.reAbility.ability.AbilityGrade;
import org.kkaemok.reAbility.guild.GuildData;
import org.kkaemok.reAbility.guild.GuildManager;
import org.kkaemok.reAbility.utils.SkillParticles;

import java.util.*;

public class Chronos extends AbilityBase {
    private static final int PASSIVE_RANGE = 100;
    private static final long ROAR_COOLDOWN_MS = 60_000L;
    private static final long WISDOM_COOLDOWN_MS = 3_600_000L;
    private static final long SEVERANCE_COOLDOWN_MS = 10_800_000L;
    private static final int ROAR_DAMAGE = 30;
    private static final int ROAR_DEBUFF_TICKS = 200;
    private static final int WISDOM_REWARD_SCRAP = 1;
    private static final int SEVERANCE_COST = 4;
    private static final int SEVERANCE_CHARGE_SECONDS = 30;
    private static final int SEVERANCE_DURATION_TICKS = 1200;

    private final ReAbility plugin;
    private final GuildManager guildManager;
    private final Map<UUID, Long> roarCooldown = new HashMap<>();
    private final Map<UUID, Long> wisdomCooldown = new HashMap<>();
    private final Map<UUID, Long> stopCooldown = new HashMap<>();
    private final Set<UUID> frozenPlayers = new HashSet<>();
    private final Map<UUID, BukkitTask> passiveTasks = new HashMap<>();
    private long timeStopEndsAt = 0L;

    public Chronos(ReAbility plugin) {
        this.plugin = plugin;
        this.guildManager = plugin.getGuildManager();
    }

    @Override public String getName() { return "CHRONOS"; }
    @Override public String getDisplayName() { return "크로노스"; }
    @Override public AbilityGrade getGrade() { return AbilityGrade.SS; }

    @Override
    public String[] getDescription() {
        return new String[]{
                "패시브: 힘 2, 재생 2 상시 획득.",
                "100칸 내 길드원이 있으면 힘 1, 재생 1로 약화.",
                "스킬 {시간의 포효}: 네더라이트 주괴 1개 웅크리기",
                "100칸 내 적에게 피해 30 + 구속2, 멀미, 나약함2, 어둠, 허기20 (10초)",
                "(쿨타임 1분)",
                "스킬 {카이로스의 지혜}: 네더라이트 파편 1개 획득 (쿨타임 1시간)",
                "스킬 {시간 단절}: 네더라이트 주괴 4개 우클릭",
                "30초 의식 후 1분간 자신 제외 모두 경직 (길드원 포함, 쿨타임 3시간)",
                "* 경직 중 이동/공격 불가, 황금사과/토템 사용 가능"
        };
    }

    @Override
    public void onActivate(Player player) {
        startPassiveTask(player);
    }

    @Override
    public void onDeactivate(Player player) {
        BukkitTask task = passiveTasks.remove(player.getUniqueId());
        if (task != null) task.cancel();
        clearTimeStop();
        player.removePotionEffect(PotionEffectType.STRENGTH);
        player.removePotionEffect(PotionEffectType.REGENERATION);
        player.removePotionEffect(PotionEffectType.RESISTANCE);
        player.removePotionEffect(PotionEffectType.SPEED);
    }

    private void startPassiveTask(Player chronos) {
        BukkitTask existing = passiveTasks.remove(chronos.getUniqueId());
        if (existing != null) existing.cancel();

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!chronos.isOnline() || !isHasAbility(chronos)) {
                    BukkitTask current = passiveTasks.remove(chronos.getUniqueId());
                    if (current != null) current.cancel();
                    return;
                }

                boolean guildMemberNearby = chronos.getNearbyEntities(PASSIVE_RANGE, PASSIVE_RANGE, PASSIVE_RANGE).stream()
                        .filter(e -> e instanceof Player)
                        .map(e -> (Player) e)
                        .anyMatch(p -> !p.equals(chronos) && isSameGuild(chronos, p));

                if (!guildMemberNearby) {
                    chronos.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 40, 1, false, false));
                    chronos.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 40, 1, false, false));
                } else {
                    chronos.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 40, 0, false, false));
                    chronos.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 40, 0, false, false));
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
        passiveTasks.put(chronos.getUniqueId(), task);
    }

    @Override
    public void onSneakSkill(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        long now = System.currentTimeMillis();

        if (item.getType() == Material.NETHERITE_INGOT) {
            if (!checkCooldown(player, roarCooldown, now)) return;
            if (!consumeFromHand(player, Material.NETHERITE_INGOT, 1)) return;

            roarCooldown.put(player.getUniqueId(), now + ROAR_COOLDOWN_MS);
            SkillParticles.chronosRoar(player);
            executeRoar(player);
            broadcastSkill(player, "{시간의 포효}");
            return;
        }

        if (checkCooldown(player, wisdomCooldown, now)) {
            wisdomCooldown.put(player.getUniqueId(), now + WISDOM_COOLDOWN_MS);
            player.getInventory().addItem(new ItemStack(Material.NETHERITE_SCRAP, WISDOM_REWARD_SCRAP));
            SkillParticles.chronosWisdom(player);
            player.sendMessage(Component.text("[!] 카이로스의 지혜로 네더라이트 파편 " + WISDOM_REWARD_SCRAP + "개를 획득했습니다.",
                    NamedTextColor.GOLD));
        }
    }

    @EventHandler
    public void onRightClick(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!event.getAction().isRightClick()) return;
        if (shouldIgnoreSneakRightClickBlock(event)) return;

        Player player = event.getPlayer();
        if (!isHasAbility(player)) return;

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType() != Material.NETHERITE_INGOT || item.getAmount() < SEVERANCE_COST) return;
        if (Disruptor.tryFailSkill(plugin, player)) return;

        long now = System.currentTimeMillis();
        if (!checkCooldown(player, stopCooldown, now)) return;
        if (!consumeFromHand(player, Material.NETHERITE_INGOT, SEVERANCE_COST)) return;

        stopCooldown.put(player.getUniqueId(), now + SEVERANCE_COOLDOWN_MS);
        SkillParticles.chronosSeveranceCharge(player);
        startTimeSeverance(player);
        broadcastSkill(player, "{시간 단절}");
    }

    private void executeRoar(Player chronos) {
        chronos.getNearbyEntities(PASSIVE_RANGE, PASSIVE_RANGE, PASSIVE_RANGE).stream()
                .filter(e -> e instanceof Player)
                .map(e -> (Player) e)
                .filter(p -> !isSameGuild(chronos, p))
                .forEach(target -> {
                    target.damage(ROAR_DAMAGE, chronos);
                    target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, ROAR_DEBUFF_TICKS, 1));
                    target.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, ROAR_DEBUFF_TICKS, 0));
                    target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, ROAR_DEBUFF_TICKS, 1));
                    target.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, ROAR_DEBUFF_TICKS, 0));
                    target.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, ROAR_DEBUFF_TICKS, 19));
                });
    }

    private void startTimeSeverance(Player chronos) {
        Bukkit.broadcast(Component.text(SEVERANCE_CHARGE_SECONDS + "초 뒤 SS등급 크로노스의 시간 단절 스킬이 발동됩니다.",
                NamedTextColor.RED));

        new BukkitRunnable() {
            int count = SEVERANCE_CHARGE_SECONDS;
            @Override
            public void run() {
                if (!chronos.isOnline() || !isHasAbility(chronos)) {
                    this.cancel();
                    return;
                }
                if (count > 0) {
                    if (count <= 5 && chronos.isOnline()) {
                        chronos.sendMessage(Component.text(count + "...", NamedTextColor.YELLOW));
                    }
                    count--;
                } else {
                    applyTimeStop(chronos);
                    this.cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void applyTimeStop(Player chronos) {
        long durationMs = SEVERANCE_DURATION_TICKS * 50L;
        long now = System.currentTimeMillis();
        timeStopEndsAt = Math.max(timeStopEndsAt, now + durationMs);

        frozenPlayers.clear();
        Bukkit.getOnlinePlayers().stream()
                .filter(p -> !p.equals(chronos))
                .filter(p -> !isMichaelJack(p))
                .forEach(p -> frozenPlayers.add(p.getUniqueId()));

        SkillParticles.chronosSeveranceBurst(chronos);
        Bukkit.broadcast(Component.text("[!] 시간 단절 발동! 1분 동안 크로노스 외 모두가 경직됩니다.",
                NamedTextColor.AQUA));

        new BukkitRunnable() {
            @Override
            public void run() {
                if (timeStopEndsAt == 0L) return;
                if (System.currentTimeMillis() < timeStopEndsAt) return;
                frozenPlayers.clear();
                timeStopEndsAt = 0L;
                Bukkit.broadcast(Component.text("[!] 시간 단절이 종료되었습니다.", NamedTextColor.GREEN));
            }
        }.runTaskLater(plugin, SEVERANCE_DURATION_TICKS);
    }

    private void clearTimeStop() {
        timeStopEndsAt = 0L;
        frozenPlayers.clear();
    }

    private boolean ensureTimeStopActive() {
        if (timeStopEndsAt == 0L) {
            frozenPlayers.clear();
            return false;
        }
        if (System.currentTimeMillis() >= timeStopEndsAt) {
            frozenPlayers.clear();
            timeStopEndsAt = 0L;
            return false;
        }
        return true;
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (isMichaelJack(event.getPlayer())) {
            frozenPlayers.remove(event.getPlayer().getUniqueId());
            return;
        }
        if (frozenPlayers.contains(event.getPlayer().getUniqueId()) && ensureTimeStopActive()) {
            Location from = event.getFrom();
            Location to = event.getTo();
            if (to == null) return;
            if (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ()) {
                event.setTo(event.getFrom());
            }
        }
    }

    @EventHandler
    public void onAttack(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player attacker
                && !isMichaelJack(attacker)
                && frozenPlayers.contains(attacker.getUniqueId())
                && ensureTimeStopActive()) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        ProjectileSource shooter = event.getEntity().getShooter();
        if (!(shooter instanceof Player player)) return;
        if (isMichaelJack(player)) {
            frozenPlayers.remove(player.getUniqueId());
            return;
        }
        if (!frozenPlayers.contains(player.getUniqueId())) return;
        if (!ensureTimeStopActive()) return;
        event.setCancelled(true);
    }

    private boolean checkCooldown(Player p, Map<UUID, Long> map, long now) {
        if (now < map.getOrDefault(p.getUniqueId(), 0L)) {
            p.sendMessage(Component.text("쿨타임 중입니다.", NamedTextColor.RED));
            return false;
        }
        return true;
    }

    private boolean consumeFromHand(Player player, Material type, int amount) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType() != type || hand.getAmount() < amount) return false;
        hand.setAmount(hand.getAmount() - amount);
        return true;
    }

    private boolean isSameGuild(Player p1, Player p2) {
        if (p1.equals(p2)) return true;
        GuildData g1 = guildManager.getGuildByMember(p1.getUniqueId());
        GuildData g2 = guildManager.getGuildByMember(p2.getUniqueId());
        return g1 != null && g2 != null && g1.name.equalsIgnoreCase(g2.name);
    }

    private void broadcastSkill(Player player, String name) {
        Bukkit.broadcast(Component.text("[SS] 크로노스 " + player.getName() + " - " + name,
                NamedTextColor.LIGHT_PURPLE));
    }

    private boolean isHasAbility(Player player) {
        return getName().equals(plugin.getAbilityManager().getPlayerData(player.getUniqueId()).getAbilityName());
    }

    private boolean isMichaelJack(Player player) {
        String ability = plugin.getAbilityManager().getPlayerData(player.getUniqueId()).getAbilityName();
        return "MICHAEL_JACK".equals(ability);
    }

}
