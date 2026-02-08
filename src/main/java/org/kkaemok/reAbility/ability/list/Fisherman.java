package org.kkaemok.reAbility.ability.list;

import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;
import org.kkaemok.reAbility.ability.AbilityBase;
import org.kkaemok.reAbility.ability.AbilityGrade;
import java.util.Random;

public class Fisherman extends AbilityBase {
    private final Random random = new Random();

    @Override
    public String getName() { return "FISHERMAN"; }
    @Override
    public String getDisplayName() { return "낚시꾼"; }
    @Override
    public AbilityGrade getGrade() { return AbilityGrade.D; }
    @Override
    public String[] getDescription() { return new String[]{"낚시 시 희귀 아이템을 낚을 확률이 대폭 증가합니다."}; }

    @EventHandler
    public void onFish(PlayerFishEvent event) {
        if (event.getState() == PlayerFishEvent.State.CAUGHT_FISH && event.getCaught() instanceof Item caught) {
            double chance = random.nextDouble() * 100;

            if (chance < 0.5) { // 0.5% 네더라이트 파편
                caught.setItemStack(new ItemStack(Material.NETHERITE_SCRAP));
            } else if (chance < 1.0) { // 0.5% 마황 (기획안의 마황 아이템으로 대체 필요)
                caught.setItemStack(new ItemStack(Material.ENCHANTED_GOLDEN_APPLE));
            } else if (chance < 2.0) { // 1% 셜커상자
                caught.setItemStack(new ItemStack(Material.SHULKER_BOX));
            } else if (chance < 12.0) { // 10% 다이아몬드 10개
                caught.setItemStack(new ItemStack(Material.DIAMOND, 10));
            } else if (chance < 22.0) { // 10% 황금사과 5개
                caught.setItemStack(new ItemStack(Material.GOLDEN_APPLE, 5));
            }
        }
    }
}