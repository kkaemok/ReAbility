package org.kkaemok.reAbility.ability.list;

import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;
import org.kkaemok.reAbility.ReAbility;
import org.kkaemok.reAbility.ability.AbilityBase;
import org.kkaemok.reAbility.ability.AbilityGrade;

import java.util.Random;

public class Fisherman extends AbilityBase {
    private final ReAbility plugin;
    private final Random random = new Random();

    public Fisherman(ReAbility plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() { return "FISHERMAN"; }

    @Override
    public String getDisplayName() { return "낚시꾼"; }

    @Override
    public AbilityGrade getGrade() { return AbilityGrade.D; }

    @Override
    public String[] getDescription() {
        return new String[]{
                "낚시 시 희귀 아이템을 낚을 확률이 증가함.",
                "확률: 다이아 10개 10%, 네더라이트 파편 0.5%, 마황 0.5%,",
                "황금사과 5개 10%, 셜커상자 1%, 나머지는 기본 확률과 동일."
        };
    }

    @EventHandler
    public void onFish(PlayerFishEvent event) {
        Player player = event.getPlayer();
        if (!isHasAbility(player)) return;

        if (event.getState() == PlayerFishEvent.State.CAUGHT_FISH && event.getCaught() instanceof Item caught) {
            double chance = random.nextDouble() * 100;

            if (chance < 0.5) {
                caught.setItemStack(new ItemStack(Material.NETHERITE_SCRAP));
            } else if (chance < 1.0) {
                caught.setItemStack(new ItemStack(Material.ENCHANTED_GOLDEN_APPLE));
            } else if (chance < 2.0) {
                caught.setItemStack(new ItemStack(Material.SHULKER_BOX));
            } else if (chance < 12.0) {
                caught.setItemStack(new ItemStack(Material.DIAMOND, 10));
            } else if (chance < 22.0) {
                caught.setItemStack(new ItemStack(Material.GOLDEN_APPLE, 5));
            }
        }
    }

    private boolean isHasAbility(Player player) {
        String ability = plugin.getAbilityManager().getPlayerData(player.getUniqueId()).getAbilityName();
        return getName().equals(ability);
    }
}
