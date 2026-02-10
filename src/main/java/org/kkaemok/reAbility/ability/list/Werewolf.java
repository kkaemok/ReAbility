package org.kkaemok.reAbility.ability.list;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.kkaemok.reAbility.ReAbility;
import org.kkaemok.reAbility.ability.AbilityBase;
import org.kkaemok.reAbility.ability.AbilityGrade;

public class Werewolf extends AbilityBase {
    private final ReAbility plugin;
    private boolean isNightActive = false;

    public Werewolf(ReAbility plugin) {
        this.plugin = plugin;
        startUpdateTask();
    }

    @Override public String getName() { return "WEREWOLF"; }
    @Override public String getDisplayName() { return "늑대인간"; }
    @Override public AbilityGrade getGrade() { return AbilityGrade.A; }

    @Override
    public String[] getDescription() {
        return new String[]{
                "밤에 힘 2, 체력 3줄, 폭발 피해 무효, 저항 2 버프 획득.",
                "낮이 되면 버프가 사라짐.",
                "스킬 {나의 시간}: 다이아 100개 소모 시 즉시 밤이 됨."
        };
    }

    private void startUpdateTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (!isHasAbility(player)) continue;

                    long time = player.getWorld().getTime();
                    boolean night = (time >= 13000 && time <= 23000);

                    if (night && !isNightActive) {
                        applyNightBuffs(player);
                    } else if (!night && isNightActive) {
                        removeNightBuffs(player);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void applyNightBuffs(Player player) {
        isNightActive = true;
        player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, Integer.MAX_VALUE, 1, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, Integer.MAX_VALUE, 1, false, false));

        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) maxHealth.setBaseValue(60.0);

        player.sendMessage(Component.text("[!] 밤이 되었습니다. 늑대인간의 힘이 깨어납니다.", NamedTextColor.RED));
        player.playSound(player.getLocation(), Sound.ENTITY_WOLF_HOWL, 1.0f, 0.8f);
    }

    private void removeNightBuffs(Player player) {
        isNightActive = false;
        player.removePotionEffect(PotionEffectType.STRENGTH);
        player.removePotionEffect(PotionEffectType.RESISTANCE);

        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(40.0);
            if (player.getHealth() > 40.0) player.setHealth(40.0);
        }

        player.sendMessage(Component.text("[!] 낮이 되었습니다. 힘이 사라집니다.", NamedTextColor.GRAY));
    }

    @EventHandler
    public void onExplode(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || !isHasAbility(player)) return;
        if (!isNightActive) return;

        if (event.getCause() == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION ||
                event.getCause() == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION) {
            event.setCancelled(true);
        }
    }

    @Override
    public void onSneakSkill(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType() != Material.DIAMOND || item.getAmount() < 100) return;

        item.setAmount(item.getAmount() - 100);
        player.getWorld().setTime(13000);

        Bukkit.broadcast(Component.text("==========", NamedTextColor.DARK_RED));
        Bukkit.broadcast(Component.text("늑대인간 " + player.getName() + "이 {나의 시간}을 사용하여 밤을 불러옵니다!",
                NamedTextColor.RED));
        Bukkit.broadcast(Component.text("==========", NamedTextColor.DARK_RED));

        for (Player online : Bukkit.getOnlinePlayers()) {
            online.playSound(online.getLocation(), Sound.ENTITY_WOLF_HOWL, 1.0f, 0.5f);
        }
    }

    private boolean isHasAbility(Player player) {
        return getName().equals(plugin.getAbilityManager().getPlayerData(player.getUniqueId()).getAbilityName());
    }
}
