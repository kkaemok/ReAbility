package org.kkaemok.reAbility.ability.list;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
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

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class CuteCute extends AbilityBase {
    private static final Material[] PASSIVE_REWARDS = {
            Material.IRON_INGOT,
            Material.GOLD_INGOT,
            Material.DIAMOND
    };

    private final ReAbility plugin;
    private final GuildManager guildManager;
    private final Random random = new Random();

    private final Map<UUID, Long> appealCooldown = new HashMap<>();
    private final Map<UUID, Long> explosionCooldown = new HashMap<>();
    private final Map<UUID, Long> dashCooldown = new HashMap<>();
    private final Map<UUID, Long> dashReductionUntil = new HashMap<>();
    private final Map<UUID, BukkitTask> passiveTasks = new HashMap<>();
    private final Map<UUID, BukkitTask> shakeTasks = new HashMap<>();

    public CuteCute(ReAbility plugin) {
        this.plugin = plugin;
        this.guildManager = plugin.getGuildManager();
    }

    @Override
    public String getName() {
        return "CUTECUTE";
    }

    @Override
    public String getDisplayName() {
        return "큐트큐트";
    }

    @Override
    public AbilityGrade getGrade() {
        return AbilityGrade.H;
    }

    @Override
    public String[] getDescription() {
        return new String[]{
                "패시브 {큐트 에너지}: 30분마다 철/금/다이아 중 하나를 10개 획득합니다.",
                "스킬 {큐트 어필}: 금 10개 소모, 5분 쿨타임.",
                "15블럭 내 적에게 슬로우 + 시야방해 + 흔들림을 부여합니다.",
                "스킬 {심쿵 폭발}: 다이아 50개 소모, 1시간 쿨타임.",
                "반경 50블럭 내 적에게 45 피해 + 넉백 + 짧은 스턴을 부여합니다.",
                "스킬 {큐트 대시}: 철 10개 소모, 3분 쿨타임.",
                "즉시 짧게 돌진하고 3분 동안 받는 피해가 30% 감소합니다."
        };
    }

    @Override
    public boolean isDefaultVisibleInList() {
        return false;
    }

    @Override
    public boolean isDefaultVisibleInDescription() {
        return false;
    }

    @Override
    public boolean isDefaultRandomAssignable() {
        return false;
    }

    @Override
    public void onActivate(Player player) {
        startPassiveTask(player);
    }

    @Override
    public void onDeactivate(Player player) {
        UUID uuid = player.getUniqueId();
        BukkitTask passiveTask = passiveTasks.remove(uuid);
        if (passiveTask != null) passiveTask.cancel();

        appealCooldown.remove(uuid);
        explosionCooldown.remove(uuid);
        dashCooldown.remove(uuid);
        dashReductionUntil.remove(uuid);
    }

    @EventHandler
    public void onRightClick(PlayerInteractEvent event) {
        if (!event.getAction().isRightClick()) return;

        Player player = event.getPlayer();
        if (!isHasAbility(player)) return;
        if (shouldIgnoreSneakRightClickBlock(event)) return;

        Material handType = player.getInventory().getItemInMainHand().getType();
        if (handType == Material.GOLD_INGOT) {
            useCuteAppeal(player);
            return;
        }
        if (handType == Material.DIAMOND) {
            useHeartExplosion(player);
            return;
        }
        if (handType == Material.IRON_INGOT) {
            useCuteDash(player);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!isHasAbility(player)) return;

        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        long until = dashReductionUntil.getOrDefault(uuid, 0L);
        if (now >= until) {
            dashReductionUntil.remove(uuid);
            return;
        }

        double reduction = plugin.getAbilityConfigManager()
                .getDouble(getName(), "skills.cute-dash.damage-reduction", 0.30);
        reduction = Math.max(0.0, Math.min(0.95, reduction));
        event.setDamage(event.getDamage() * (1.0 - reduction));
    }

    private void useCuteAppeal(Player player) {
        SkillCost cost = plugin.getAbilityConfigManager()
                .getSkillCost(getName(), "cute-appeal", Material.GOLD_INGOT, 10);
        if (Disruptor.tryFailSkill(plugin, player)) return;

        long now = System.currentTimeMillis();
        if (now < appealCooldown.getOrDefault(player.getUniqueId(), 0L)) {
            player.sendMessage(Component.text("큐트 어필 쿨타임입니다.", NamedTextColor.RED));
            return;
        }
        if (!cost.consumeFromInventory(player)) {
            player.sendMessage(Component.text("금이 부족합니다.", NamedTextColor.RED));
            return;
        }

        long cooldownMs = plugin.getAbilityConfigManager()
                .getLong(getName(), "skills.cute-appeal.cooldown-ms", 300000L);
        int range = plugin.getAbilityConfigManager()
                .getInt(getName(), "skills.cute-appeal.range", 15);
        int slownessTicks = plugin.getAbilityConfigManager()
                .getInt(getName(), "skills.cute-appeal.slowness-ticks", 100);
        int slownessAmplifier = plugin.getAbilityConfigManager()
                .getInt(getName(), "skills.cute-appeal.slowness-amplifier", 1);
        int darknessTicks = plugin.getAbilityConfigManager()
                .getInt(getName(), "skills.cute-appeal.darkness-ticks", 60);
        int nauseaTicks = plugin.getAbilityConfigManager()
                .getInt(getName(), "skills.cute-appeal.nausea-ticks", 80);
        int shakeTicks = plugin.getAbilityConfigManager()
                .getInt(getName(), "skills.cute-appeal.shake-ticks", 40);
        double shakePower = plugin.getAbilityConfigManager()
                .getDouble(getName(), "skills.cute-appeal.shake-power", 0.35);

        appealCooldown.put(player.getUniqueId(), now + cooldownMs);

        for (Entity entity : player.getNearbyEntities(range, range, range)) {
            if (!(entity instanceof Player target)) continue;
            if (target.equals(player)) continue;
            if (isSameGuild(player, target)) continue;

            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, slownessTicks, slownessAmplifier, false, false, true));
            target.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, darknessTicks, 0, false, false, true));
            target.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, nauseaTicks, 0, false, false, true));
            startShakeTask(target, shakeTicks, shakePower);
        }
        player.sendMessage(Component.text("큐트 어필을 사용했습니다.", NamedTextColor.LIGHT_PURPLE));
    }

    private void useHeartExplosion(Player player) {
        SkillCost cost = plugin.getAbilityConfigManager()
                .getSkillCost(getName(), "heart-explosion", Material.DIAMOND, 50);
        if (Disruptor.tryFailSkill(plugin, player)) return;

        long now = System.currentTimeMillis();
        if (now < explosionCooldown.getOrDefault(player.getUniqueId(), 0L)) {
            player.sendMessage(Component.text("심쿵 폭발 쿨타임입니다.", NamedTextColor.RED));
            return;
        }
        if (!cost.consumeFromInventory(player)) {
            player.sendMessage(Component.text("다이아몬드가 부족합니다.", NamedTextColor.RED));
            return;
        }

        long cooldownMs = plugin.getAbilityConfigManager()
                .getLong(getName(), "skills.heart-explosion.cooldown-ms", 3600000L);
        int range = plugin.getAbilityConfigManager()
                .getInt(getName(), "skills.heart-explosion.range", 50);
        double damage = plugin.getAbilityConfigManager()
                .getDouble(getName(), "skills.heart-explosion.damage", 45.0);
        double knockbackHorizontal = plugin.getAbilityConfigManager()
                .getDouble(getName(), "skills.heart-explosion.knockback-horizontal", 1.4);
        double knockbackVertical = plugin.getAbilityConfigManager()
                .getDouble(getName(), "skills.heart-explosion.knockback-vertical", 0.45);
        int stunTicks = plugin.getAbilityConfigManager()
                .getInt(getName(), "skills.heart-explosion.stun-ticks", 30);

        explosionCooldown.put(player.getUniqueId(), now + cooldownMs);

        for (Entity entity : player.getNearbyEntities(range, range, range)) {
            if (!(entity instanceof Player target)) continue;
            if (target.equals(player)) continue;
            if (isSameGuild(player, target)) continue;

            target.damage(damage, player);
            Vector push = target.getLocation().toVector().subtract(player.getLocation().toVector());
            if (push.lengthSquared() < 0.0001) {
                push = player.getLocation().getDirection().clone();
            }
            push.normalize().multiply(knockbackHorizontal).setY(knockbackVertical);
            target.setVelocity(push);
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, stunTicks, 10, false, false, true));
        }
        player.sendMessage(Component.text("심쿵 폭발을 사용했습니다.", NamedTextColor.LIGHT_PURPLE));
    }

    private void useCuteDash(Player player) {
        SkillCost cost = plugin.getAbilityConfigManager()
                .getSkillCost(getName(), "cute-dash", Material.IRON_INGOT, 10);
        if (Disruptor.tryFailSkill(plugin, player)) return;

        long now = System.currentTimeMillis();
        if (now < dashCooldown.getOrDefault(player.getUniqueId(), 0L)) {
            player.sendMessage(Component.text("큐트 대시 쿨타임입니다.", NamedTextColor.RED));
            return;
        }
        if (!cost.consumeFromInventory(player)) {
            player.sendMessage(Component.text("철이 부족합니다.", NamedTextColor.RED));
            return;
        }

        long cooldownMs = plugin.getAbilityConfigManager()
                .getLong(getName(), "skills.cute-dash.cooldown-ms", 180000L);
        long reductionDurationMs = plugin.getAbilityConfigManager()
                .getLong(getName(), "skills.cute-dash.reduction-duration-ms", 180000L);
        double dashPower = plugin.getAbilityConfigManager()
                .getDouble(getName(), "skills.cute-dash.power", 1.3);
        double dashVertical = plugin.getAbilityConfigManager()
                .getDouble(getName(), "skills.cute-dash.vertical", 0.15);

        dashCooldown.put(player.getUniqueId(), now + cooldownMs);
        dashReductionUntil.put(player.getUniqueId(), now + reductionDurationMs);

        Vector direction = player.getLocation().getDirection();
        if (direction.lengthSquared() > 0.0001) {
            player.setVelocity(direction.normalize().multiply(dashPower).setY(dashVertical));
        }
        player.sendMessage(Component.text("큐트 대시를 사용했습니다. 3분간 받는 피해 30% 감소!", NamedTextColor.AQUA));
    }

    private void startPassiveTask(Player player) {
        UUID uuid = player.getUniqueId();
        BukkitTask existing = passiveTasks.remove(uuid);
        if (existing != null) existing.cancel();

        long interval = Math.max(20L, plugin.getAbilityConfigManager()
                .getLong(getName(), "passive.cute-energy.interval-ticks", 36000L));
        int amount = Math.max(1, plugin.getAbilityConfigManager()
                .getInt(getName(), "passive.cute-energy.amount", 10));

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || !isHasAbility(player)) {
                    BukkitTask current = passiveTasks.get(uuid);
                    if (current != null && current.getTaskId() == this.getTaskId()) {
                        passiveTasks.remove(uuid);
                    }
                    this.cancel();
                    return;
                }

                Material reward = PASSIVE_REWARDS[random.nextInt(PASSIVE_REWARDS.length)];
                ItemStack item = new ItemStack(reward, amount);
                Map<Integer, ItemStack> overflow = player.getInventory().addItem(item);
                for (ItemStack left : overflow.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), left);
                }
                player.sendMessage(Component.text("큐트 에너지! " + reward.name() + " x" + amount + " 획득", NamedTextColor.GOLD));
            }
        }.runTaskTimer(plugin, interval, interval);
        passiveTasks.put(uuid, task);
    }

    private void startShakeTask(Player target, int durationTicks, double power) {
        UUID targetId = target.getUniqueId();
        BukkitTask existing = shakeTasks.remove(targetId);
        if (existing != null) existing.cancel();

        BukkitTask task = new BukkitRunnable() {
            int elapsed;

            @Override
            public void run() {
                if (!target.isOnline() || target.isDead() || elapsed >= durationTicks) {
                    BukkitTask current = shakeTasks.get(targetId);
                    if (current != null && current.getTaskId() == this.getTaskId()) {
                        shakeTasks.remove(targetId);
                    }
                    this.cancel();
                    return;
                }
                double x = (random.nextDouble() - 0.5) * power;
                double z = (random.nextDouble() - 0.5) * power;
                target.setVelocity(target.getVelocity().add(new Vector(x, 0.0, z)));
                elapsed += 2;
            }
        }.runTaskTimer(plugin, 0L, 2L);
        shakeTasks.put(targetId, task);
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
