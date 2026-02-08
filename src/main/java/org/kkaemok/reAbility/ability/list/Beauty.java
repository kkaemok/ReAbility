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
                "§6[S급 패시브] §f자신과 주변 길드원에게 §a재생 1, 저항 1 §f공유",
                "§c[패시브] §f적군이 나를 바라보면 심장이 멈춰 움직일 수 없습니다.",
                "§6[스킬: 미인계] §f다이아 30개 소모 (웅크리기)",
                "§f1시간 동안 상점 이용 가격이 낮아집니다.",
                "§6[스킬: 기분좋은 냄새] §f다이아 60개 소모 (웅크리기)",
                "§f주변 적에게 §7나약함 2, 구속 2, 멀미§f를 1분간 부여 (쿨타임 5분)"
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

                // 1. 버프 공유 (본인 포함 주변 50칸 아군)
                beauty.getNearbyEntities(50, 50, 50).stream()
                        .filter(e -> e instanceof Player)
                        .map(e -> (Player) e)
                        .filter(p -> isSameGuild(beauty, p))
                        .forEach(this::applyBuff);
                applyBuff(beauty);

                // 2. 심정지 패시브 (시선 체크)
                for (Player enemy : Bukkit.getOnlinePlayers()) {
                    if (enemy.getWorld().equals(beauty.getWorld()) && !isSameGuild(beauty, enemy)) {
                        if (enemy.getLocation().distance(beauty.getLocation()) < 50 && isLookingAt(enemy, beauty)) {
                            // SLOWNESS, JUMP_BOOST로 수정
                            enemy.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 10, 255, false, false));
                            enemy.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 10, 200, false, false));
                            enemy.sendActionBar(Component.text("§d미녀를 바라보아 심장이 멈췄습니다!"));
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

        // 1. 미인계 (할인 로직 포함)
        if (item.getType() == Material.DIAMOND && item.getAmount() >= 30) {
            item.setAmount(item.getAmount() - 30);
            discountTime.put(player.getUniqueId(), now + 3600000);
            player.sendMessage(Component.text("[!] {미인계} 발동! 1시간 동안 할인이 적용됩니다.", NamedTextColor.LIGHT_PURPLE));
            return;
        }

        // 2. 기분좋은 냄새
        if (item.getType() == Material.DIAMOND && item.getAmount() >= 60) {
            if (now < scentCooldown.getOrDefault(player.getUniqueId(), 0L)) {
                player.sendMessage(Component.text("쿨타임 중입니다.", NamedTextColor.RED));
                return;
            }

            item.setAmount(item.getAmount() - 60);
            scentCooldown.put(player.getUniqueId(), now + 300000);

            for (Entity entity : player.getNearbyEntities(20, 20, 20)) {
                if (entity instanceof Player target && !isSameGuild(player, target)) {
                    // SLOWNESS, NAUSEA로 수정
                    target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 1200, 1));
                    target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 1200, 1));
                    target.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 1200, 0));
                }
            }
            broadcastSkill(player, "{기분좋은 냄새}");
        }
    }

    // 할인 여부 확인 메서드 (상점 이벤트에서 호출 가능)
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
        // 실제 길드 로직 연동 시 여기에 작성
        return false;
    }

    private void broadcastSkill(Player player, String skillName) {
        Bukkit.broadcast(Component.text("[S] 미녀 " + player.getName() + "님이 " + skillName + "를 시전했습니다!", NamedTextColor.LIGHT_PURPLE));
    }
}