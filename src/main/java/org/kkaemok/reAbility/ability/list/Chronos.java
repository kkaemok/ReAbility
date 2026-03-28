package org.kkaemok.reAbility.ability.list;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.kkaemok.reAbility.ReAbility;
import org.kkaemok.reAbility.ability.AbilityBase;
import org.kkaemok.reAbility.ability.AbilityGrade;
import org.kkaemok.reAbility.ability.SkillCost;
import org.kkaemok.reAbility.data.PlayerData;
import org.kkaemok.reAbility.guild.GuildData;
import org.kkaemok.reAbility.guild.GuildManager;
import org.kkaemok.reAbility.utils.SkillParticles;

import java.util.*;

public class Chronos extends AbilityBase {
    private final ReAbility plugin;
    private final GuildManager guildManager;
    private final Map<UUID, Long> roarCooldown = new HashMap<>();
    private final Map<UUID, Long> wisdomCooldown = new HashMap<>();
    private final Map<UUID, Long> stopCooldown = new HashMap<>();
    private final Set<UUID> frozenPlayers = new HashSet<>();
    private final Map<UUID, BukkitTask> passiveTasks = new HashMap<>();
    private final Map<UUID, Long> dailyGiftCooldown = new HashMap<>();
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
                "길드원 없음: 힘 4, 재생 3, 저항 1, 속도 1.",
                "길드원 100칸 내: 힘 2, 재생 1, 저항 1.",
                "패시브: 하루에 한 번 길드원에게 능력 1일 유지권 지급(최대 5일).",
                "스킬 {시간의 포효}: 네더라이트 주괴 2개 소모",
                "500칸 내 적에게 피해 100 + 구속3, 멀미, 나약함1, 어둠, 허기3",
                "(쿨타임 10분)",
                "스킬 {카이로스의 지혜}: 네더라이트 파편 2개 + 저항2/재생2 15초 (쿨타임 1시간)",
                "스킬 {시간 단절}: 네더라이트 주괴 3개 소모,",
                "20초 의식 후 1분간 자신 제외 모두 경직 (쿨타임 1시간)",
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
        dailyGiftCooldown.remove(player.getUniqueId());
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

                boolean guildMemberNearby = chronos.getNearbyEntities(100, 100, 100).stream()
                        .filter(e -> e instanceof Player)
                        .map(e -> (Player) e)
                        .anyMatch(p -> !p.equals(chronos) && isSameGuild(chronos, p));

