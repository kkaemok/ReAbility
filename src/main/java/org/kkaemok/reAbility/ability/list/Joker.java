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
                "닉네임과 스킨이 1시간마다 바뀜.",
                "스킬 {스파이}: 에메랄드 50개 소모, 3분간 채팅 감청",
                "(쿨타임 30분)",
                "스킬 {환상}: 네더라이트 파편 2개 소모, 나약함 100 부여",
                "(쿨타임 15분)",
                "스킬 {죽음의 트릭}: 다이아 100개 소모, 80% 본인 사망",
                "20% 확률로 랜덤 플레이어 즉사 (쿨타임 3시간)"
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

        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer.equals(joker)) continue;
            updateJokerNametag(joker, viewer, fakeName);
        }
        joker.sendMessage(Component.text("[!] 신분 위장 완료. 현재 닉네임: " + fakeName, NamedTextColor.LIGHT_PURPLE));
    }

    private void updateJokerNametag(Player target, Player viewer, String customName) {
        viewer.hidePlayer(plugin, target);
        try {
            PacketContainer removePacket = new PacketContainer(PacketType.Play.Server.PLAYER_INFO_REMOVE);
            removePacket.getUUIDLists().write(0, Collections.singletonList(target.getUniqueId()));
            ProtocolLibrary.getProtocolManager().sendServerPacket(viewer, removePacket);

            PacketContainer infoPacket = new PacketContainer(PacketType.Play.Server.PLAYER_INFO);
            infoPacket.getPlayerInfoActions().write(0, EnumSet.of(EnumWrappers.PlayerInfoAction.ADD_PLAYER));

            WrappedGameProfile profile = WrappedGameProfile.fromPlayer(target);
            WrappedGameProfile customProfile = new WrappedGameProfile(target.getUniqueId(), customName);
            customProfile.getProperties().putAll(profile.getProperties());

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
            plugin.getLogger().log(Level.SEVERE, "[ReAbility] 조커 위장 패킷 전송 중 오류 발생", e);
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

        if (isHasAbility(sender) && currentFakeNames.containsKey(sender.getUniqueId())) {
            String fakeName = currentFakeNames.get(sender.getUniqueId());
            event.renderer((source, sourceDisplayName, message, viewer) ->
                    Component.text().append(Component.text(fakeName, NamedTextColor.WHITE))
                            .append(Component.text(": "))
                            .append(message).build()
            );
        }

        spyEndTime.forEach((uuid, endTime) -> {
            if (System.currentTimeMillis() < endTime) {
                Player joker = Bukkit.getPlayer(uuid);
                if (joker != null && joker.isOnline() && !joker.equals(sender)) {
                    joker.sendMessage(Component.text("[스파이] " + sender.getName() + ": " + rawMessage, NamedTextColor.GRAY));
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

        if (item.getType() == Material.EMERALD && item.getAmount() >= 50) {
            item.setAmount(item.getAmount() - 50);
            spyEndTime.put(player.getUniqueId(), now + 180000);
            broadcastSkill(player, "{스파이}");
            return;
        }

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
        Bukkit.broadcast(Component.text("[S] 조커 " + player.getName() + "이(가) " + skillName + "을 사용했습니다!",
                NamedTextColor.LIGHT_PURPLE));
    }

    private boolean isHasAbility(Player player) {
        return getName().equals(plugin.getAbilityManager().getPlayerData(player.getUniqueId()).getAbilityName());
    }
}
