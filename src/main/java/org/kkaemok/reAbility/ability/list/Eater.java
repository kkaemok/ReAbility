package org.kkaemok.reAbility.ability.list;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.kkaemok.reAbility.ability.AbilityBase;
import org.kkaemok.reAbility.ability.AbilityGrade;

public class Eater extends AbilityBase {
    @Override
    public String getName() { return "EATER"; }
    @Override
    public String getDisplayName() { return "먹보"; }
    @Override
    public AbilityGrade getGrade() { return AbilityGrade.D; }
    @Override
    public String[] getDescription() { return new String[]{"배고픔이 닳지 않으며,", "상시 재생 1 효과를 얻습니다."}; }

    @Override
    public void onActivate(Player player) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, Integer.MAX_VALUE, 0, false, false));
    }

    @Override
    public void onDeactivate(Player player) {
        player.removePotionEffect(PotionEffectType.REGENERATION);
    }

    @EventHandler
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        // 배고픔이 닳지 않게 고정
        event.setCancelled(true);
        ((Player) event.getEntity()).setFoodLevel(20);
    }
}