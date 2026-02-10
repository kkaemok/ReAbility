package org.kkaemok.reAbility.ability.list;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.kkaemok.reAbility.ReAbility;
import org.kkaemok.reAbility.ability.AbilityBase;
import org.kkaemok.reAbility.ability.AbilityGrade;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Beauty extends AbilityBase {
    private final ReAbility plugin;
    private final Map<UUID, Long> discountTime = new HashMap<>();
    private final Map<UUID, Long> scentCooldown = new HashMap<>();
    private BukkitRunnable passiveTask;

    public Beauty(ReAbility plugin) {
        this.plugin = plugin;
    }

    @Override public String getName() { return "BEAUTY"; }
    @Override public String getDisplayName() { return "미녀"; }
    @Override public AbilityGrade getGrade() { return AbilityGrade.S; }

    @Override
    public String[] getDescription() {
        return new String[]{
                "길드원에게 재생 1, 저항 1 효과 공유.",
                "상대팀이 자신을 바라보면 움직일 수 없음.",
                "스킬 {미인계}: 다이아 30개 소모, 상점 가격 1시간 감소",
                "스킬 {기분 좋은 냄새}: 다이아 60개 소모,",
                "주변 적에게 나약함 2, 구속 2, 멀미 (1분, 쿨타임 5분)"
        };
    }

    @Override
    public void onActivate(Player player) {
        startPassiveTask(player);
    }

    @Override
    public void onDeactivate(Player player) {
        if (passiveTask != null) passiveTask.cancel();
        discountTime.remove(player.getUniqueId());
    }

    private void startPassiveTask(Player beauty) {
        passiveTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!beauty.isOnline()) {
                    this.cancel();
                    return;
                }

                beauty.getNearbyEntities(50, 50, 50).stream()
                        .filter(e -> e instanceof Player)
                        .map(e -> (Player) e)
                        .filter(p -> isSameGuild(beauty, p))
                        .forEach(this::applyBuff);
                applyBuff(beauty);

                for (Player enemy : Bukkit.getOnlinePlayers()) {
                    if (enemy.getWorld().equals(beauty.getWorld()) && !isSameGuild(beauty, enemy)) {
                        if (enemy.getLocation().distance(beauty.getLocation()) < 50 && isLookingAt(enemy, beauty)) {
                            enemy.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 10, 255, false, false));
                            enemy.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 10, 200, false, false));
                            enemy.sendActionBar(Component.text("미녀를 바라보아 움직일 수 없습니다."));
                        }
                    }
                }
            }
            private void applyBuff(Player p) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 40, 0, false, false));
                p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 40, 0, false, false));
            }
        };
        passiveTask.runTaskTimer(plugin, 0L, 5L);
    }

    @Override
    public void onSneakSkill(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        long now = System.currentTimeMillis();

        if (item.getType() == Material.DIAMOND && item.getAmount() >= 30) {
            item.setAmount(item.getAmount() - 30);
            discountTime.put(player.getUniqueId(), now + 3600000);
            player.sendMessage(Component.text("[!] 스킬 {미인계} 발동! 1시간 동안 상점 가격이 낮아집니다.",
                    NamedTextColor.LIGHT_PURPLE));
            return;
        }

        if (item.getType() == Material.DIAMOND && item.getAmount() >= 60) {
            if (now < scentCooldown.getOrDefault(player.getUniqueId(), 0L)) {
                player.sendMessage(Component.text("쿨타임 중입니다.", NamedTextColor.RED));
                return;
            }

            item.setAmount(item.getAmount() - 60);
            scentCooldown.put(player.getUniqueId(), now + 300000);

            for (Entity entity : player.getNearbyEntities(20, 20, 20)) {
                if (entity instanceof Player target && !isSameGuild(player, target)) {
                    target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 1200, 1));
                    target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 1200, 1));
                    target.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 1200, 0));
                }
            }
            broadcastSkill(player, "{기분 좋은 냄새}");
        }
    }

    public boolean hasDiscount(Player player) {
        return System.currentTimeMillis() < discountTime.getOrDefault(player.getUniqueId(), 0L);
    }

    private boolean isLookingAt(Player observer, Player target) {
        Location eye = observer.getEyeLocation();
        Vector toTarget = target.getEyeLocation().toVector().subtract(eye.toVector());
        return eye.getDirection().normalize().dot(toTarget.normalize()) > 0.8;
    }

    private boolean isSameGuild(Player p1, Player p2) {
        if (p1.equals(p2)) return true;
        return false;
    }

    private void broadcastSkill(Player player, String skillName) {
        Bukkit.broadcast(Component.text("[S] 미녀 " + player.getName() + "이(가) " + skillName + "을 사용했습니다!",
                NamedTextColor.LIGHT_PURPLE));
    }
}
