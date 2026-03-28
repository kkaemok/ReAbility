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
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.kkaemok.reAbility.ReAbility;
import org.kkaemok.reAbility.ability.AbilityBase;
import org.kkaemok.reAbility.ability.AbilityGrade;
import org.kkaemok.reAbility.ability.SkillCost;
import org.kkaemok.reAbility.guild.GuildData;
import org.kkaemok.reAbility.guild.GuildManager;
import org.kkaemok.reAbility.utils.AbilityTags;
import org.kkaemok.reAbility.utils.JokerNameRegistry;
import org.kkaemok.reAbility.utils.SkillParticles;

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
    private final Map<UUID, Long> noTotemUntil = new ConcurrentHashMap<>();
    private final Random random = new Random();
    private static final long NO_TOTEM_DURATION_MS = 5000L;

    public Joker(ReAbility plugin) {
        this.plugin = plugin;
        this.guildManager = plugin.getGuildManager();
        startIdentityTask();
        startIllusionBuffTask();
    }

    @Override public String getName() { return "JOKER"; }
    @Override public String getDisplayName() { return "조커"; }
    @Override public AbilityGrade getGrade() { return AbilityGrade.S; }

    @Override
    public String[] getDescription() {
        return new String[]{
                "자신의 닉네임이 1시간마다 바뀝니다.",
                "스킬 {스파이}: 에메랄드 50개 웅크림, 3분간 다른 길드 채팅/귓속말 감청",
                "(쿨타임 15분)",
                "스킬 {환상}: 네더라이트 파편 2개 웅크림, 자신/길드 제외 전원 나약함 100(거품 없음) 무한",
                "에메랄드 1개 들고 웅크리면 해제 (쿨타임 15분)",
                "환상이 걸린 적 50칸 이내: 스피드 3, 힘 2",
                "스킬 {죽음의 트릭}: 다이아몬드 블럭 20개 웅크림",
                "40% 자신 500 피해 / 60% 주변 500칸 내 랜덤 2명 토템 사용 불가 + 데미지 100 (쿨타임 1시간)"
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

    private void startIllusionBuffTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player joker : Bukkit.getOnlinePlayers()) {
                    if (!isHasAbility(joker)) continue;
                    if (!hasNearbyIllusionTarget(joker)) continue;

                    joker.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 40, 2, false, false));
                    joker.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 40, 1, false, false));
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private boolean hasNearbyIllusionTarget(Player joker) {
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (target.equals(joker)) continue;
            if (!target.getWorld().equals(joker.getWorld())) continue;
            if (isSameGuild(joker, target)) continue;
            if (!target.getScoreboardTags().contains(AbilityTags.JOKER_ILLUSION)) continue;
            if (joker.getLocation().distanceSquared(target.getLocation()) <= 2500) {
                return true;
            }
        }
        return false;
    }

    private void changeIdentity(Player joker) {
        String fakeName = generateUniqueFakeName();
        currentFakeNames.put(joker.getUniqueId(), fakeName);
        JokerNameRegistry.setFakeName(joker.getUniqueId(), fakeName);

        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer.equals(joker)) continue;
            updateJokerNametag(joker, viewer, fakeName);
        }
        joker.sendMessage(Component.text("[!] 신분 위장 완료. 현재 닉네임: " + fakeName, NamedTextColor.LIGHT_PURPLE));
    }

    private String generateUniqueFakeName() {
        String fakeName;
        do {
            fakeName = "Player" + (random.nextInt(9000) + 1000);
        } while (Bukkit.getPlayerExact(fakeName) != null || JokerNameRegistry.isFakeNameTaken(fakeName));
        return fakeName;
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
        JokerNameRegistry.clearFakeName(joker.getUniqueId());
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer.equals(joker)) continue;
            updateJokerNametag(joker, viewer, joker.getName());
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player viewer = event.getPlayer();
        for (Map.Entry<UUID, String> entry : currentFakeNames.entrySet()) {
            Player joker = Bukkit.getPlayer(entry.getKey());
            if (joker == null || !joker.isOnline()) continue;
            if (joker.equals(viewer)) continue;
            updateJokerNametag(joker, viewer, entry.getValue());
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

    @EventHandler
    public void onResurrect(EntityResurrectEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        long now = System.currentTimeMillis();
        Long until = noTotemUntil.get(player.getUniqueId());
        if (until == null) return;
        if (now > until) {
            noTotemUntil.remove(player.getUniqueId());
            return;
        }
        event.setCancelled(true);
    }

    @Override
    public void onSneakSkill(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        long now = System.currentTimeMillis();

        SkillCost spyCost = plugin.getAbilityConfigManager()
                .getSkillCost(getName(), "spy", Material.EMERALD, 50);
        if (item.getType() == spyCost.getItem()) {
            if (now < spyCooldown.getOrDefault(player.getUniqueId(), 0L)) {
                player.sendMessage(Component.text("스파이 쿨타임입니다.", NamedTextColor.RED));
                return;
            }
            if (!spyCost.matchesHand(item)) return;
            if (!spyCost.consumeFromHand(player)) return;
            long durationMs = plugin.getAbilityConfigManager()
                    .getLong(getName(), "skills.spy.duration-ms", 180000L);
            long cooldownMs = plugin.getAbilityConfigManager()
                    .getLong(getName(), "skills.spy.cooldown-ms", 900000L);
            spyEndTime.put(player.getUniqueId(), now + durationMs);
            spyCooldown.put(player.getUniqueId(), now + cooldownMs);
            player.sendMessage(Component.text("[!] 스킬 {스파이}가 발동되었습니다.", NamedTextColor.GRAY));
            SkillParticles.jokerSpy(player);
            return;
        }

        SkillCost illusionCost = plugin.getAbilityConfigManager()
                .getSkillCost(getName(), "illusion", Material.NETHERITE_SCRAP, 2);
        if (item.getType() == illusionCost.getItem()) {
            if (now < illusionCooldown.getOrDefault(player.getUniqueId(), 0L)) {
                player.sendMessage(Component.text("환상 쿨타임입니다.", NamedTextColor.RED));
                return;
            }
            if (!illusionCost.matchesHand(item)) return;
            if (!illusionCost.consumeFromHand(player)) return;
            long cooldownMs = plugin.getAbilityConfigManager()
                    .getLong(getName(), "skills.illusion.cooldown-ms", 900000L);
            illusionCooldown.put(player.getUniqueId(), now + cooldownMs);
            broadcastSkill(player, "{환상}");
            SkillParticles.jokerIllusion(player);
            for (Player target : Bukkit.getOnlinePlayers()) {
                if (!target.equals(player) && !isSameGuild(player, target)) {
                    target.addPotionEffect(new PotionEffect(
                            PotionEffectType.WEAKNESS, PotionEffect.INFINITE_DURATION, 99, false, false));
                    target.addScoreboardTag(AbilityTags.JOKER_ILLUSION);
                    SkillParticles.jokerIllusionTarget(target);
                }
            }
            return;
        }

        SkillCost trickCost = plugin.getAbilityConfigManager()
                .getSkillCost(getName(), "trick", Material.DIAMOND_BLOCK, 20);
        if (item.getType() == trickCost.getItem()) {
            if (now < trickCooldown.getOrDefault(player.getUniqueId(), 0L)) {
                player.sendMessage(Component.text("죽음의 트릭 쿨타임입니다.", NamedTextColor.RED));
                return;
            }
            if (!trickCost.consumeFromInventory(player)) return;
            long cooldownMs = plugin.getAbilityConfigManager()
                    .getLong(getName(), "skills.trick.cooldown-ms", 3600000L);
            trickCooldown.put(player.getUniqueId(), now + cooldownMs);
            broadcastSkill(player, "{죽음의 트릭}");
            SkillParticles.jokerDeathTrick(player);

            if (random.nextInt(100) < 40) {
                player.damage(500.0, player);
                return;
            }

            List<Player> targets = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.equals(player)) continue;
                if (!p.getWorld().equals(player.getWorld())) continue;
                if (isSameGuild(player, p)) continue;
                if (p.getLocation().distanceSquared(player.getLocation()) > 250000) continue;
                targets.add(p);
            }

            Collections.shuffle(targets);
            int count = Math.min(2, targets.size());
            for (int i = 0; i < count; i++) {
                Player victim = targets.get(i);
                noTotemUntil.put(victim.getUniqueId(), now + NO_TOTEM_DURATION_MS);
                victim.damage(100.0, player);
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
