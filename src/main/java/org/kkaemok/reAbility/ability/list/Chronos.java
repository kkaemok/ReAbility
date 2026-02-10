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
                "힘 3, 재생 2 효과 획득 (주변 100칸 내 길드원 있으면 해제).",
                "패시브: 하루에 한 번 길드원에게 능력 1일 유지권 지급(최대 5일).",
                "스킬 {시간의 포효}: 네더라이트 주괴 2개 소모",
                "1000칸 내 적에게 피해 100 + 구속3, 멀미, 나약함1, 어둠, 허기3",
                "(쿨타임 10분)",
                "스킬 {카이로스의 지혜}: 네더라이트 파편 2개 획득 (쿨타임 1시간)",
                "스킬 {시간 단절}: 네더라이트 주괴 3개 소모,",
                "20초 의식 후 1분간 자신 제외 모두 경직 (쿨타임 1시간)",
                "* 경직 중 이동/공격 불가, 황금사과/토템 사용 가능"
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

        if (item.getType() == Material.NETHERITE_INGOT && item.getAmount() >= 2) {
            if (checkCooldown(player, roarCooldown, 600000, now)) {
                item.setAmount(item.getAmount() - 2);
                executeRoar(player);
                broadcastSkill(player, "{시간의 포효}");
            }
            return;
        }

        if (item.getType() == Material.NETHERITE_INGOT && item.getAmount() >= 3) {
            if (checkCooldown(player, stopCooldown, 3600000, now)) {
                item.setAmount(item.getAmount() - 3);
                startTimeSeverance(player);
                broadcastSkill(player, "{시간 단절}");
            }
            return;
        }

        if (checkCooldown(player, wisdomCooldown, 3600000, now)) {
            player.getInventory().addItem(new ItemStack(Material.NETHERITE_SCRAP, 2));
            player.sendMessage(Component.text("[!] 카이로스의 지혜로 네더라이트 파편 2개를 획득했습니다.",
                    NamedTextColor.GOLD));
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
        Bukkit.broadcast(Component.text("20초 뒤 SS등급 크로노스의 시간 단절 스킬이 발동됩니다.",
                NamedTextColor.RED));

        new BukkitRunnable() {
            int count = 20;
            @Override
            public void run() {
                if (count > 0) {
                    if (count <= 5 && chronos.isOnline()) {
                        chronos.sendMessage(Component.text(count + "...", NamedTextColor.YELLOW));
                    }
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

        Bukkit.broadcast(Component.text("[!] 시간 단절 발동! 1분 동안 크로노스 외 모두가 경직됩니다.",
                NamedTextColor.AQUA));

        new BukkitRunnable() {
            @Override
            public void run() {
                frozenPlayers.clear();
                Bukkit.broadcast(Component.text("[!] 시간 단절이 종료되었습니다.", NamedTextColor.GREEN));
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
        return p1.equals(p2);
    }

    private void broadcastSkill(Player player, String name) {
        Bukkit.broadcast(Component.text("[SS] 크로노스 " + player.getName() + " - " + name,
                NamedTextColor.LIGHT_PURPLE));
    }
}
