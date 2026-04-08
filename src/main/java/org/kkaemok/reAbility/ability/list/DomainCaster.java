package org.kkaemok.reAbility.ability.list;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.kkaemok.reAbility.ReAbility;
import org.kkaemok.reAbility.ability.AbilityBase;
import org.kkaemok.reAbility.ability.AbilityGrade;
import org.kkaemok.reAbility.ability.SkillCost;
import org.kkaemok.reAbility.data.PlayerData;
import org.kkaemok.reAbility.guild.GuildData;
import org.kkaemok.reAbility.guild.GuildManager;
import org.kkaemok.reAbility.utils.SkillParticles;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class DomainCaster extends AbilityBase {
    private static final double DOMAIN_HEALTH_BONUS = 40.0;

    private final ReAbility plugin;
    private final GuildManager guildManager;
    private final Map<UUID, Long> expandCooldown = new HashMap<>();
    private final Map<UUID, Long> markCooldown = new HashMap<>();
    private final Map<UUID, Long> expandedUntil = new HashMap<>();

    public DomainCaster(ReAbility plugin) {
        this.plugin = plugin;
        this.guildManager = plugin.getGuildManager();
        startDomainTask();
    }

    @Override
    public String getName() {
        return "DOMAIN_CASTER";
    }

    @Override
    public String getDisplayName() {
        return "영역술사";
    }

    @Override
    public AbilityGrade getGrade() {
        return AbilityGrade.A;
    }

    @Override
    public String[] getDescription() {
        return new String[]{
                "설정한 홈 기준 200칸이 자신의 영역입니다.",
                "영역 내 버프: 힘2, 스피드3, 체력 2줄 증가.",
                "스킬 {영역확장}: 철 블럭 20개 웅크리기, 30분간 영역 500칸 + 저항1 (쿨 1시간).",
                "스킬 {영역표시}: 다이아 블럭 10개 우클릭, 영역 내 적에게 피해 50 + 나약함3/구속3 45초 (쿨 3분).",
                "홈 설정: /domainhome set 또는 /영역홈 설정"
        };
    }

    @Override
    public void onActivate(Player player) {
        if (getHome(player) == null) {
            player.sendMessage(Component.text("영역 홈이 설정되지 않았습니다. /domainhome set 또는 /영역홈 설정",
                    NamedTextColor.YELLOW));
        }
    }

    @Override
    public void onDeactivate(Player player) {
        removeDomainHealth(player);
    }

    @Override
    public void onSneakSkill(Player player) {
        if (getHome(player) == null) {
            player.sendMessage(Component.text("영역 홈이 없습니다. /domainhome set으로 먼저 설정하세요.",
                    NamedTextColor.RED));
            return;
        }
        if (!isInDomain(player)) {
            player.sendMessage(Component.text("영역 내에서만 사용할 수 있습니다.", NamedTextColor.RED));
            return;
        }

        SkillCost expandCost = plugin.getAbilityConfigManager()
                .getSkillCost(getName(), "expand", Material.IRON_BLOCK, 20);
        if (player.getInventory().getItemInMainHand().getType() != expandCost.getItem()) return;

        long now = System.currentTimeMillis();
        long until = expandCooldown.getOrDefault(player.getUniqueId(), 0L);
        if (now < until) {
            player.sendMessage(Component.text("영역확장 쿨타임입니다.", NamedTextColor.RED));
            return;
        }

        if (!expandCost.consumeFromInventory(player)) return;

        long cooldownMs = plugin.getAbilityConfigManager()
                .getLong(getName(), "skills.expand.cooldown-ms", 60L * 60L * 1000L);
        long durationMs = plugin.getAbilityConfigManager()
                .getLong(getName(), "skills.expand.duration-ms", 30L * 60L * 1000L);
        expandCooldown.put(player.getUniqueId(), now + cooldownMs);
        expandedUntil.put(player.getUniqueId(), now + durationMs);

        player.sendMessage(Component.text("[영역술사] 영역이 30분 동안 확장되었습니다.", NamedTextColor.GREEN));
        SkillParticles.domainExpand(player);
    }

    @EventHandler
    public void onRightClick(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!isHasAbility(player)) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (shouldIgnoreSneakRightClickBlock(event)) return;

        if (getHome(player) == null) {
            player.sendMessage(Component.text("영역 홈이 없습니다. /domainhome set으로 먼저 설정하세요.",
                    NamedTextColor.RED));
            return;
        }
        if (!isInDomain(player)) return;

        SkillCost markCost = plugin.getAbilityConfigManager()
                .getSkillCost(getName(), "mark", Material.DIAMOND_BLOCK, 10);
        if (player.getInventory().getItemInMainHand().getType() != markCost.getItem()) return;
        if (Disruptor.tryFailSkill(plugin, player)) return;

        long now = System.currentTimeMillis();
        long until = markCooldown.getOrDefault(player.getUniqueId(), 0L);
        if (now < until) {
            player.sendMessage(Component.text("영역표시 쿨타임입니다.", NamedTextColor.RED));
            return;
        }

        if (!markCost.consumeFromInventory(player)) return;

        long cooldownMs = plugin.getAbilityConfigManager()
                .getLong(getName(), "skills.mark.cooldown-ms", 3L * 60L * 1000L);
        double damage = plugin.getAbilityConfigManager()
                .getDouble(getName(), "skills.mark.damage", 50.0);
        int debuffTicks = plugin.getAbilityConfigManager()
                .getInt(getName(), "skills.mark.debuff-ticks", 45 * 20);
        int weaknessAmp = plugin.getAbilityConfigManager()
                .getInt(getName(), "skills.mark.weakness-amplifier", 2);
        int slownessAmp = plugin.getAbilityConfigManager()
                .getInt(getName(), "skills.mark.slowness-amplifier", 2);
        markCooldown.put(player.getUniqueId(), now + cooldownMs);

        for (Entity entity : Bukkit.getOnlinePlayers()) {
            if (!(entity instanceof Player target)) continue;
            if (target.equals(player)) continue;
            if (isSameGuild(player, target)) continue;
            if (!isInDomainOf(player, target.getLocation())) continue;

            target.damage(damage, player);
            target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, debuffTicks, weaknessAmp, false, false));
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, debuffTicks, slownessAmp, false, false));
        }

        SkillParticles.domainMark(player);
        player.sendMessage(Component.text("[영역술사] 영역표시 발동!", NamedTextColor.GOLD));
    }

    private void startDomainTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (!isHasAbility(player)) continue;

                    if (!isInDomain(player)) {
                        removeDomainHealth(player);
                        continue;
                    }

                    player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 40, 1, false, false));
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 40, 2, false, false));
                    if (isExpanded(player)) {
                        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 40, 0, false, false));
                    }

                    applyDomainHealth(player);
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private boolean isExpanded(Player player) {
        Long until = expandedUntil.get(player.getUniqueId());
        if (until == null) return false;
        if (System.currentTimeMillis() > until) {
            expandedUntil.remove(player.getUniqueId());
            return false;
        }
        return true;
    }

    private void applyDomainHealth(Player player) {
        AttributeInstance attr = player.getAttribute(Attribute.MAX_HEALTH);
        if (attr == null) return;

        NamespacedKey key = getDomainHealthKey();
        boolean hasModifier = attr.getModifiers().stream().anyMatch(mod -> mod.getKey().equals(key));
        if (!hasModifier) {
            attr.addModifier(new AttributeModifier(key, DOMAIN_HEALTH_BONUS, AttributeModifier.Operation.ADD_NUMBER));
        }
    }

    private void removeDomainHealth(Player player) {
        AttributeInstance attr = player.getAttribute(Attribute.MAX_HEALTH);
        if (attr == null) return;

        NamespacedKey key = getDomainHealthKey();
        attr.getModifiers().stream()
                .filter(mod -> mod.getKey().equals(key))
                .forEach(attr::removeModifier);
    }

    private NamespacedKey getDomainHealthKey() {
        return new NamespacedKey(plugin, "domain-health");
    }

    private boolean isInDomain(Player player) {
        Location center = getHome(player);
        if (center == null) return false;
        if (!player.getWorld().equals(center.getWorld())) return false;

        int baseRadius = plugin.getAbilityConfigManager()
                .getInt(getName(), "stats.base-radius", 200);
        int expandedRadius = plugin.getAbilityConfigManager()
                .getInt(getName(), "stats.expanded-radius", 500);
        int radius = isExpanded(player) ? expandedRadius : baseRadius;

        return player.getLocation().distanceSquared(center) <= (double) radius * radius;
    }

    private boolean isInDomainOf(Player owner, Location location) {
        Location center = getHome(owner);
        if (center == null) return false;
        if (!location.getWorld().equals(center.getWorld())) return false;

        int baseRadius = plugin.getAbilityConfigManager()
                .getInt(getName(), "stats.base-radius", 200);
        int expandedRadius = plugin.getAbilityConfigManager()
                .getInt(getName(), "stats.expanded-radius", 500);
        int radius = isExpanded(owner) ? expandedRadius : baseRadius;

        return location.distanceSquared(center) <= (double) radius * radius;
    }

    private Location getHome(Player player) {
        PlayerData data = plugin.getAbilityManager().getPlayerData(player.getUniqueId());
        String stored = data.getDomainHome();
        if (stored == null || stored.isBlank()) return null;
        return deserializeHome(stored);
    }

    public static Location deserializeHome(String value) {
        try {
            String[] parts = value.split(",");
            if (parts.length != 4) return null;

            World world = Bukkit.getWorld(parts[0]);
            if (world == null) return null;

            double x = Double.parseDouble(parts[1]);
            double y = Double.parseDouble(parts[2]);
            double z = Double.parseDouble(parts[3]);
            return new Location(world, x, y, z);
        } catch (Exception ignored) {
            return null;
        }
    }

    public static String serializeHome(Location loc) {
        if (loc == null || loc.getWorld() == null) return null;
        return loc.getWorld().getName() + "," + loc.getX() + "," + loc.getY() + "," + loc.getZ();
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
