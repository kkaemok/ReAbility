package org.kkaemok.reAbility.ability.list;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.kkaemok.reAbility.ReAbility;
import org.kkaemok.reAbility.ability.AbilityBase;
import org.kkaemok.reAbility.ability.AbilityGrade;

import java.util.*;

public class SpaceRuler extends AbilityBase {
    private final ReAbility plugin;
    private final Map<UUID, Long> rtpCooldown = new HashMap<>();
    private final Map<UUID, Long> skill1Cooldown = new HashMap<>(); // 공간 이동
    private final Map<UUID, Long> skill2Cooldown = new HashMap<>(); // 공간 절단
    private final Set<UUID> invinciblePlayers = new HashSet<>(); // 무적 상태 관리
    private final Random random = new Random();

    public SpaceRuler(ReAbility plugin) {
        this.plugin = plugin;
    }

    @Override public String getName() { return "SPACE_RULER"; }
    @Override public String getDisplayName() { return "공간 지배자"; }
    @Override public AbilityGrade getGrade() { return AbilityGrade.S; }

    @Override
    public String[] getDescription() {
        return new String[]{
                "§6[S급 패시브] §f저항 1, 재생 1 상시 부여",
                "§b[패시브] §f/rtp 쿨타임이 1분으로 감소, 범위 ±30000으로 증가.",
                "§fRTP 직후 30초간 유령(관전자) 상태가 됩니다.",
                "§6[스킬: 공간 이동] §f다이아 100개 + 네더라이트 주괴 1개 소모 (웅크리기)",
                "§f랜덤한 플레이어 주변 1000칸 이내로 이동 (쿨타임 3시간)",
                "§6[스킬: 공간 절단] §f다이아 50개 소모 (웅크리기)",
                "§f30초간 §c무적 §f및 §c힘 3 §f부여 (쿨타임 10분)"
        };
    }

