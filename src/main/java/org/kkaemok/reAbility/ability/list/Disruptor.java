package org.kkaemok.reAbility.ability.list;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.kkaemok.reAbility.ReAbility;
import org.kkaemok.reAbility.ability.AbilityBase;
import org.kkaemok.reAbility.ability.AbilityGrade;
import org.kkaemok.reAbility.guild.GuildData;
import org.kkaemok.reAbility.guild.GuildManager;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

public class Disruptor extends AbilityBase {
    private static final double FAIL_CHANCE = 0.35;
    private static final double RANGE = 20.0;
    private static final String ABILITY_KEY = "DISRUPTOR";
    private static final Random RANDOM = new Random();

    private final ReAbility plugin;

    public Disruptor(ReAbility plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return ABILITY_KEY;
    }

    @Override
    public String getDisplayName() {
        return "방해꾼";
    }

    @Override
    public AbilityGrade getGrade() {
        return AbilityGrade.D;
    }

    @Override
    public String[] getDescription() {
        return new String[]{
                "금을 들고 있으면 20칸 내 상대의 스킬이 35% 확률로 실패.",
                "스킬 실패를 유도하면 힘 2를 5초 획득."
        };
    }

    public static boolean tryFailSkill(ReAbility plugin, Player caster) {
        if (caster == null || plugin == null) return false;

        List<Player> candidates = new ArrayList<>();
        double rangeSquared = RANGE * RANGE;

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.equals(caster)) continue;
            if (!player.getWorld().equals(caster.getWorld())) continue;
            if (player.getLocation().distanceSquared(caster.getLocation()) > rangeSquared) continue;
            if (!isDisruptor(plugin, player)) continue;
            if (!isHoldingGold(player)) continue;
            if (isSameGuild(plugin.getGuildManager(), player, caster)) continue;
            candidates.add(player);
        }

        if (candidates.isEmpty()) return false;
        if (RANDOM.nextDouble() >= FAIL_CHANCE) return false;

        Player owner = candidates.stream()
                .min(Comparator.comparingDouble(p -> p.getLocation().distanceSquared(caster.getLocation())))
                .orElse(candidates.get(0));

        owner.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 100, 1, false, false));
        owner.sendMessage(Component.text("[방해꾼] 상대 스킬을 방해했습니다! 힘 2 (5초)", NamedTextColor.GOLD));
        caster.sendMessage(Component.text("[방해꾼] 스킬이 방해되어 실패했습니다.", NamedTextColor.RED));
        return true;
    }

    private static boolean isDisruptor(ReAbility plugin, Player player) {
        String ability = plugin.getAbilityManager().getPlayerData(player.getUniqueId()).getAbilityName();
        return ABILITY_KEY.equals(ability);
    }

    private static boolean isHoldingGold(Player player) {
        Material main = player.getInventory().getItemInMainHand().getType();
        Material off = player.getInventory().getItemInOffHand().getType();
        return main == Material.GOLD_INGOT || off == Material.GOLD_INGOT;
    }

    private static boolean isSameGuild(GuildManager guildManager, Player p1, Player p2) {
        if (p1.equals(p2)) return true;
        GuildData g1 = guildManager.getGuildByMember(p1.getUniqueId());
        GuildData g2 = guildManager.getGuildByMember(p2.getUniqueId());
        return g1 != null && g2 != null && g1.name.equalsIgnoreCase(g2.name);
    }

    @SuppressWarnings("unused")
    private boolean isHasAbility(Player player) {
        String ability = plugin.getAbilityManager().getPlayerData(player.getUniqueId()).getAbilityName();
        return ABILITY_KEY.equals(ability);
    }
}
