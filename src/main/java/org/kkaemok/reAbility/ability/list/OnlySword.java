package org.kkaemok.reAbility.ability.list;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
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

public class OnlySword extends AbilityBase {
    private final ReAbility plugin;
    private final GuildManager guildManager;
    private final Map<UUID, Long> onlySwordCooldown = new HashMap<>();
    private final Map<UUID, Long> onlySwordActiveUntil = new HashMap<>();
    private final Map<UUID, BukkitTask> onlySwordTasks = new HashMap<>();
    private final Map<UUID, Long> wTapCooldown = new HashMap<>();
    private final Map<UUID, BukkitTask> wTapTasks = new HashMap<>();

    public OnlySword(ReAbility plugin) {
        this.plugin = plugin;
        this.guildManager = plugin.getGuildManager();
    }

    @Override public String getName() { return "ONLY_SWORD"; }
    @Override public String getDisplayName() { return "온리소드"; }
    @Override public AbilityGrade getGrade() { return AbilityGrade.S; }

    @Override
    public String[] getDescription() {
        return new String[]{
                "검 데미지 +5.",
                "스킬 {온리소드}: 다이아몬드 블럭 5개 우클릭, 2분간 주변 30칸 검만 공격 가능",
                "검 제외 아이템 사용 시 나약함 200. 자신도 검만 공격 가능",
                "힘 3, 스피드 2 (35초), 쿨타임 30분",
                "스킬 {W탭}: 검 우클릭, 리치 +2/힘1/저항1 (3분)",
                "주변 7칸 적 리치 -1 (쿨타임 3분)"
        };
    }

    @Override
    public void onDeactivate(Player player) {
        BukkitTask task = onlySwordTasks.remove(player.getUniqueId());
        if (task != null) task.cancel();
        onlySwordActiveUntil.remove(player.getUniqueId());

        BukkitTask wTask = wTapTasks.remove(player.getUniqueId());
        if (wTask != null) wTask.cancel();
        removeAttackRangeModifier(player, getWTapSelfKey(player));
        removeDebuffFromAll(player);
        player.removePotionEffect(PotionEffectType.STRENGTH);
        player.removePotionEffect(PotionEffectType.SPEED);
        player.removePotionEffect(PotionEffectType.RESISTANCE);
    }

    @EventHandler
    public void onRightClick(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!isHasAbility(player)) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack item = player.getInventory().getItemInMainHand();
        long now = System.currentTimeMillis();

