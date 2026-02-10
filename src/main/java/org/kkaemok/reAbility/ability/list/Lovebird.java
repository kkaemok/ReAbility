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

import java.util.List;

public class Lovebird extends AbilityBase {
    private final ReAbility plugin;
    private final GuildManager guildManager;
    private BukkitTask task;

    public Lovebird(ReAbility plugin, GuildManager guildManager) {
        this.plugin = plugin;
        this.guildManager = guildManager;
    }

    @Override
    public String getName() { return "LOVEBIRD"; }

    @Override
    public String getDisplayName() { return "사랑꾼"; }

    @Override
    public AbilityGrade getGrade() { return AbilityGrade.C; }

    @Override
    public String[] getDescription() {
        return new String[]{
                "길드원과 함께 붙어있을 시",
                "길드원과 자신에게 재생 2, 힘 1 버프 제공",
                "(자신 포함 최대 2명만 적용)"
        };
    }

    @Override
    public void onActivate(Player player) {
        task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
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
    }

    @Override
    public void onDeactivate(Player player) {
        if (task != null) {
            task.cancel();
            task = null;
        }
        player.removePotionEffect(PotionEffectType.REGENERATION);
        player.removePotionEffect(PotionEffectType.STRENGTH);
    }

    private void applyLoveBuff(Player target) {
        target.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 50, 1, false, false, true));
        target.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 50, 0, false, false, true));
    }
}
