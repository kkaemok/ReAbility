package org.kkaemok.reAbility.ability.list;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.kkaemok.reAbility.ReAbility;
import org.kkaemok.reAbility.ability.AbilityBase;
import org.kkaemok.reAbility.ability.AbilityGrade;

public class Eater extends AbilityBase {
    private final ReAbility plugin;

    public Eater(ReAbility plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() { return "EATER"; }

    @Override
    public String getDisplayName() { return "먹보"; }

    @Override
    public AbilityGrade getGrade() { return AbilityGrade.D; }

    @Override
    public String[] getDescription() {
        return new String[]{
                "배고픔이 닳지 않음.",
                "24시간 재생 1 버프 획득."
        };
    }

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
        if (!(event.getEntity() instanceof Player player)) return;
        if (!isHasAbility(player)) return;

        event.setCancelled(true);
        player.setFoodLevel(20);
    }

    private boolean isHasAbility(Player player) {
        String ability = plugin.getAbilityManager().getPlayerData(player.getUniqueId()).getAbilityName();
        return getName().equals(ability);
    }
}