                if (!guildMemberNearby) {
                    chronos.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 40, 3, false, false));
                    chronos.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 40, 2, false, false));
                    chronos.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 40, 0, false, false));
                    chronos.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 40, 0, false, false));
                } else {
                    chronos.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 40, 1, false, false));
                    chronos.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 40, 0, false, false));
                    chronos.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 40, 0, false, false));
                }

                tryGrantDailyGift(chronos);
            }
        }.runTaskTimer(plugin, 0L, 20L);
        passiveTasks.put(chronos.getUniqueId(), task);
    }

    private void tryGrantDailyGift(Player chronos) {
        long now = System.currentTimeMillis();
        long next = dailyGiftCooldown.getOrDefault(chronos.getUniqueId(), 0L);
        if (now < next) return;

        List<Player> recipients = new ArrayList<>();
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (target.equals(chronos)) continue;
            if (!isSameGuild(chronos, target)) continue;
            recipients.add(target);
        }
        if (recipients.isEmpty()) return;

        long dayMs = 24L * 60 * 60 * 1000;
        long maxExpiry = now + (dayMs * 5);
        int granted = 0;

        for (Player target : recipients) {
            PlayerData data = plugin.getAbilityManager().getPlayerData(target.getUniqueId());
            if (data.getAbilityName() == null || data.isExpired()) continue;
            if (data.getExpiryTime() == Long.MAX_VALUE) continue;
            if (data.getExpiryTime() >= maxExpiry) continue;

            long newExpiry = Math.min(data.getExpiryTime() + dayMs, maxExpiry);
            data.setExpiryTime(newExpiry);
            plugin.getAbilityManager().savePlayerData(target.getUniqueId());
            granted++;
        }

        if (granted > 0) {
            dailyGiftCooldown.put(chronos.getUniqueId(), now + dayMs);
        }
    }

    @Override
    public void onSneakSkill(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        long now = System.currentTimeMillis();

        SkillCost roarCost = plugin.getAbilityConfigManager()
                .getSkillCost(getName(), "roar", Material.NETHERITE_INGOT, 2);
        if (roarCost.matchesHand(item)) {
            long cooldownMs = plugin.getAbilityConfigManager()
                    .getLong(getName(), "skills.roar.cooldown-ms", 600000L);
            if (checkCooldown(player, roarCooldown, cooldownMs, now)) {
                if (!roarCost.consumeFromHand(player)) return;
                SkillParticles.chronosRoar(player);
                executeRoar(player);
                broadcastSkill(player, "{시간의 포효}");
            }
            return;
        }

        SkillCost severanceCost = plugin.getAbilityConfigManager()
                .getSkillCost(getName(), "severance", Material.NETHERITE_INGOT, 3);
        if (severanceCost.matchesHand(item)) {
            long cooldownMs = plugin.getAbilityConfigManager()
                    .getLong(getName(), "skills.severance.cooldown-ms", 3600000L);
            if (checkCooldown(player, stopCooldown, cooldownMs, now)) {
                if (!severanceCost.consumeFromHand(player)) return;
                SkillParticles.chronosSeveranceCharge(player);
                startTimeSeverance(player);
                broadcastSkill(player, "{시간 단절}");
            }
            return;
        }

        long wisdomCooldownMs = plugin.getAbilityConfigManager()
                .getLong(getName(), "skills.wisdom.cooldown-ms", 3600000L);
        if (checkCooldown(player, wisdomCooldown, wisdomCooldownMs, now)) {
            int rewardAmount = plugin.getAbilityConfigManager()
                    .getInt(getName(), "skills.wisdom.reward-amount", 2);
            player.getInventory().addItem(new ItemStack(Material.NETHERITE_SCRAP, rewardAmount));
            player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 300, 1, false, false));
            player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 300, 1, false, false));
            SkillParticles.chronosWisdom(player);
            player.sendMessage(Component.text("[!] 카이로스의 지혜로 네더라이트 파편 " + rewardAmount + "개를 획득했습니다.",
                    NamedTextColor.GOLD));
        }
    }

    private void executeRoar(Player chronos) {
        double damage = plugin.getAbilityConfigManager()
                .getDouble(getName(), "skills.roar.damage", 100.0);
        chronos.getNearbyEntities(500, 500, 500).stream()
                .filter(e -> e instanceof Player)
                .map(e -> (Player) e)
                .filter(p -> !isSameGuild(chronos, p))
                .forEach(target -> {
                    target.damage(damage, chronos);
                    target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 400, 2));
                    target.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 400, 0));
                    target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 400, 0));
                    target.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 400, 0));
                    target.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, 400, 2));
                });
    }

    private void startTimeSeverance(Player chronos) {
        Bukkit.broadcast(Component.text("20초 뒤 SS등급 크로노스의 시간 단절 스킬이 발동됩니다.",
                NamedTextColor.RED));

        new BukkitRunnable() {
            int count = 20;
            @Override
            public void run() {
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
        int durationTicks = plugin.getAbilityConfigManager()
                .getInt(getName(), "skills.severance.duration-ticks", 1200);
        long durationMs = durationTicks * 50L;
        long now = System.currentTimeMillis();
        timeStopEndsAt = Math.max(timeStopEndsAt, now + durationMs);

        frozenPlayers.clear();
        Bukkit.getOnlinePlayers().stream()
                .filter(p -> !p.equals(chronos))
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
        }.runTaskLater(plugin, durationTicks);
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
        if (frozenPlayers.contains(event.getPlayer().getUniqueId()) && ensureTimeStopActive()) {
            Location from = event.getFrom();
            Location to = event.getTo();
            if (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ()) {
                event.setTo(event.getFrom());
            }
        }
    }

    @EventHandler
    public void onAttack(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player attacker
                && frozenPlayers.contains(attacker.getUniqueId())
                && ensureTimeStopActive()) {
            event.setCancelled(true);
        }
    }

    private boolean checkCooldown(Player p, Map<UUID, Long> map, long ms, long now) {
        if (now < map.getOrDefault(p.getUniqueId(), 0L)) {
            p.sendMessage(Component.text("쿨타임 중입니다.", NamedTextColor.RED));
            return false;
        }
        map.put(p.getUniqueId(), now + ms);
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

}