    @Override
    public void onActivate(Player player) {
        // S급 공통 버프
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, PotionEffect.INFINITE_DURATION, 0, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, PotionEffect.INFINITE_DURATION, 0, false, false));
    }

    @Override
    public void onDeactivate(Player player) {
        player.removePotionEffect(PotionEffectType.RESISTANCE);
        player.removePotionEffect(PotionEffectType.REGENERATION);
        if (player.getGameMode() == GameMode.SPECTATOR) {
            player.setGameMode(GameMode.SURVIVAL); // 혹시 유령 상태면 해제
        }
        invinciblePlayers.remove(player.getUniqueId());
    }

    // 1. RTP 명령어 가로채기 및 커스텀 로직 실행
    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!isHasAbility(event.getPlayer())) return;

        String command = event.getMessage().toLowerCase();
        // /rtp 명령어 감지
        if (command.startsWith("/rtp") || command.startsWith("/randomtp")) {
            event.setCancelled(true);
            performCustomRTP(event.getPlayer());
        }
    }

    private void performCustomRTP(Player player) {
        long now = System.currentTimeMillis();
        // 쿨타임 1분 (60,000ms)
        if (now < rtpCooldown.getOrDefault(player.getUniqueId(), 0L)) {
            long left = (rtpCooldown.get(player.getUniqueId()) - now) / 1000;
            player.sendMessage(Component.text("공간 도약 준비 중... 남은 시간: " + left + "초", NamedTextColor.RED));
            return;
        }

        // 좌표 설정 (-30000 ~ 30000)
        int x = random.nextInt(60001) - 30000;
        int z = random.nextInt(60001) - 30000;
        int y = player.getWorld().getHighestBlockYAt(x, z) + 1;

        Location targetLoc = new Location(player.getWorld(), x, y, z);
        player.teleport(targetLoc);

        rtpCooldown.put(player.getUniqueId(), now + 60000); // 1분 쿨타임 적용

        // 유령 상태 (관전자) 30초
        player.setGameMode(GameMode.SPECTATOR);
        player.sendMessage(Component.text("[!] 공간을 비틀어 30초간 유령 상태가 됩니다.", NamedTextColor.AQUA));

        new BukkitRunnable() {
            @Override
            public void run() {
                if (player.isOnline()) {
                    player.setGameMode(GameMode.SURVIVAL);
                    // 관전자 풀릴 때 안전한 높이로 이동 (땅속 갇힘 방지)
                    Block b = player.getWorld().getHighestBlockAt(player.getLocation());
                    player.teleport(b.getLocation().add(0, 1, 0));
                    player.sendMessage(Component.text("[!] 실체화되었습니다.", NamedTextColor.GRAY));
                }
            }
        }.runTaskLater(plugin, 600L); // 30초
    }

    // 2. 무적 처리 (공간 절단)
    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player p && invinciblePlayers.contains(p.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @Override
    public void onSneakSkill(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        long now = System.currentTimeMillis();

        // ------------------------------------------------------------
        // 스킬 1: {공간 이동} (다이아 100개 + 네더라이트 주괴 1개)
        // ------------------------------------------------------------
        if (item.getType() == Material.DIAMOND && item.getAmount() >= 100) {
            // 네더라이트 주괴 체크 (인벤토리 검사)
            if (player.getInventory().contains(Material.NETHERITE_INGOT, 1)) {

                // 쿨타임 3시간
                if (now < skill1Cooldown.getOrDefault(player.getUniqueId(), 0L)) {
                    player.sendMessage(Component.text("공간 이동 쿨타임 중입니다.", NamedTextColor.RED));
                    return;
                }

                // 재료 소모
                item.setAmount(item.getAmount() - 100);
                removeItem(player, Material.NETHERITE_INGOT, 1);

                skill1Cooldown.put(player.getUniqueId(), now + 10800000); // 3시간

                // 랜덤 플레이어 타겟팅
                List<Player> targets = new ArrayList<>(Bukkit.getOnlinePlayers());
                if (targets.isEmpty()) {
                    player.sendMessage(Component.text("이동할 대상이 없습니다.", NamedTextColor.RED));
                    return;
                }

                Player target = targets.get(random.nextInt(targets.size())); // 자신 포함 가능

                // 타겟 주변 1000칸 이내 랜덤
                int offsetX = random.nextInt(2001) - 1000;
                int offsetZ = random.nextInt(2001) - 1000;
                Location tLoc = target.getLocation().add(offsetX, 0, offsetZ);
                tLoc.setY(tLoc.getWorld().getHighestBlockYAt(tLoc) + 1);

                player.teleport(tLoc);
                broadcastSkill(player, "{공간 이동}");
                player.sendMessage(Component.text("[!] " + target.getName() + " 주변으로 공간 이동했습니다.", NamedTextColor.LIGHT_PURPLE));
                return;
            }
        }

        // ------------------------------------------------------------
        // 스킬 2: {공간 절단} (다이아 50개)
        // ------------------------------------------------------------
        if (item.getType() == Material.DIAMOND && item.getAmount() >= 50) {
            // 쿨타임 10분
            if (now < skill2Cooldown.getOrDefault(player.getUniqueId(), 0L)) {
                player.sendMessage(Component.text("공간 절단 쿨타임 중입니다.", NamedTextColor.RED));
                return;
            }

            item.setAmount(item.getAmount() - 50);
            skill2Cooldown.put(player.getUniqueId(), now + 600000); // 10분

            // 효과 적용
            invinciblePlayers.add(player.getUniqueId());
            player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 600, 2)); // 힘 3 (Amplifier 2)

            broadcastSkill(player, "{공간 절단}");
            player.sendMessage(Component.text("[!] 30초간 무적 상태가 되며 힘이 솟구칩니다!", NamedTextColor.GOLD));

            // 30초 후 무적 해제
            new BukkitRunnable() {
                @Override
                public void run() {
                    invinciblePlayers.remove(player.getUniqueId());
                    if (player.isOnline()) {
                        player.sendMessage(Component.text("[!] 무적 상태가 종료되었습니다.", NamedTextColor.RED));
                    }
                }
            }.runTaskLater(plugin, 600L);
        }
    }

    private void removeItem(Player player, Material type, int amount) {
        for (ItemStack is : player.getInventory().getContents()) {
            if (is != null && is.getType() == type) {
                int newAmount = is.getAmount() - amount;
                if (newAmount > 0) {
                    is.setAmount(newAmount);
                    break;
                } else {
                    player.getInventory().remove(is);
                    amount = -newAmount;
                    if (amount == 0) break;
                }
            }
        }
    }

    private void broadcastSkill(Player player, String skillName) {
        Bukkit.broadcast(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_PURPLE));
        Bukkit.broadcast(Component.text("[S] 공간 지배자 " + player.getName() + "님이 " + skillName + " 스킬을 사용했습니다!", NamedTextColor.LIGHT_PURPLE));
        Bukkit.broadcast(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_PURPLE));
    }

    private boolean isHasAbility(Player player) {
        return getName().equals(plugin.getAbilityManager().getPlayerData(player.getUniqueId()).getAbilityName());
    }
}