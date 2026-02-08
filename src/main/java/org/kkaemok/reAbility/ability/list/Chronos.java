package org.kkaemok.reAbility.ability.list;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.kkaemok.reAbility.ReAbility;
import org.kkaemok.reAbility.ability.AbilityBase;
import org.kkaemok.reAbility.ability.AbilityGrade;

import java.util.*;

public class Chronos extends AbilityBase {
    private final ReAbility plugin;
    private final Map<UUID, Long> roarCooldown = new HashMap<>();
    private final Map<UUID, Long> wisdomCooldown = new HashMap<>();
    private final Map<UUID, Long> stopCooldown = new HashMap<>();
    private final Set<UUID> frozenPlayers = new HashSet<>();
    private BukkitRunnable passiveTask;

    public Chronos(ReAbility plugin) {
        this.plugin = plugin;
    }

    @Override public String getName() { return "CHRONOS"; }
    @Override public String getDisplayName() { return "크로노스"; }
    @Override public AbilityGrade getGrade() { return AbilityGrade.SS; }

    @Override
    public String[] getDescription() {
        return new String[]{
                "§e[SS급 패시브] §f힘 3, 재생 2 부여 (주변 100칸 내 길드원 감지 시 제거)",
                "§e[패시브] §f매일 모든 길드원에게 능력 1일 유지권 지급 (최대 5일)",
                "§6[스킬: 시간의 포효] §f네더라이트 주괴 2개 소모 (웅크리기)",
                "§f1000칸 내 적 데미지 100 + 각종 디버프 (쿨타임 10분)",
                "§6[스킬: 카이로스의 지혜] §f네더라이트 파편 2개 획득 (쿨타임 1시간)",
                "§b[스킬: 시간 단절] §f네더라이트 주괴 3개 소모 (웅크리기)",
                "§f20초 의식 후 자신 제외 전원 1분간 경직 (쿨타임 1시간)"
        };
    }

    @Override
    public void onActivate(Player player) {
        startPassiveTask(player);
    }

    @Override
    public void onDeactivate(Player player) {
        if (passiveTask != null) passiveTask.cancel();
        frozenPlayers.clear();
    }

    private void startPassiveTask(Player chronos) {
        passiveTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!chronos.isOnline()) { this.cancel(); return; }

                // 100칸 이내 길드원 체크
                boolean guildMemberNearby = chronos.getNearbyEntities(100, 100, 100).stream()
                        .filter(e -> e instanceof Player)
                        .map(e -> (Player) e)
                        .anyMatch(p -> !p.equals(chronos) && isSameGuild(chronos, p));

                if (!guildMemberNearby) {
                    chronos.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 40, 2, false, false));
                    chronos.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 40, 1, false, false));
                }
            }
        };
        passiveTask.runTaskTimer(plugin, 0L, 20L);
    }

    @Override
    public void onSneakSkill(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        long now = System.currentTimeMillis();

        // 1. 시간의 포효 (주괴 2개)
        if (item.getType() == Material.NETHERITE_INGOT && item.getAmount() >= 2) {
            if (checkCooldown(player, roarCooldown, 600000, now)) {
                item.setAmount(item.getAmount() - 2);
                executeRoar(player);
                broadcastSkill(player, "{시간의 포효}");
            }
            return;
        }

        // 2. 시간 단절 (주괴 3개)
        if (item.getType() == Material.NETHERITE_INGOT && item.getAmount() >= 3) {
            if (checkCooldown(player, stopCooldown, 3600000, now)) {
                item.setAmount(item.getAmount() - 3);
                startTimeSeverance(player);
                broadcastSkill(player, "{시간 단절}");
            }
            return;
        }

        // 3. 카이로스의 지혜 (쿨타임 한 시간)
        if (checkCooldown(player, wisdomCooldown, 3600000, now)) {
            player.getInventory().addItem(new ItemStack(Material.NETHERITE_SCRAP, 2));
            player.sendMessage(Component.text("[!] 카이로스의 지혜로 네더라이트 파편을 얻었습니다.", NamedTextColor.GOLD));
        }
    }

    private void executeRoar(Player chronos) {
        chronos.getNearbyEntities(1000, 500, 1000).stream()
                .filter(e -> e instanceof Player)
                .map(e -> (Player) e)
                .filter(p -> !isSameGuild(chronos, p))
                .forEach(target -> {
                    target.damage(100.0, chronos);
                    target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 400, 2));
                    target.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 400, 0));
                    target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 400, 0));
                    target.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 400, 0));
                    target.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, 400, 2));
                });
    }

    private void startTimeSeverance(Player chronos) {
        Bukkit.broadcast(Component.text("§c[!] 크로노스가 시간 단절 의식을 시작합니다 (20초)"));

        new BukkitRunnable() {
            int count = 20;
            @Override
            public void run() {
                if (count > 0) {
                    if (count <= 5) Bukkit.broadcast(Component.text("§e" + count + "..."));
                    count--;
                } else {
                    applyTimeStop(chronos);
                    this.cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void applyTimeStop(Player chronos) {
        Bukkit.getOnlinePlayers().stream()
                .filter(p -> !p.equals(chronos))
                .forEach(p -> frozenPlayers.add(p.getUniqueId()));

        Bukkit.broadcast(Component.text("§b§l[!] 시간이 단절되었습니다. 1분간 크로노스 외 모두가 경직됩니다."));

        new BukkitRunnable() {
            @Override
            public void run() {
                frozenPlayers.clear();
                Bukkit.broadcast(Component.text("§a[!] 시간의 흐름이 복구되었습니다."));
            }
        }.runTaskLater(plugin, 1200L);
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (frozenPlayers.contains(event.getPlayer().getUniqueId())) {
            Location from = event.getFrom();
            Location to = event.getTo();
            if (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ()) {
                event.setTo(event.getFrom());
            }
        }
    }

    @EventHandler
    public void onAttack(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player attacker && frozenPlayers.contains(attacker.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    private boolean checkCooldown(Player p, Map<UUID, Long> map, long ms, long now) {
        if (now < map.getOrDefault(p.getUniqueId(), 0L)) {
            p.sendMessage(Component.text("쿨타임 중입니다.", NamedTextColor.RED));
            return false;
        }
        map.put(p.getUniqueId(), now + ms);
        return true;
    }

    private boolean isSameGuild(Player p1, Player p2) {
        // p1, p2를 활용한 길드 체크 로직 (예시: p1의 길드 정보와 p2의 길드 정보 비교)
        return p1.equals(p2);
    }

    private void broadcastSkill(Player player, String name) {
        Bukkit.broadcast(Component.text("§e[SS] 크로노스 " + player.getName() + " §f- " + name));
    }
}