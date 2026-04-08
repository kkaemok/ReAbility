package org.kkaemok.reAbility.ability.list;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.kkaemok.reAbility.ReAbility;
import org.kkaemok.reAbility.ability.AbilityBase;
import org.kkaemok.reAbility.ability.AbilityGrade;
import org.kkaemok.reAbility.guild.GuildData;
import org.kkaemok.reAbility.guild.GuildManager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Bulldozer extends AbilityBase {
    private static final double HEALTH_BONUS = 10.0;

    private final ReAbility plugin;
    private final GuildManager guildManager;
    private final Map<String, Long> dashHitCooldown = new HashMap<>();

    public Bulldozer(ReAbility plugin) {
        this.plugin = plugin;
        this.guildManager = plugin.getGuildManager();
        startStateTask();
    }

    @Override
    public String getName() {
        return "BULLDOZER";
    }

    @Override
    public String getDisplayName() {
        return "불도저";
    }

    @Override
    public AbilityGrade getGrade() {
        return AbilityGrade.C;
    }

    @Override
    public String[] getDescription() {
        return new String[]{
                "HP 5칸 증가, 체력이 낮을수록 강해짐.",
                "17칸↓ 스피드1, 14칸↓ 힘1, 10칸↓ 저항1, 8칸↓ 스피드2, 6칸↓ 재생2",
                "4칸↓ 웅크리면 1초에 5칸 전진 + 적 밀쳐내기 + 피해10, 웅크림 중 저항2",
                "1칸↓ 힘6"
        };
    }

    @Override
    public void onActivate(Player player) {
        applyHealthBonus(player);
    }

    @Override
    public void onDeactivate(Player player) {
        removeHealthBonus(player);
        player.removePotionEffect(PotionEffectType.SPEED);
        player.removePotionEffect(PotionEffectType.STRENGTH);
        player.removePotionEffect(PotionEffectType.RESISTANCE);
        player.removePotionEffect(PotionEffectType.REGENERATION);
    }

    private void startStateTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (!isHasAbility(player)) continue;

                    applyHealthBonus(player);
                    applyHealthBasedBuffs(player);
                    handleDash(player);
                }
            }
        }.runTaskTimer(plugin, 0L, 4L);
    }

    private void applyHealthBasedBuffs(Player player) {
        double health = player.getHealth();
        int shortTicks = 20;

        if (health <= 34.0 && health > 16.0) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, shortTicks, 0, false, false));
        }
        if (health <= 14.0 * 2.0) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, shortTicks, 0, false, false));
        }
        if (health <= 10.0 * 2.0) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, shortTicks, 0, false, false));
        }
        if (health <= 8.0 * 2.0) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, shortTicks, 1, false, false));
        }
        if (health <= 6.0 * 2.0) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, shortTicks, 1, false, false));
        }
        if (health <= 1.0 * 2.0) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, shortTicks, 5, false, false));
        }
    }

    private void handleDash(Player player) {
        if (!player.isSneaking()) return;
        if (player.getHealth() > 4.0 * 2.0) return;

        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 20, 1, false, false));

        Vector direction = player.getLocation().getDirection().setY(0);
        if (direction.lengthSquared() < 0.0001) return;
        Vector dash = direction.normalize().multiply(1.0);
        player.setVelocity(dash);

        long now = System.currentTimeMillis();
        for (Entity entity : player.getNearbyEntities(2.5, 1.5, 2.5)) {
            if (!(entity instanceof Player target)) continue;
            if (target.equals(player)) continue;
            if (isSameGuild(player, target)) continue;

            Vector toTarget = target.getLocation().toVector().subtract(player.getLocation().toVector()).setY(0);
            if (toTarget.lengthSquared() < 0.0001) continue;
            double facingDot = direction.normalize().dot(toTarget.normalize());
            if (facingDot < 0.2) continue;

            String key = player.getUniqueId() + ":" + target.getUniqueId();
            long until = dashHitCooldown.getOrDefault(key, 0L);
            if (now < until) continue;
            dashHitCooldown.put(key, now + 500L);

            target.damage(10.0, player);
            target.setVelocity(dash.clone().multiply(1.2).setY(0.3));
        }
    }

    private void applyHealthBonus(Player player) {
        AttributeInstance attr = player.getAttribute(Attribute.MAX_HEALTH);
        if (attr == null) return;
        NamespacedKey key = getHealthKey();
        boolean hasModifier = attr.getModifiers().stream().anyMatch(mod -> mod.getKey().equals(key));
        if (!hasModifier) {
            attr.addModifier(new AttributeModifier(key, HEALTH_BONUS, AttributeModifier.Operation.ADD_NUMBER));
        }
    }

    private void removeHealthBonus(Player player) {
        AttributeInstance attr = player.getAttribute(Attribute.MAX_HEALTH);
        if (attr == null) return;
        NamespacedKey key = getHealthKey();
        attr.getModifiers().stream()
                .filter(mod -> mod.getKey().equals(key))
                .forEach(attr::removeModifier);
    }

    private NamespacedKey getHealthKey() {
        return new NamespacedKey(plugin, "bulldozer-health");
    }

    private boolean isSameGuild(Player p1, Player p2) {
        if (p1.equals(p2)) return true;
        GuildData g1 = guildManager.getGuildByMember(p1.getUniqueId());
        GuildData g2 = guildManager.getGuildByMember(p2.getUniqueId());
        return g1 != null && g2 != null && g1.name.equalsIgnoreCase(g2.name);
    }

    private boolean isHasAbility(Player player) {
        String ability = plugin.getAbilityManager().getPlayerData(player.getUniqueId()).getAbilityName();
        return getName().equals(ability);
    }
}
