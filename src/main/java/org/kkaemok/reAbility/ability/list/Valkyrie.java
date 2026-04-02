package org.kkaemok.reAbility.ability.list;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Valkyrie extends AbilityBase {
    private static final double WING_RANGE_BONUS = 10.0;

    private final ReAbility plugin;
    private final GuildManager guildManager;
    private final Map<UUID, Long> wingCooldown = new HashMap<>();
    private final Map<UUID, Long> vortexCooldown = new HashMap<>();
    private final Map<UUID, Boolean> prevAllowFlight = new HashMap<>();
    private final Map<UUID, BukkitTask> wingTasks = new HashMap<>();

    public Valkyrie(ReAbility plugin) {
        this.plugin = plugin;
        this.guildManager = plugin.getGuildManager();
    }

    @Override public String getName() { return "VALKYRIE"; }
    @Override public String getDisplayName() { return "발키리"; }
    @Override public AbilityGrade getGrade() { return AbilityGrade.A; }

    @Override
    public String[] getDescription() {
        return new String[]{
                "도끼 공격 속도 45% 증가.",
                "스킬 {천사의 날개}: 네더라이트 파편 2개 우클릭",
                "30초 비행 + 도끼 리치 10칸 증가, 힘2 10초 (쿨타임 2분)",
                "스킬 {분노의 회오리}: 네더라이트 파편 3개 웅크림",
                "주변 15칸 흡입, 중앙 적은 1초마다 35 피해 (쿨타임 3분)"
        };
    }

    @Override
    public void onActivate(Player player) {
        updateAxeSpeed(player);
    }

    @Override
    public void onDeactivate(Player player) {
        removeAxeSpeed(player);
        removeRange(player);
        BukkitTask wingTask = wingTasks.remove(player.getUniqueId());
        if (wingTask != null) wingTask.cancel();
        Boolean prev = prevAllowFlight.remove(player.getUniqueId());
        if (prev != null && !prev && player.getGameMode() == org.bukkit.GameMode.SURVIVAL) {
            player.setFlying(false);
            player.setAllowFlight(false);
        }
        player.removePotionEffect(PotionEffectType.STRENGTH);
    }

    @EventHandler
    public void onItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        if (isHasAbility(player)) {
            updateAxeSpeed(player);
        }
    }

    @EventHandler
    public void onRightClick(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!isHasAbility(player)) return;
        if (!event.getAction().isRightClick()) return;
        if (shouldIgnoreSneakRightClickBlock(event)) return;
        SkillCost wingCost = plugin.getAbilityConfigManager()
                .getSkillCost(getName(), "wings", Material.NETHERITE_SCRAP, 2);
        if (player.getInventory().getItemInMainHand().getType() != wingCost.getItem()) return;

        long now = System.currentTimeMillis();
        if (now < wingCooldown.getOrDefault(player.getUniqueId(), 0L)) {
            player.sendMessage(Component.text("천사의 날개 쿨타임입니다.", NamedTextColor.RED));
            return;
        }
        if (!wingCost.consumeFromInventory(player)) return;

        long cooldownMs = plugin.getAbilityConfigManager()
                .getLong(getName(), "skills.wings.cooldown-ms", 120000L);
        int strengthTicks = plugin.getAbilityConfigManager()
                .getInt(getName(), "skills.wings.strength-ticks", 200);
        int strengthAmp = plugin.getAbilityConfigManager()
                .getInt(getName(), "skills.wings.strength-amplifier", 1);
        int durationTicks = plugin.getAbilityConfigManager()
                .getInt(getName(), "skills.wings.duration-ticks", 600);
        wingCooldown.put(player.getUniqueId(), now + cooldownMs);
        applyRange(player);
        player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, strengthTicks, strengthAmp, false, false));

        if (!player.getAllowFlight()) {
            prevAllowFlight.put(player.getUniqueId(), false);
        }
        player.setAllowFlight(true);
        player.setFlying(true);
        SkillParticles.valkyrieWings(player);

        BukkitTask existing = wingTasks.remove(player.getUniqueId());
        if (existing != null) existing.cancel();

        BukkitTask endTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            wingTasks.remove(player.getUniqueId());
            removeRange(player);
            Boolean prev = prevAllowFlight.remove(player.getUniqueId());
            if (prev != null && !prev && player.getGameMode() == org.bukkit.GameMode.SURVIVAL) {
                player.setFlying(false);
                player.setAllowFlight(false);
            }
        }, durationTicks);
        wingTasks.put(player.getUniqueId(), endTask);
    }

    @Override
    public void onSneakSkill(Player player) {
        long now = System.currentTimeMillis();
        if (now < vortexCooldown.getOrDefault(player.getUniqueId(), 0L)) {
            player.sendMessage(Component.text("분노의 회오리 쿨타임입니다.", NamedTextColor.RED));
            return;
        }
        SkillCost vortexCost = plugin.getAbilityConfigManager()
                .getSkillCost(getName(), "vortex", Material.NETHERITE_SCRAP, 3);
        if (player.getInventory().getItemInMainHand().getType() != vortexCost.getItem()) return;
        if (!vortexCost.consumeFromInventory(player)) return;

        long cooldownMs = plugin.getAbilityConfigManager()
                .getLong(getName(), "skills.vortex.cooldown-ms", 180000L);
        vortexCooldown.put(player.getUniqueId(), now + cooldownMs);
        Location center = player.getLocation();
        SkillParticles.valkyrieVortex(player);

        new BukkitRunnable() {
            int tick = 0;

            @Override
            public void run() {
                if (tick >= 60 || !player.isOnline()) {
                    cancel();
                    return;
                }

                for (Entity entity : player.getNearbyEntities(15, 15, 15)) {
                    if (!(entity instanceof Player target)) continue;
                    if (isSameGuild(player, target)) continue;

                    Vector dir = center.toVector().subtract(target.getLocation().toVector());
                    target.setVelocity(dir.normalize().multiply(0.4));

                    if (tick % 20 == 0 && target.getLocation().distanceSquared(center) <= 9) {
                        target.damage(25.0, player);
                        target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 100, 1, false, false));
                        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 4, false, false));
                    }
                }
                tick += 5;
            }
        }.runTaskTimer(plugin, 0L, 5L);
    }

    private void updateAxeSpeed(Player player) {
        Material main = player.getInventory().getItemInMainHand().getType();
        if (isAxe(main)) {
            applyAxeSpeed(player);
        } else {
            removeAxeSpeed(player);
        }
    }

    private void applyAxeSpeed(Player player) {
        Attribute attrType = getAttackSpeedAttribute();
        if (attrType == null) return;
        AttributeInstance attr = player.getAttribute(attrType);
        if (attr == null) return;
        removeAxeSpeed(player);
        attr.addModifier(new AttributeModifier(getAxeSpeedKey(), -0.45, AttributeModifier.Operation.MULTIPLY_SCALAR_1));
    }

    private void removeAxeSpeed(Player player) {
        Attribute attrType = getAttackSpeedAttribute();
        if (attrType == null) return;
        AttributeInstance attr = player.getAttribute(attrType);
        if (attr == null) return;
        NamespacedKey key = getAxeSpeedKey();
        attr.getModifiers().stream()
                .filter(mod -> mod.getKey().equals(key))
                .forEach(attr::removeModifier);
    }

    private void applyRange(Player player) {
        Attribute attrType = getAttackRangeAttribute();
        if (attrType == null) return;
        AttributeInstance attr = player.getAttribute(attrType);
        if (attr == null) return;
        removeRange(player);
        attr.addModifier(new AttributeModifier(getRangeKey(), WING_RANGE_BONUS, AttributeModifier.Operation.ADD_NUMBER));
    }

    private void removeRange(Player player) {
        Attribute attrType = getAttackRangeAttribute();
        if (attrType == null) return;
        AttributeInstance attr = player.getAttribute(attrType);
        if (attr == null) return;
        NamespacedKey key = getRangeKey();
        attr.getModifiers().stream()
                .filter(mod -> mod.getKey().equals(key))
                .forEach(attr::removeModifier);
    }

    private NamespacedKey getAxeSpeedKey() {
        return new NamespacedKey(plugin, "valkyrie-axe-speed");
    }

    private NamespacedKey getRangeKey() {
        return new NamespacedKey(plugin, "valkyrie-range");
    }

    private Attribute getAttackSpeedAttribute() {
        return Attribute.ATTACK_SPEED;
    }

    private Attribute getAttackRangeAttribute() {
        return Attribute.ENTITY_INTERACTION_RANGE;
    }

    private boolean isAxe(Material type) {
        return type == Material.WOODEN_AXE
                || type == Material.STONE_AXE
                || type == Material.IRON_AXE
                || type == Material.GOLDEN_AXE
                || type == Material.DIAMOND_AXE
                || type == Material.NETHERITE_AXE;
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
