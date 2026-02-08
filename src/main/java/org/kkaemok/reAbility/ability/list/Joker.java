package org.kkaemok.reAbility.ability.list;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.*;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.kkaemok.reAbility.ReAbility;
import org.kkaemok.reAbility.ability.AbilityBase;
import org.kkaemok.reAbility.ability.AbilityGrade;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class Joker extends AbilityBase {
    private final ReAbility plugin;
    private final Map<UUID, Long> spyEndTime = new ConcurrentHashMap<>();
    private final Map<UUID, String> currentFakeNames = new ConcurrentHashMap<>();
    private final Map<UUID, Long> trickCooldown = new HashMap<>();
    private final Random random = new Random();

    public Joker(ReAbility plugin) {
        this.plugin = plugin;
        startIdentityTask();
    }

    @Override public String getName() { return "JOKER"; }
    @Override public String getDisplayName() { return "조커"; }
    @Override public AbilityGrade getGrade() { return AbilityGrade.S; }

    @Override
    public String[] getDescription() {
        return new String[]{
                "§6[S급 패시브] §f저항 1, 재생 1 상시 부여",
                "§e[패시브] §f1시간마다 이름표와 채팅 이름이 무작위로 변경됩니다.",
                "§6[스킬: 스파이] §f에메랄드 50개 소모, 3분간 모든 채팅 도청",
                "§6[스킬: 환상] §f네더라이트 파편 2개 소모, 적들에게 나약함 100 부여",
                "§6[스킬: 죽음의 트릭] §f다이아 100개 소모, 20% 확률 즉사 / 80% 자폭"
        };
    }

    @Override
    public void onActivate(Player player) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, PotionEffect.INFINITE_DURATION, 0, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, PotionEffect.INFINITE_DURATION, 0, false, false));
        changeIdentity(player);
    }

    @Override
    public void onDeactivate(Player player) {
        player.removePotionEffect(PotionEffectType.RESISTANCE);
        player.removePotionEffect(PotionEffectType.REGENERATION);
        resetIdentity(player);
    }

    private void startIdentityTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                Bukkit.getOnlinePlayers().stream()
                        .filter(Joker.this::isHasAbility)
                        .forEach(Joker.this::changeIdentity);
            }
        }.runTaskTimer(plugin, 72000L, 72000L);
    }

    private void changeIdentity(Player joker) {
        String fakeName = "Player" + (random.nextInt(9000) + 1000);
        currentFakeNames.put(joker.getUniqueId(), fakeName);

        // 자신을 제외한 모든 플레이어에게 가짜 이름 패킷 전송
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer.equals(joker)) continue;
            updateJokerNametag(joker, viewer, fakeName);
        }
        joker.sendMessage(Component.text("[!] 신분 세탁 완료. 현재 가짜 이름: " + fakeName, NamedTextColor.LIGHT_PURPLE));
    }

    private void updateJokerNametag(Player target, Player viewer, String customName) {
        viewer.hidePlayer(plugin, target);
        try {
            // 1. 정보 제거
            PacketContainer removePacket = new PacketContainer(PacketType.Play.Server.PLAYER_INFO_REMOVE);
            removePacket.getUUIDLists().write(0, Collections.singletonList(target.getUniqueId()));
            ProtocolLibrary.getProtocolManager().sendServerPacket(viewer, removePacket);

            // 2. 정보 추가 (가짜 이름으로 프로필 생성)
            PacketContainer infoPacket = new PacketContainer(PacketType.Play.Server.PLAYER_INFO);
            infoPacket.getPlayerInfoActions().write(0, EnumSet.of(EnumWrappers.PlayerInfoAction.ADD_PLAYER));

            WrappedGameProfile profile = WrappedGameProfile.fromPlayer(target);
            WrappedGameProfile customProfile = new WrappedGameProfile(target.getUniqueId(), customName);
            customProfile.getProperties().putAll(profile.getProperties()); // 스킨은 유지

            PlayerInfoData data = new PlayerInfoData(
                    target.getUniqueId(),
                    target.getPing(),
                    true,
                    EnumWrappers.NativeGameMode.fromBukkit(target.getGameMode()),
                    customProfile,
                    WrappedChatComponent.fromText(customName)
            );

            infoPacket.getPlayerInfoDataLists().write(1, Collections.singletonList(data));
            ProtocolLibrary.getProtocolManager().sendServerPacket(viewer, infoPacket);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "[ReAbility] 조커 패킷 전송 중 오류 발생", e);
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> viewer.showPlayer(plugin, target), 2L);
    }

    private void resetIdentity(Player joker) {
        currentFakeNames.remove(joker.getUniqueId());
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            viewer.hidePlayer(plugin, joker);
            viewer.showPlayer(plugin, joker);
        }
    }

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        Player sender = event.getPlayer();
        String rawMessage = PlainTextComponentSerializer.plainText().serialize(event.message());

        // 로직 1: 조커 본인이 채팅을 칠 때 가짜 이름으로 표시
        if (isHasAbility(sender) && currentFakeNames.containsKey(sender.getUniqueId())) {
            String fakeName = currentFakeNames.get(sender.getUniqueId());
            event.renderer((source, sourceDisplayName, message, viewer) ->
                    Component.text().append(Component.text(fakeName, NamedTextColor.WHITE))
                            .append(Component.text(": "))
                            .append(message).build()
            );
        }

        // 로직 2: 도청 기능 (Spy)
        spyEndTime.forEach((uuid, endTime) -> {
            if (System.currentTimeMillis() < endTime) {
                Player joker = Bukkit.getPlayer(uuid);
                if (joker != null && joker.isOnline() && !joker.equals(sender)) {
                    joker.sendMessage(Component.text("[도청] " + sender.getName() + ": " + rawMessage, NamedTextColor.GRAY));
                }
            } else {
                spyEndTime.remove(uuid);
            }
        });
    }

    @Override
    public void onSneakSkill(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        long now = System.currentTimeMillis();

        // 1. {스파이}
        if (item.getType() == Material.EMERALD && item.getAmount() >= 50) {
            item.setAmount(item.getAmount() - 50);
            spyEndTime.put(player.getUniqueId(), now + 180000);
            broadcastSkill(player, "{스파이}");
            return;
        }

        // 2. {환상}
        if (item.getType() == Material.NETHERITE_SCRAP && item.getAmount() >= 2) {
            item.setAmount(item.getAmount() - 2);
            broadcastSkill(player, "{환상}");
            for (Player target : Bukkit.getOnlinePlayers()) {
                if (!target.equals(player)) {
                    target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, Integer.MAX_VALUE, 99));
                }
            }
            return;
        }

        // 3. {죽음의 트릭}
        if (item.getType() == Material.DIAMOND && item.getAmount() >= 100) {
            if (now < trickCooldown.getOrDefault(player.getUniqueId(), 0L)) return;
            item.setAmount(item.getAmount() - 100);
            trickCooldown.put(player.getUniqueId(), now + 10800000);
            broadcastSkill(player, "{죽음의 트릭}");

            if (random.nextInt(100) < 20) {
                Bukkit.getOnlinePlayers().stream()
                        .filter(p -> !p.equals(player))
                        .findAny()
                        .ifPresent(victim -> victim.setHealth(0));
            } else {
                player.setHealth(0);
            }
        }
    }

    private void broadcastSkill(Player player, String skillName) {
        Bukkit.broadcast(Component.text("[S] " + player.getName() + "님이 " + skillName + " 스킬을 사용했습니다!", NamedTextColor.LIGHT_PURPLE));
    }

    private boolean isHasAbility(Player player) {
        return getName().equals(plugin.getAbilityManager().getPlayerData(player.getUniqueId()).getAbilityName());
    }
}