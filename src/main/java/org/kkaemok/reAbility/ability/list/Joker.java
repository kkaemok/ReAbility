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
import org.kkaemok.reAbility.guild.GuildData;
import org.kkaemok.reAbility.guild.GuildManager;
import org.kkaemok.reAbility.utils.AbilityTags;
import org.kkaemok.reAbility.utils.InventoryUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class Joker extends AbilityBase {
    private final ReAbility plugin;
    private final GuildManager guildManager;
    private final Map<UUID, Long> spyEndTime = new ConcurrentHashMap<>();
    private final Map<UUID, String> currentFakeNames = new ConcurrentHashMap<>();
    private final Map<UUID, Long> spyCooldown = new HashMap<>();
    private final Map<UUID, Long> illusionCooldown = new HashMap<>();
    private final Map<UUID, Long> trickCooldown = new HashMap<>();
    private final Random random = new Random();

    public Joker(ReAbility plugin) {
        this.plugin = plugin;
        this.guildManager = plugin.getGuildManager();
        startIdentityTask();
    }

    @Override public String getName() { return "JOKER"; }
    @Override public String getDisplayName() { return "조커"; }
    @Override public AbilityGrade getGrade() { return AbilityGrade.S; }

    @Override
    public String[] getDescription() {
        return new String[]{
                "닉네임과 스킨이 1시간마다 바뀜.",
                "스킬 {스파이}: 에메랄드 50개 소모, 3분간 다른 길드 채팅 감청",
                "(쿨타임 30분)",
                "스킬 {환상}: 네더라이트 파편 2개 소모,",
                "자신/길드원 제외 전원 나약함 100(거품 없음) 무한",
                "에메랄드 1개 들고 웅크리면 해제 (쿨타임 15분)",
                "스킬 {죽음의 트릭}: 다이아 100개 소모, 80% 본인 사망",
                "20% 확률로 길드원 제외 랜덤 플레이어 즉사 (쿨타임 3시간)"
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
        spyEndTime.remove(player.getUniqueId());
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

        if (!guildManager.isGuildChatMode(sender.getUniqueId())) return;
        GuildData senderGuild = guildManager.getGuildByMember(sender.getUniqueId());
        if (senderGuild == null) return;

        spyEndTime.forEach((uuid, endTime) -> {
            if (System.currentTimeMillis() < endTime) {
                Player joker = Bukkit.getPlayer(uuid);
                if (joker != null && joker.isOnline() && !joker.equals(sender) && isHasAbility(joker)) {
                    GuildData jokerGuild = guildManager.getGuildByMember(joker.getUniqueId());
                    if (jokerGuild != null && jokerGuild.name.equalsIgnoreCase(senderGuild.name)) return;
                    joker.sendMessage(Component.text("[스파이][길드챗] " + sender.getName() + ": " + rawMessage, NamedTextColor.GRAY));
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

        if (item.getType() == Material.EMERALD) {
            if (now < spyCooldown.getOrDefault(player.getUniqueId(), 0L)) {
                player.sendMessage(Component.text("스파이 쿨타임입니다.", NamedTextColor.RED));
                return;
            }
            if (item.getAmount() < 50) return;
            item.setAmount(item.getAmount() - 50);
            spyEndTime.put(player.getUniqueId(), now + 180000);
            spyCooldown.put(player.getUniqueId(), now + 1800000);
            player.sendMessage(Component.text("[!] 스킬 {스파이}가 발동되었습니다.", NamedTextColor.GRAY));
            return;
        }

        if (item.getType() == Material.NETHERITE_SCRAP) {
            if (now < illusionCooldown.getOrDefault(player.getUniqueId(), 0L)) {
                player.sendMessage(Component.text("환상 쿨타임입니다.", NamedTextColor.RED));
                return;
            }
            if (item.getAmount() < 2) return;
            item.setAmount(item.getAmount() - 2);
            illusionCooldown.put(player.getUniqueId(), now + 900000);
            broadcastSkill(player, "{환상}");
            for (Player target : Bukkit.getOnlinePlayers()) {
                if (!target.equals(player) && !isSameGuild(player, target)) {
                    target.addPotionEffect(new PotionEffect(
                            PotionEffectType.WEAKNESS, PotionEffect.INFINITE_DURATION, 99, false, false));
                    target.addScoreboardTag(AbilityTags.JOKER_ILLUSION);
                }
            }
            return;
        }

        if (item.getType() == Material.DIAMOND) {
            if (now < trickCooldown.getOrDefault(player.getUniqueId(), 0L)) {
                player.sendMessage(Component.text("죽음의 트릭 쿨타임입니다.", NamedTextColor.RED));
                return;
            }
            if (!InventoryUtils.consume(player, Material.DIAMOND, 100)) return;
            trickCooldown.put(player.getUniqueId(), now + 10800000);
            broadcastSkill(player, "{죽음의 트릭}");

            if (random.nextInt(100) < 20) {
                List<Player> targets = new ArrayList<>();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.equals(player)) continue;
                    if (isSameGuild(player, p)) continue;
                    targets.add(p);
                }
                if (!targets.isEmpty()) {
                    Player victim = targets.get(random.nextInt(targets.size()));
                    victim.setHealth(0);
                }
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

    private boolean isSameGuild(Player p1, Player p2) {
        if (p1.equals(p2)) return true;
        GuildData g1 = guildManager.getGuildByMember(p1.getUniqueId());
        GuildData g2 = guildManager.getGuildByMember(p2.getUniqueId());
        return g1 != null && g2 != null && g1.name.equalsIgnoreCase(g2.name);
    }
}
