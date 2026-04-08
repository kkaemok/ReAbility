package org.kkaemok.reAbility.ability.list;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.kkaemok.reAbility.ReAbility;
import org.kkaemok.reAbility.ability.AbilityBase;
import org.kkaemok.reAbility.ability.AbilityGrade;
import org.kkaemok.reAbility.ability.SkillCost;
import org.kkaemok.reAbility.guild.GuildData;
import org.kkaemok.reAbility.guild.GuildManager;
import org.kkaemok.reAbility.utils.SkillParticles;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class Beauty extends AbilityBase {
    private final ReAbility plugin;
    private final GuildManager guildManager;
    private final Map<UUID, Long> scentCooldown = new HashMap<>();
    private final Map<UUID, BukkitTask> passiveTasks = new HashMap<>();
    private final Map<UUID, BukkitTask> charmTasks = new HashMap<>();
    private final Map<UUID, UUID> charmOwners = new HashMap<>();

    public Beauty(ReAbility plugin) {
        this.plugin = plugin;
        this.guildManager = plugin.getGuildManager();
    }

    @Override public String getName() { return "BEAUTY"; }
    @Override public String getDisplayName() { return "미녀"; }
    @Override public AbilityGrade getGrade() { return AbilityGrade.S; }

    @Override
    public String[] getDescription() {
        return new String[]{
                "길드원에게 재생 1, 저항 1 부여 및 자신의 버프 공유.",
                "상대팀이 자신을 바라보면 구속 10 효과.",
                "스킬 {미인계}: 네더라이트 주괴로 타격 시 13초간 나약함 200 + 강제 추종",
                "(엔더펄로 해제 가능)",
                "스킬 {기분좋은 냄새}: 다이아 60개 우클릭, 15칸 적 디버프 1분 (쿨타임 5분)"
        };
    }

    @Override
    public void onActivate(Player player) {
        startPassiveTask(player);
    }

    @Override
    public void onDeactivate(Player player) {
        BukkitTask passiveTask = passiveTasks.remove(player.getUniqueId());
        if (passiveTask != null) {
            passiveTask.cancel();
        }

        List<UUID> toCancel = new ArrayList<>();
        UUID ownerId = player.getUniqueId();
        for (Map.Entry<UUID, UUID> entry : charmOwners.entrySet()) {
            if (ownerId.equals(entry.getValue())) {
                toCancel.add(entry.getKey());
            }
        }
        for (UUID targetId : toCancel) {
            cancelCharm(targetId);
        }
    }

    private void startPassiveTask(Player beauty) {
        BukkitTask existing = passiveTasks.remove(beauty.getUniqueId());
        if (existing != null) {
            existing.cancel();
        }

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!beauty.isOnline() || !isHasAbility(beauty)) {
                    BukkitTask current = passiveTasks.get(beauty.getUniqueId());
                    if (current != null && current.getTaskId() == this.getTaskId()) {
                        passiveTasks.remove(beauty.getUniqueId());
                    }
                    this.cancel();
                    return;
                }

                beauty.getNearbyEntities(50, 50, 50).stream()
                        .filter(e -> e instanceof Player)
                        .map(e -> (Player) e)
                        .filter(p -> isSameGuild(beauty, p))
                        .forEach(p -> applyBuffs(beauty, p));
                applyBuffs(beauty, beauty);

                for (Player enemy : Bukkit.getOnlinePlayers()) {
                    if (!enemy.getWorld().equals(beauty.getWorld())) continue;
                    if (isSameGuild(beauty, enemy)) continue;
                    if (enemy.getLocation().distance(beauty.getLocation()) > 50) continue;
                    if (isLookingAt(enemy, beauty)) {
                        enemy.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20, 9, false, false));
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 5L);
        passiveTasks.put(beauty.getUniqueId(), task);
    }

    private void applyBuffs(Player beauty, Player target) {
        target.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 40, 0, false, false));
        target.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 40, 0, false, false));

        for (PotionEffect effect : beauty.getActivePotionEffects()) {
            if (!isBeneficial(effect.getType())) continue;
            target.addPotionEffect(new PotionEffect(effect.getType(), 40, effect.getAmplifier(), false, false));
        }
    }

    @EventHandler
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!isHasAbility(attacker)) return;
        if (!(event.getEntity() instanceof Player target)) return;
        if (isSameGuild(attacker, target)) return;

        if (attacker.getInventory().getItemInMainHand().getType() != Material.NETHERITE_INGOT) return;

        target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 260, 199, false, false));
        startCharm(attacker, target);
        SkillParticles.beautyCharm(target);
    }

    @EventHandler
    public void onRightClick(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!isHasAbility(player)) return;
        if (!event.getAction().isRightClick()) return;
        if (shouldIgnoreSneakRightClickBlock(event)) return;

        ItemStack item = player.getInventory().getItemInMainHand();
        SkillCost scentCost = plugin.getAbilityConfigManager()
                .getSkillCost(getName(), "scent", Material.DIAMOND, 60);
        if (item.getType() != scentCost.getItem()) return;
        if (Disruptor.tryFailSkill(plugin, player)) return;

        long now = System.currentTimeMillis();
        if (now < scentCooldown.getOrDefault(player.getUniqueId(), 0L)) {
            player.sendMessage(Component.text("쿨타임입니다.", NamedTextColor.RED));
            return;
        }
        if (!scentCost.consumeFromInventory(player)) return;

        long cooldownMs = plugin.getAbilityConfigManager()
                .getLong(getName(), "skills.scent.cooldown-ms", 300000L);
        int range = plugin.getAbilityConfigManager()
                .getInt(getName(), "skills.scent.range", 15);
        int debuffTicks = plugin.getAbilityConfigManager()
                .getInt(getName(), "skills.scent.debuff-ticks", 1200);
        int weaknessAmp = plugin.getAbilityConfigManager()
                .getInt(getName(), "skills.scent.weakness-amplifier", 2);
        int slownessAmp = plugin.getAbilityConfigManager()
                .getInt(getName(), "skills.scent.slowness-amplifier", 1);
        scentCooldown.put(player.getUniqueId(), now + cooldownMs);
        for (Entity entity : player.getNearbyEntities(range, range, range)) {
            if (entity instanceof Player target && !isSameGuild(player, target)) {
                target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, debuffTicks, weaknessAmp, false, false));
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, debuffTicks, slownessAmp, false, false));
                target.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, debuffTicks, 0, false, false));
            }
        }
        SkillParticles.beautyScent(player);
        broadcastSkill(player, "{기분좋은 냄새}");
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.getCause() != PlayerTeleportEvent.TeleportCause.ENDER_PEARL) return;
        Player player = event.getPlayer();
        if (charmTasks.containsKey(player.getUniqueId())) {
            cancelCharm(player.getUniqueId());
        }
    }

    private void startCharm(Player beauty, Player target) {
        cancelCharm(target.getUniqueId());
        charmOwners.put(target.getUniqueId(), beauty.getUniqueId());

        BukkitRunnable runnable = new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!beauty.isOnline() || !target.isOnline()) {
                    cancelCharm(target.getUniqueId());
                    return;
                }
                if (!beauty.getWorld().equals(target.getWorld())) {
                    cancelCharm(target.getUniqueId());
                    return;
                }
                if (ticks >= 260) {
                    cancelCharm(target.getUniqueId());
                    return;
                }

                Vector dir = beauty.getLocation().toVector().subtract(target.getLocation().toVector());
                if (dir.length() > 1.5) {
                    target.setVelocity(dir.normalize().multiply(0.35));
                }
                ticks += 5;
            }
        };
        BukkitTask task = runnable.runTaskTimer(plugin, 0L, 5L);
        charmTasks.put(target.getUniqueId(), task);
    }

    private void cancelCharm(UUID targetId) {
        BukkitTask task = charmTasks.remove(targetId);
        if (task != null) task.cancel();
        charmOwners.remove(targetId);
        Player target = Bukkit.getPlayer(targetId);
        if (target != null) {
            target.removePotionEffect(PotionEffectType.WEAKNESS);
        }
    }

    private boolean isLookingAt(Player observer, Player target) {
        Location eye = observer.getEyeLocation();
        Vector toTarget = target.getEyeLocation().toVector().subtract(eye.toVector());
        return eye.getDirection().normalize().dot(toTarget.normalize()) > 0.8;
    }

    private boolean isSameGuild(Player p1, Player p2) {
        if (p1.equals(p2)) return true;
        GuildData g1 = guildManager.getGuildByMember(p1.getUniqueId());
        GuildData g2 = guildManager.getGuildByMember(p2.getUniqueId());
        return g1 != null && g2 != null && g1.name.equalsIgnoreCase(g2.name);
    }

    private void broadcastSkill(Player player, String skillName) {
        Bukkit.broadcast(Component.text("[S] 미녀 " + player.getName() + "이(가) " + skillName + "을 사용했습니다!",
                NamedTextColor.LIGHT_PURPLE));
    }

    private boolean isBeneficial(PotionEffectType type) {
        return type.getEffectCategory() != PotionEffectType.Category.HARMFUL;
    }

    private boolean isHasAbility(Player player) {
        return getName().equals(plugin.getAbilityManager().getPlayerData(player.getUniqueId()).getAbilityName());
    }
}