        SkillCost onlySwordCost = plugin.getAbilityConfigManager()
                .getSkillCost(getName(), "only_sword", Material.DIAMOND_BLOCK, 5);
        if (item.getType() == onlySwordCost.getItem()) {
            if (now < onlySwordCooldown.getOrDefault(player.getUniqueId(), 0L)) {
                player.sendMessage(Component.text("온리소드 쿨타임입니다.", NamedTextColor.RED));
                return;
            }
            if (!onlySwordCost.consumeFromInventory(player)) return;

            long cooldownMs = plugin.getAbilityConfigManager()
                    .getLong(getName(), "skills.only_sword.cooldown-ms", 1800000L);
            long activeMs = plugin.getAbilityConfigManager()
                    .getLong(getName(), "skills.only_sword.active-ms", 120000L);
            int buffTicks = plugin.getAbilityConfigManager()
                    .getInt(getName(), "skills.only_sword.buff-ticks", 700);
            int strengthAmp = plugin.getAbilityConfigManager()
                    .getInt(getName(), "skills.only_sword.strength-amplifier", 2);
            int speedAmp = plugin.getAbilityConfigManager()
                    .getInt(getName(), "skills.only_sword.speed-amplifier", 1);
            int range = plugin.getAbilityConfigManager()
                    .getInt(getName(), "skills.only_sword.range", 30);

            onlySwordCooldown.put(player.getUniqueId(), now + cooldownMs);
            onlySwordActiveUntil.put(player.getUniqueId(), now + activeMs);
            startOnlySwordTask(player, range);

            player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, buffTicks, strengthAmp, false, false));
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, buffTicks, speedAmp, false, false));
            Bukkit.broadcast(Component.text("[S] 온리소드 " + player.getName() + "이(가) {온리소드}를 사용했습니다!",
                    NamedTextColor.GOLD));
            SkillParticles.onlySwordField(player);
            return;
        }

        if (isSword(item.getType())) {
            if (now < wTapCooldown.getOrDefault(player.getUniqueId(), 0L)) {
                player.sendMessage(Component.text("W탭 쿨타임입니다.", NamedTextColor.RED));
                return;
            }
            long cooldownMs = plugin.getAbilityConfigManager()
                    .getLong(getName(), "skills.wtap.cooldown-ms", 180000L);
            int durationTicks = plugin.getAbilityConfigManager()
                    .getInt(getName(), "skills.wtap.duration-ticks", 3600);
            double selfRangeBonus = plugin.getAbilityConfigManager()
                    .getDouble(getName(), "skills.wtap.self-range-bonus", 2.0);
            int strengthAmp = plugin.getAbilityConfigManager()
                    .getInt(getName(), "skills.wtap.strength-amplifier", 0);
            int resistanceAmp = plugin.getAbilityConfigManager()
                    .getInt(getName(), "skills.wtap.resistance-amplifier", 0);
            int debuffRange = plugin.getAbilityConfigManager()
                    .getInt(getName(), "skills.wtap.debuff-range", 7);
            double debuffAmount = plugin.getAbilityConfigManager()
                    .getDouble(getName(), "skills.wtap.debuff-amount", -1.0);

            wTapCooldown.put(player.getUniqueId(), now + cooldownMs);
            applyAttackRangeModifier(player, getWTapSelfKey(player), selfRangeBonus);

            player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, durationTicks, strengthAmp, false, false));
            player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, durationTicks, resistanceAmp, false, false));
            SkillParticles.onlySwordWTap(player);

            startWTapTask(player, durationTicks, debuffRange, debuffAmount);
        }
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player attacker) {
            Material weapon = attacker.getInventory().getItemInMainHand().getType();
            if (isHasAbility(attacker) && isSword(weapon)) {
                double bonus = plugin.getAbilityConfigManager()
                        .getDouble(getName(), "stats.sword-damage-bonus", 5.0);
                event.setDamage(event.getDamage() + bonus);
            }

            if (isOnlySwordBlocked(attacker, weapon)) {
                event.setCancelled(true);
                attacker.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 40, 199, false, false));
            }
        }
    }

    private boolean isOnlySwordBlocked(Player attacker, Material weapon) {
        long now = System.currentTimeMillis();

        if (isHasAbility(attacker)) {
            Long until = onlySwordActiveUntil.get(attacker.getUniqueId());
            if (until != null && now <= until && !isSword(weapon)) {
                return true;
            }
        }

        for (Map.Entry<UUID, Long> entry : onlySwordActiveUntil.entrySet()) {
            if (entry.getValue() < now) continue;
            Player owner = Bukkit.getPlayer(entry.getKey());
            if (owner == null || !owner.isOnline()) continue;
            if (!owner.getWorld().equals(attacker.getWorld())) continue;
            int range = plugin.getAbilityConfigManager()
                    .getInt(getName(), "skills.only_sword.range", 30);
            double rangeSquared = (double) range * range;
            if (owner.getLocation().distanceSquared(attacker.getLocation()) > rangeSquared) continue;
            if (isSameGuild(owner, attacker)) continue;
            if (!isSword(weapon)) {
                return true;
            }
        }
        return false;
    }

    private void startOnlySwordTask(Player owner, int range) {
        BukkitTask existing = onlySwordTasks.remove(owner.getUniqueId());
        if (existing != null) existing.cancel();

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                Long until = onlySwordActiveUntil.get(owner.getUniqueId());
                if (until == null || now > until || !owner.isOnline()) {
                    onlySwordTasks.remove(owner.getUniqueId());
                    cancel();
                    return;
                }

                for (Entity entity : owner.getNearbyEntities(range, range, range)) {
                    if (!(entity instanceof Player target)) continue;
                    if (isSameGuild(owner, target)) continue;
                    Material weapon = target.getInventory().getItemInMainHand().getType();
                    if (isSword(weapon)) continue;
                    target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 40, 199, false, false));
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
        onlySwordTasks.put(owner.getUniqueId(), task);
    }

    private void startWTapTask(Player owner, int durationTicks, int range, double debuffAmount) {
        BukkitTask existing = wTapTasks.remove(owner.getUniqueId());
        if (existing != null) existing.cancel();

        NamespacedKey debuffKey = getWTapDebuffKey(owner);
        BukkitTask task = new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!owner.isOnline()) {
                    removeDebuffFromAll(owner);
                    cancel();
                    return;
                }
                if (ticks >= durationTicks) {
                    removeDebuffFromAll(owner);
                    removeAttackRangeModifier(owner, getWTapSelfKey(owner));
                    cancel();
                    return;
                }

                for (Player target : Bukkit.getOnlinePlayers()) {
                    if (target.equals(owner)) continue;
                    if (!target.getWorld().equals(owner.getWorld())) {
                        removeAttackRangeModifier(target, debuffKey);
                        continue;
                    }
                    if (isSameGuild(owner, target)) {
                        removeAttackRangeModifier(target, debuffKey);
                        continue;
                    }
                    double rangeSquared = (double) range * range;
                    if (target.getLocation().distanceSquared(owner.getLocation()) <= rangeSquared) {
                        applyAttackRangeModifier(target, debuffKey, debuffAmount);
                    } else {
                        removeAttackRangeModifier(target, debuffKey);
                    }
                }
                ticks += 20;
            }
        }.runTaskTimer(plugin, 0L, 20L);
        wTapTasks.put(owner.getUniqueId(), task);
    }

    private void removeDebuffFromAll(Player owner) {
        NamespacedKey debuffKey = getWTapDebuffKey(owner);
        for (Player target : Bukkit.getOnlinePlayers()) {
            removeAttackRangeModifier(target, debuffKey);
        }
    }

    private void applyAttackRangeModifier(Player player, NamespacedKey key, double amount) {
        Attribute attrType = getAttackRangeAttribute();
        if (attrType == null) return;
        AttributeInstance attr = player.getAttribute(attrType);
        if (attr == null) return;
        removeAttackRangeModifier(player, key);
        attr.addModifier(new AttributeModifier(key, amount, AttributeModifier.Operation.ADD_NUMBER));
    }

    private void removeAttackRangeModifier(Player player, NamespacedKey key) {
        Attribute attrType = getAttackRangeAttribute();
        if (attrType == null) return;
        AttributeInstance attr = player.getAttribute(attrType);
        if (attr == null) return;
        attr.getModifiers().stream()
                .filter(mod -> mod.getKey().equals(key))
                .forEach(attr::removeModifier);
    }

    private NamespacedKey getWTapSelfKey(Player player) {
        return new NamespacedKey(plugin, "onlysword-wtap-self-" + player.getUniqueId());
    }

    private NamespacedKey getWTapDebuffKey(Player player) {
        return new NamespacedKey(plugin, "onlysword-wtap-debuff-" + player.getUniqueId());
    }

    private Attribute getAttackRangeAttribute() {
        return Attribute.ENTITY_INTERACTION_RANGE;
    }

    private boolean isSword(Material type) {
        return type == Material.WOODEN_SWORD
                || type == Material.STONE_SWORD
                || type == Material.IRON_SWORD
                || type == Material.GOLDEN_SWORD
                || type == Material.DIAMOND_SWORD
                || type == Material.NETHERITE_SWORD;
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
