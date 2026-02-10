package org.kkaemok.reAbility.ability.list;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.kkaemok.reAbility.ReAbility;
import org.kkaemok.reAbility.ability.AbilityBase;
import org.kkaemok.reAbility.ability.AbilityGrade;
import org.kkaemok.reAbility.data.PlayerData;

import java.util.Random;

public class Miner extends AbilityBase {
    private final ReAbility plugin;
    private final Random random = new Random();

    public Miner(ReAbility plugin) {
        this.plugin = plugin;
        startUndergroundCheck();
    }

    @Override public String getName() { return "MINER"; }
    @Override public String getDisplayName() { return "광부"; }
    @Override public AbilityGrade getGrade() { return AbilityGrade.A; }

    @Override
    public String[] getDescription() {
        return new String[]{
                "24시간 채굴속도 증가 2 버프 획득.",
                "다이아몬드 1시간 제한: 100개 -> 700개",
                "고대잔해 3시간 제한: 2개 -> 7개",
                "스킬 {연금술}: 철 50개로 다이아 50개 교환",
                "Y좌표 0 이하에서 저항 2, 힘 1, 재생 1 버프 획득."
        };
    }

    @Override
    public void onActivate(Player player) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, PotionEffect.INFINITE_DURATION, 1, false, false));
    }

    @Override
    public void onDeactivate(Player player) {
        player.removePotionEffect(PotionEffectType.HASTE);
        player.removePotionEffect(PotionEffectType.RESISTANCE);
        player.removePotionEffect(PotionEffectType.STRENGTH);
        player.removePotionEffect(PotionEffectType.REGENERATION);
    }

    private void startUndergroundCheck() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (!isHasAbility(player)) continue;

                    if (player.getLocation().getBlockY() <= 0) {
                        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 40, 1, false, false));
                        player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 40, 0, false, false));
                        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 40, 0, false, false));
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    @EventHandler
    public void onMine(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (!isHasAbility(player)) return;

        Block block = event.getBlock();
        Material type = block.getType();

        if (type == Material.DIAMOND_ORE || type == Material.DEEPSLATE_DIAMOND_ORE) {
            block.getWorld().dropItemNaturally(block.getLocation(), new ItemStack(Material.DIAMOND, 6));
        } else if (type == Material.ANCIENT_DEBRIS) {
            int extra = random.nextBoolean() ? 3 : 2;
            block.getWorld().dropItemNaturally(block.getLocation(), new ItemStack(Material.ANCIENT_DEBRIS, extra));
        }
    }

    @Override
    public void onSneakSkill(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();

        if (item.getType() == Material.IRON_INGOT && item.getAmount() >= 50) {
            item.setAmount(item.getAmount() - 50);
            player.getInventory().addItem(new ItemStack(Material.DIAMOND, 50));

            player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.0f);

            Bukkit.broadcast(Component.text("==========", NamedTextColor.GOLD));
            Bukkit.broadcast(Component.text("광부 " + player.getName() + "이(가) {연금술}을 사용했습니다!",
                    NamedTextColor.YELLOW));
            Bukkit.broadcast(Component.text("==========", NamedTextColor.GOLD));
        }
    }

    private boolean isHasAbility(Player player) {
        PlayerData data = plugin.getAbilityManager().getPlayerData(player.getUniqueId());
        if (data == null || data.getAbilityName() == null) return false;
        return data.getAbilityName().equals(getName());
    }
}
