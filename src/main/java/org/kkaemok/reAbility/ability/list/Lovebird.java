package org.kkaemok.reAbility.ability.list;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.kkaemok.reAbility.ReAbility;
import org.kkaemok.reAbility.ability.AbilityBase;
import org.kkaemok.reAbility.ability.AbilityGrade;
import org.kkaemok.reAbility.guild.GuildData;
import org.kkaemok.reAbility.guild.GuildManager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class Lovebird extends AbilityBase {
    private final ReAbility plugin;
    private final GuildManager guildManager;
    private final Map<UUID, BukkitTask> tasks = new HashMap<>();

    public Lovebird(ReAbility plugin, GuildManager guildManager) {
        this.plugin = plugin;
        this.guildManager = guildManager;
    }

    @Override
    public String getName() { return "LOVEBIRD"; }

    @Override
    public String getDisplayName() { return "Lovebird"; }

    @Override
    public AbilityGrade getGrade() { return AbilityGrade.C; }

    @Override
    public String[] getDescription() {
        return new String[]{
                "When near a guild member, both players receive buffs.",
                "Applies Regeneration II and Strength I.",
                "Effect applies to at most two players including yourself."
        };
    }

    @Override
    public void onActivate(Player player) {
        BukkitTask existing = tasks.remove(player.getUniqueId());
        if (existing != null) {
            existing.cancel();
        }

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || !isHasAbility(player)) {
                    BukkitTask current = tasks.get(player.getUniqueId());
                    if (current != null && current.getTaskId() == this.getTaskId()) {
                        tasks.remove(player.getUniqueId());
                    }
                    this.cancel();
                    return;
                }

                GuildData myGuild = guildManager.getGuildByMember(player.getUniqueId());
                if (myGuild == null) return;

                List<Entity> nearby = player.getNearbyEntities(5, 5, 5);
                Player partner = null;

                for (Entity entity : nearby) {
                    if (entity instanceof Player target && target != player) {
                        GuildData targetGuild = guildManager.getGuildByMember(target.getUniqueId());
                        if (targetGuild != null && targetGuild.name.equals(myGuild.name)) {
                            partner = target;
                            break;
                        }
                    }
                }

                if (partner != null) {
                    applyLoveBuff(player);
                    applyLoveBuff(partner);
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
        tasks.put(player.getUniqueId(), task);
    }

    @Override
    public void onDeactivate(Player player) {
        BukkitTask task = tasks.remove(player.getUniqueId());
        if (task != null) {
            task.cancel();
        }
        player.removePotionEffect(PotionEffectType.REGENERATION);
        player.removePotionEffect(PotionEffectType.STRENGTH);
    }

    private void applyLoveBuff(Player target) {
        target.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 50, 1, false, false, true));
        target.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 50, 0, false, false, true));
    }

    private boolean isHasAbility(Player player) {
        return getName().equals(plugin.getAbilityManager().getPlayerData(player.getUniqueId()).getAbilityName());
    }
}
