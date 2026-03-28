package org.kkaemok.reAbility.ability.list;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
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

public class Elementer extends AbilityBase {
    private final ReAbility plugin;
    private final GuildManager guildManager;
    private final Map<UUID, Long> waterCooldown = new HashMap<>();
    private final Map<UUID, Double> prevKnockback = new HashMap<>();

    public Elementer(ReAbility plugin, GuildManager guildManager) {
        this.plugin = plugin;
        this.guildManager = guildManager;
    }

    @Override public String getName() { return "ELEMENTER"; }
    @Override public String getDisplayName() { return "엘리멘터"; }
    @Override public AbilityGrade getGrade() { return AbilityGrade.B; }

    @Override
    public String[] getDescription() {
        return new String[]{
                "수중 호흡, 화염 저항, 저항 1, 넉백 저항.",
                "스킬 {물의 힘}: 다이아 50개 소모, 물속에서",
                "힘 3 + 저항 3 (1분, 쿨타임 3분).",
                "스킬 {불의 힘}: 레드스톤 50개 소모,",
                "주변 8칸 적에게 화염 + 데미지 30 + 구속 3, 어둠, 나약함 3 (10초)"
        };
    }

    @Override
    public void onActivate(Player player) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, PotionEffect.INFINITE_DURATION, 0, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, PotionEffect.INFINITE_DURATION, 0, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, PotionEffect.INFINITE_DURATION, 0, false, false));

        AttributeInstance knockback = player.getAttribute(Attribute.KNOCKBACK_RESISTANCE);
        if (knockback != null) {
            prevKnockback.put(player.getUniqueId(), knockback.getBaseValue());
            knockback.setBaseValue(0.5);
        }
    }

    @Override
    public void onDeactivate(Player player) {
        player.removePotionEffect(PotionEffectType.WATER_BREATHING);
        player.removePotionEffect(PotionEffectType.FIRE_RESISTANCE);
        player.removePotionEffect(PotionEffectType.RESISTANCE);
        player.removePotionEffect(PotionEffectType.STRENGTH);

        AttributeInstance knockback = player.getAttribute(Attribute.KNOCKBACK_RESISTANCE);
        if (knockback != null) {
            Double prev = prevKnockback.remove(player.getUniqueId());
            knockback.setBaseValue(prev != null ? prev : 0.0);
        }
    }

    @Override
    public void onSneakSkill(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        long now = System.currentTimeMillis();

        SkillCost waterCost = plugin.getAbilityConfigManager()
                .getSkillCost(getName(), "water", Material.DIAMOND, 50);
        if (waterCost.matchesHand(item)) {
            if (now < waterCooldown.getOrDefault(player.getUniqueId(), 0L)) {
                player.sendMessage(Component.text("물의 힘 쿨타임입니다.", NamedTextColor.RED));
                return;
            }
            if (!isInWater(player)) {
                player.sendMessage(Component.text("물속에서만 사용할 수 있습니다.", NamedTextColor.RED));
                return;
            }

            if (!waterCost.consumeFromHand(player)) return;
            long cooldownMs = plugin.getAbilityConfigManager()
                    .getLong(getName(), "skills.water.cooldown-ms", 180000L);
            int durationTicks = plugin.getAbilityConfigManager()
                    .getInt(getName(), "skills.water.duration-ticks", 1200);
            int strengthAmp = plugin.getAbilityConfigManager()
                    .getInt(getName(), "skills.water.strength-amplifier", 2);
            int resistanceAmp = plugin.getAbilityConfigManager()
                    .getInt(getName(), "skills.water.resistance-amplifier", 2);
            waterCooldown.put(player.getUniqueId(), now + cooldownMs);

            player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, durationTicks, strengthAmp, false, false));
            player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, durationTicks, resistanceAmp, false, false));
            SkillParticles.elementerWater(player);
            player.sendMessage(Component.text("[!] 스킬 {물의 힘} 발동!", NamedTextColor.AQUA));
            return;
        }

        SkillCost fireCost = plugin.getAbilityConfigManager()
                .getSkillCost(getName(), "fire", Material.REDSTONE, 50);
        if (fireCost.matchesHand(item)) {
            if (!fireCost.consumeFromHand(player)) return;
            int range = plugin.getAbilityConfigManager()
                    .getInt(getName(), "skills.fire.range", 8);
            double damage = plugin.getAbilityConfigManager()
                    .getDouble(getName(), "skills.fire.damage", 30.0);
            int fireTicks = plugin.getAbilityConfigManager()
                    .getInt(getName(), "skills.fire.fire-ticks", 200);
            int debuffTicks = plugin.getAbilityConfigManager()
                    .getInt(getName(), "skills.fire.debuff-ticks", 200);
            int slowAmp = plugin.getAbilityConfigManager()
                    .getInt(getName(), "skills.fire.slow-amplifier", 2);
            int weaknessAmp = plugin.getAbilityConfigManager()
                    .getInt(getName(), "skills.fire.weakness-amplifier", 2);

            for (Entity entity : player.getNearbyEntities(range, range, range)) {
                if (entity instanceof Player target && !target.equals(player) && !isSameGuild(player, target)) {
                    target.damage(damage, player);
                    target.setFireTicks(fireTicks);
                    target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, debuffTicks, slowAmp, false, false));
                    target.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, debuffTicks, 0, false, false));
                    target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, debuffTicks, weaknessAmp, false, false));
                }
            }
            SkillParticles.elementerFire(player);
            player.sendMessage(Component.text("[!] 스킬 {불의 힘} 발동!", NamedTextColor.RED));
        }
    }

    private boolean isSameGuild(Player p1, Player p2) {
        GuildData g1 = guildManager.getGuildByMember(p1.getUniqueId());
        GuildData g2 = guildManager.getGuildByMember(p2.getUniqueId());
        return g1 != null && g2 != null && g1.name.equalsIgnoreCase(g2.name);
    }

    private boolean isInWater(Player player) {
        Material feet = player.getLocation().getBlock().getType();
        Material eye = player.getEyeLocation().getBlock().getType();
        return isWaterLike(feet) || isWaterLike(eye);
    }

    private boolean isWaterLike(Material type) {
        return type == Material.WATER
                || type == Material.BUBBLE_COLUMN
                || type == Material.KELP
                || type == Material.KELP_PLANT
                || type == Material.SEAGRASS
                || type == Material.TALL_SEAGRASS;
    }
}
