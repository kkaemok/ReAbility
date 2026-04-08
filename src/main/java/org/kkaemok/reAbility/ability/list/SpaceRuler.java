package org.kkaemok.reAbility.ability.list;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.kkaemok.reAbility.ReAbility;
import org.kkaemok.reAbility.ability.AbilityBase;
import org.kkaemok.reAbility.ability.AbilityGrade;
import org.kkaemok.reAbility.ability.SkillCost;
import org.kkaemok.reAbility.system.SpectatorLockListener;
import org.kkaemok.reAbility.utils.SkillParticles;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class SpaceRuler extends AbilityBase {
    private final ReAbility plugin;
    private final Map<UUID, Long> skill1Cooldown = new HashMap<>();
    private final Map<UUID, Long> skill2Cooldown = new HashMap<>();
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
                "RTP 쿨타임 없음, 범위 ±30000.",
                "RTP 직후 30초 동안 유령(관전자) 상태.",
                "스킬 {공간 이동}: 네더라이트 주괴 1개 우클릭",
                "랜덤 플레이어 주변 1500칸 이내로 이동 + 스피드5 10분 (쿨타임 3시간)",
                "자신/길드원이 걸릴 수 있음.",
                "스킬 {공간 절단}: 금블럭 25개 우클릭",
                "45초간 스피드2/저항2/재생2/힘3, 종료 시 체력 전부 회복 (쿨타임 5분)"
        };
    }

    @Override
    public void onDeactivate(Player player) {
        if (player.getGameMode() == GameMode.SPECTATOR) {
            player.setGameMode(GameMode.SURVIVAL);
        }
        player.removeScoreboardTag(SpectatorLockListener.SPECTATOR_LOCK_TAG);
        player.removePotionEffect(PotionEffectType.SPEED);
        player.removePotionEffect(PotionEffectType.RESISTANCE);
        player.removePotionEffect(PotionEffectType.REGENERATION);
        player.removePotionEffect(PotionEffectType.STRENGTH);
    }

    @EventHandler
    public void onRightClick(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!isHasAbility(player)) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (shouldIgnoreSneakRightClickBlock(event)) return;

        ItemStack item = player.getInventory().getItemInMainHand();
        long now = System.currentTimeMillis();

        SkillCost moveCost = plugin.getAbilityConfigManager()
                .getSkillCost(getName(), "move", Material.NETHERITE_INGOT, 1);
        if (item.getType() == moveCost.getItem()) {
            if (Disruptor.tryFailSkill(plugin, player)) return;
            if (now < skill1Cooldown.getOrDefault(player.getUniqueId(), 0L)) {
                player.sendMessage(Component.text("공간 이동 쿨타임입니다.", NamedTextColor.RED));
                return;
            }
            if (!moveCost.consumeFromInventory(player)) return;
            long cooldownMs = plugin.getAbilityConfigManager()
                    .getLong(getName(), "skills.move.cooldown-ms", 10800000L);
            int range = plugin.getAbilityConfigManager()
                    .getInt(getName(), "skills.move.range", 1500);
            int speedTicks = plugin.getAbilityConfigManager()
                    .getInt(getName(), "skills.move.speed-ticks", 12000);
            int speedAmp = plugin.getAbilityConfigManager()
                    .getInt(getName(), "skills.move.speed-amplifier", 4);
            skill1Cooldown.put(player.getUniqueId(), now + cooldownMs);

            List<Player> targets = new ArrayList<>(Bukkit.getOnlinePlayers());
            if (targets.isEmpty()) {
                player.sendMessage(Component.text("이동할 대상이 없습니다.", NamedTextColor.RED));
                return;
            }

            Player target = targets.get(random.nextInt(targets.size()));
            int offsetX = random.nextInt((range * 2) + 1) - range;
            int offsetZ = random.nextInt((range * 2) + 1) - range;
            Location tLoc = target.getLocation().add(offsetX, 0, offsetZ);
            tLoc.setY(tLoc.getWorld().getHighestBlockYAt(tLoc) + 1);

            SkillParticles.spaceMoveSource(player.getLocation());
            player.teleport(tLoc);
            SkillParticles.spaceMoveDest(player.getLocation());
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, speedTicks, speedAmp));
            broadcastSkill(player, "{공간 이동}");
            return;
        }

        SkillCost cutCost = plugin.getAbilityConfigManager()
                .getSkillCost(getName(), "cut", Material.GOLD_BLOCK, 25);
        if (item.getType() == cutCost.getItem()) {
            if (Disruptor.tryFailSkill(plugin, player)) return;
            if (now < skill2Cooldown.getOrDefault(player.getUniqueId(), 0L)) {
                player.sendMessage(Component.text("공간 절단 쿨타임입니다.", NamedTextColor.RED));
                return;
            }
            if (!cutCost.consumeFromInventory(player)) return;
            long cooldownMs = plugin.getAbilityConfigManager()
                    .getLong(getName(), "skills.cut.cooldown-ms", 300000L);
            int buffTicks = plugin.getAbilityConfigManager()
                    .getInt(getName(), "skills.cut.buff-ticks", 900);
            int speedAmp = plugin.getAbilityConfigManager()
                    .getInt(getName(), "skills.cut.speed-amplifier", 1);
            int resistanceAmp = plugin.getAbilityConfigManager()
                    .getInt(getName(), "skills.cut.resistance-amplifier", 1);
            int regenAmp = plugin.getAbilityConfigManager()
                    .getInt(getName(), "skills.cut.regen-amplifier", 1);
            int strengthAmp = plugin.getAbilityConfigManager()
                    .getInt(getName(), "skills.cut.strength-amplifier", 2);
            int healDelayTicks = plugin.getAbilityConfigManager()
                    .getInt(getName(), "skills.cut.heal-delay-ticks", 900);
            skill2Cooldown.put(player.getUniqueId(), now + cooldownMs);

            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, buffTicks, speedAmp));
            player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, buffTicks, resistanceAmp));
            player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, buffTicks, regenAmp));
            player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, buffTicks, strengthAmp));

            SkillParticles.spaceCut(player);
            broadcastSkill(player, "{공간 절단}");
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (!player.isOnline()) return;
                    if (player.getAttribute(Attribute.MAX_HEALTH) == null) return;
                    double maxHealth = player.getAttribute(Attribute.MAX_HEALTH).getValue();
                    player.setHealth(maxHealth);
                }
            }.runTaskLater(plugin, healDelayTicks);
        }
    }

    private void broadcastSkill(Player player, String skillName) {
        Bukkit.broadcast(Component.text("==========", NamedTextColor.DARK_PURPLE));
        Bukkit.broadcast(Component.text("[S] 공간 지배자 " + player.getName() + "이(가) " + skillName + "을 사용했습니다!",
                NamedTextColor.LIGHT_PURPLE));
        Bukkit.broadcast(Component.text("==========", NamedTextColor.DARK_PURPLE));
    }

    private boolean isHasAbility(Player player) {
        return getName().equals(plugin.getAbilityManager().getPlayerData(player.getUniqueId()).getAbilityName());
    }
}
