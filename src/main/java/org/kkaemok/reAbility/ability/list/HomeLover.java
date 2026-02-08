package org.kkaemok.reAbility.ability.list;

import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.kkaemok.reAbility.ability.AbilityBase;
import org.kkaemok.reAbility.ability.AbilityGrade;

public class HomeLover extends AbilityBase {
    @Override
    public String getName() { return "HOMELOVER"; }
    @Override
    public String getDisplayName() { return "집순이"; }
    @Override
    public AbilityGrade getGrade() { return AbilityGrade.D; }
    @Override
    public String[] getDescription() { return new String[]{"TPA 쿨타임이 1분으로 감소하며,", "길드 가입 시 재생 1 효과를 얻습니다."}; }

    @Override
    public void onActivate(Player player) {
        // 길드 체크 로직은 별도의 매니저나 여기서 수행
        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, Integer.MAX_VALUE, 0, false, false));
    }

    @Override
    public void onDeactivate(Player player) {
        player.removePotionEffect(PotionEffectType.REGENERATION);
    }
}