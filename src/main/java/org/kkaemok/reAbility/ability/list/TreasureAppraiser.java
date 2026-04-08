package org.kkaemok.reAbility.ability.list;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.kkaemok.reAbility.ReAbility;
import org.kkaemok.reAbility.ability.AbilityBase;
import org.kkaemok.reAbility.ability.AbilityGrade;

import java.util.List;
import java.util.Random;

public class TreasureAppraiser extends AbilityBase {
    private static final double GOOD_QUALITY_CHANCE = 0.4;

    private final ReAbility plugin;
    private final Random random = new Random();

    public TreasureAppraiser(ReAbility plugin) {
        this.plugin = plugin;
        startInventorySetTask();
    }

    @Override
    public String getName() {
        return "TREASURE_APPRAISER";
    }

    @Override
    public String getDisplayName() {
        return "보물 감정가";
    }

    @Override
    public AbilityGrade getGrade() {
        return AbilityGrade.C;
    }

    @Override
    public String[] getDescription() {
        return new String[]{
                "다이아몬드 우클릭 시 40% 확률로 다이아 2개 획득.",
                "인벤에 네더라이트/다이아/금/레드스톤/에메랄드/청금석/석탄/철 보유 시",
                "스피드 2, 힘 1 획득."
        };
    }

    @EventHandler
    public void onRightClick(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!isHasAbility(player)) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (shouldIgnoreSneakRightClickBlock(event)) return;
        if (player.getInventory().getItemInMainHand().getType() != Material.DIAMOND) return;

        if (Disruptor.tryFailSkill(plugin, player)) {
            event.setCancelled(true);
            return;
        }

        if (random.nextDouble() < GOOD_QUALITY_CHANCE) {
            player.getInventory().addItem(new org.bukkit.inventory.ItemStack(Material.DIAMOND, 2));
            player.sendMessage(Component.text("[보물 감정가] 품질이 좋은 다이아입니다! +2", NamedTextColor.AQUA));
        } else {
            player.sendMessage(Component.text("[보물 감정가] 품질이 평범합니다.", NamedTextColor.GRAY));
        }
    }

    private void startInventorySetTask() {
        List<Material> required = List.of(
                Material.NETHERITE_INGOT,
                Material.DIAMOND,
                Material.GOLD_INGOT,
                Material.REDSTONE,
                Material.EMERALD,
                Material.LAPIS_LAZULI,
                Material.COAL,
                Material.IRON_INGOT
        );

        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (!isHasAbility(player)) continue;
                    if (!hasAllMaterials(player, required)) continue;

                    player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 40, 1, false, false));
                    player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 40, 0, false, false));
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private boolean hasAllMaterials(Player player, List<Material> required) {
        for (Material material : required) {
            if (!player.getInventory().contains(material)) {
                return false;
            }
        }
        return true;
    }

    private boolean isHasAbility(Player player) {
        String ability = plugin.getAbilityManager().getPlayerData(player.getUniqueId()).getAbilityName();
        return getName().equals(ability);
    }
}
