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
    private final GuildManager guildManager; // 길드 매니저 참조 추가
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
                "§7길드원과 5블록 내에 있을 시 서로에게 버프를 부여합니다.",
                "§d[재생 II, 힘 I] §8(자신 포함 최대 2명 적용)"
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

                // 1. 본인의 길드 확인 (GuildManager 활용)
                GuildData myGuild = guildManager.getGuildByMember(player.getUniqueId());
                if (myGuild == null) return; // 소속 길드 없으면 작동 안 함

                // 2. 주변 5블록 내 길드원 탐색
                List<Entity> nearby = player.getNearbyEntities(5, 5, 5);
                Player partner = null;

                for (Entity entity : nearby) {
                    if (entity instanceof Player target && target != player) {
                        // 3. GuildManager로 같은 길드인지 판별
                        GuildData targetGuild = guildManager.getGuildByMember(target.getUniqueId());
                        if (targetGuild != null && targetGuild.name.equals(myGuild.name)) {
                            partner = target;
                            break; // 기획안: 자신 포함 최대 2명이므로 파트너 1명 찾으면 끝
                        }
                    }
                }

                // 4. 길드원을 찾았다면 둘 다 버프 부여
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
        // 재생 II (Amplifier 1), 힘 I (Amplifier 0)
        target.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 50, 1, false, false, true));
        target.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 50, 0, false, false, true));
    }
}