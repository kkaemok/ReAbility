package org.kkaemok.reAbility.ability.list;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.kkaemok.reAbility.ReAbility;
import org.kkaemok.reAbility.ability.AbilityBase;
import org.kkaemok.reAbility.ability.AbilityGrade;

public class FashionLeader extends AbilityBase {
    private final ReAbility plugin;

    public FashionLeader(ReAbility plugin) {
        this.plugin = plugin;
        startEffectTask();
    }

    @Override
    public String getName() {
        return "FASHION_LEADER";
    }

    @Override
    public String getDisplayName() {
        return "유행의 선도자";
    }

    @Override
    public AbilityGrade getGrade() {
        return AbilityGrade.D;
    }

    @Override
    public String[] getDescription() {
        return new String[]{
                "자신의 갑옷에 있는 갑옷 장식 개수에 따라 강해짐.",
                "1개: 힘 1",
                "2개: 힘 1, 체력 3칸 증가",
                "3개: 힘 1, 체력 5칸 증가",
                "4개: 힘 1, 재생 1, 체력 5칸 증가"
        };
    }

    @Override
    public void onDeactivate(Player player) {
        clearEffects(player);
    }

    private void startEffectTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (!isHasAbility(player)) continue;
                    applyEffects(player);
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void applyEffects(Player player) {
        int trimCount = countTrimmedArmor(player);

        if (trimCount >= 1) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 40, 0, false, false));
        } else {
            player.removePotionEffect(PotionEffectType.STRENGTH);
        }

        if (trimCount >= 4) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 40, 0, false, false));
        } else {
            player.removePotionEffect(PotionEffectType.REGENERATION);
        }

        double healthBonus = switch (trimCount) {
            case 2 -> 6.0;
            case 3, 4 -> 10.0;
            default -> 0.0;
        };
        applyHealthBonus(player, healthBonus);
    }

    private int countTrimmedArmor(Player player) {
        int count = 0;
        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (armor == null || armor.getType().isAir()) continue;
            if (!(armor.getItemMeta() instanceof ArmorMeta armorMeta)) continue;
            ArmorTrim trim = armorMeta.getTrim();
            if (trim != null) {
                count++;
            }
        }
        return count;
    }

    private void applyHealthBonus(Player player, double amount) {
        AttributeInstance attr = player.getAttribute(Attribute.MAX_HEALTH);
        if (attr == null) return;

        NamespacedKey key = getHealthKey();
        attr.getModifiers().stream()
                .filter(mod -> mod.getKey().equals(key))
                .forEach(attr::removeModifier);

        if (amount > 0.0) {
            attr.addModifier(new AttributeModifier(key, amount, AttributeModifier.Operation.ADD_NUMBER));
        }

        double maxHealth = attr.getValue();
        if (player.getHealth() > maxHealth) {
            player.setHealth(maxHealth);
        }
    }

    private void clearEffects(Player player) {
        player.removePotionEffect(PotionEffectType.STRENGTH);
        player.removePotionEffect(PotionEffectType.REGENERATION);
        applyHealthBonus(player, 0.0);
    }

    private NamespacedKey getHealthKey() {
        return new NamespacedKey(plugin, "fashion-leader-health");
    }

    private boolean isHasAbility(Player player) {
        return getName().equals(plugin.getAbilityManager().getPlayerData(player.getUniqueId()).getAbilityName());
    }
}
