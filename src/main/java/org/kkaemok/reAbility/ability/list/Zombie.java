package org.kkaemok.reAbility.ability.list;

import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.kkaemok.reAbility.ability.AbilityBase;
import org.kkaemok.reAbility.ability.AbilityGrade;

public class Zombie extends AbilityBase {
    @Override
    public String getName() { return "ZOMBIE"; }
    @Override
    public String getDisplayName() { return "좀비"; }
    @Override
    public AbilityGrade getGrade() { return AbilityGrade.C; }
    @Override
    public String[] getDescription() { return new String[]{"체력이 4줄이 되지만 상시 나약함과 허기가 걸립니다.", "황금사과 섭취 시 나약함이 잠시 해제됩니다."}; }

    @Override
    public void onActivate(Player player) {
        player.getAttribute(Attribute.MAX_HEALTH).setBaseValue(80.0);
        player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, Integer.MAX_VALUE, 0, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, Integer.MAX_VALUE, 1, false, false));
    }

    @Override
    public void onDeactivate(Player player) {
        player.getAttribute(Attribute.MAX_HEALTH).setBaseValue(40.0); // 서버 기본 체력 기준
        player.removePotionEffect(PotionEffectType.WEAKNESS);
        player.removePotionEffect(PotionEffectType.HUNGER);
    }

    @EventHandler
    public void onEat(PlayerItemConsumeEvent event) {
        if (event.getItem().getType() == Material.GOLDEN_APPLE) {
            Player p = event.getPlayer();
            // 1틱 뒤 실행 (기존 효과 적용 후 변조)
            org.bukkit.Bukkit.getScheduler().runTaskLater(org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(getClass()), () -> {
                p.removePotionEffect(PotionEffectType.WEAKNESS);
                p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 1)); // 재생 2 (5초)

                // 30초 후 다시 나약함 부여
                org.bukkit.Bukkit.getScheduler().runTaskLater(org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(getClass()), () -> {
                    if (p.isOnline()) p.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, Integer.MAX_VALUE, 0, false, false));
                }, 600L);
            }, 1L);
        }
    }
}