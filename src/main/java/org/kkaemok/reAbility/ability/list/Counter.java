package org.kkaemok.reAbility.ability.list;

import org.bukkit.Material;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
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
import java.util.Map;
import java.util.UUID;

public class Counter extends AbilityBase {
    private final ReAbility plugin;
    private final GuildManager guildManager;
    private final Map<UUID, BukkitTask> tasks = new HashMap<>();
    private final Map<String, Long> closeHitCooldown = new HashMap<>();

    public Counter(ReAbility plugin, GuildManager guildManager) {
        this.plugin = plugin;
        this.guildManager = guildManager;
    }

    @Override public String getName() { return "COUNTER"; }
    @Override public String getDisplayName() { return "카운터"; }
    @Override public AbilityGrade getGrade() { return AbilityGrade.B; }

    @Override
    public String[] getDescription() {
        return new String[]{
                "엔더 크리스탈 피해 50% 감소, 철퇴 피해 60 상한.",
                "주변 2칸 적에게 구속 2 + 데미지 5 (쿨타임 0.5초)"
        };
    }

    @Override
    public void onActivate(Player player) {
        startTask(player);
    }

    @Override
    public void onDeactivate(Player player) {
        BukkitTask task = tasks.remove(player.getUniqueId());
        if (task != null) task.cancel();
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!isHasAbility(player)) return;

        if (event.getDamager() instanceof EnderCrystal) {
            event.setDamage(event.getDamage() * 0.5);
        }

        if (event.getDamager() instanceof Player damager) {
            if (damager.getInventory().getItemInMainHand().getType() == Material.MACE) {
                event.setDamage(Math.min(event.getDamage(), 60.0));
            }
        }
    }

    private void startTask(Player player) {
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || !isHasAbility(player)) {
                    BukkitTask current = tasks.remove(player.getUniqueId());
                    if (current != null) current.cancel();
                    return;
                }

                long now = System.currentTimeMillis();
                for (Entity entity : player.getNearbyEntities(2, 2, 2)) {
                    if (entity instanceof Player target && !target.equals(player) && !isSameGuild(player, target)) {
                        String key = player.getUniqueId() + ":" + target.getUniqueId();
                        long next = closeHitCooldown.getOrDefault(key, 0L);
                        if (now < next) continue;

                        closeHitCooldown.put(key, now + 500L);
                        target.damage(5.0, player);
                        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20, 1, false, false));
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 10L);
        tasks.put(player.getUniqueId(), task);
    }

    private boolean isSameGuild(Player p1, Player p2) {
        GuildData g1 = guildManager.getGuildByMember(p1.getUniqueId());
        GuildData g2 = guildManager.getGuildByMember(p2.getUniqueId());
        return g1 != null && g2 != null && g1.name.equalsIgnoreCase(g2.name);
    }

    private boolean isHasAbility(Player player) {
        return getName().equals(plugin.getAbilityManager().getPlayerData(player.getUniqueId()).getAbilityName());
    }
}
