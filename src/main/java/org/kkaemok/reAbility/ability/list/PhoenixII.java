package org.kkaemok.reAbility.ability.list;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.kkaemok.reAbility.ReAbility;
import org.kkaemok.reAbility.ability.AbilityBase;
import org.kkaemok.reAbility.ability.AbilityGrade;
import org.kkaemok.reAbility.ability.SkillCost;
import org.kkaemok.reAbility.guild.GuildData;
import org.kkaemok.reAbility.guild.GuildManager;
import org.kkaemok.reAbility.utils.SkillParticles;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PhoenixII extends AbilityBase {
    private final ReAbility plugin;
    private final GuildManager guildManager;
    private final Map<UUID, Long> deathImmunityCooldown = new HashMap<>();
    private final Map<UUID, Long> invincibleUntil = new HashMap<>();
    private final Map<UUID, Long> totemCooldown = new HashMap<>();
    private final Map<UUID, Long> evadeCooldown = new HashMap<>();
    private final Map<UUID, Long> skillInvincibleUntil = new HashMap<>();
    private final Map<UUID, Long> damageCapUntil = new HashMap<>();
    private final Map<UUID, Long> damageWindowEnd = new HashMap<>();
    private final Map<UUID, Double> damageWindowTotal = new HashMap<>();

    public PhoenixII(ReAbility plugin) {
        this.plugin = plugin;
        this.guildManager = plugin.getGuildManager();
    }

    @Override public String getName() { return "PHOENIX_II"; }
    @Override public String getDisplayName() { return "불사조 II"; }
    @Override public AbilityGrade getGrade() { return AbilityGrade.S; }

    @Override
    public String[] getDescription() {
        return new String[]{
                "패시브 {죽음 면제}: HP 3칸 이하 시 1분 무적 (쿨타임 3시간)",
                "패시브 {불사의 토템}: 웅크리면 토템 3개 획득 (쿨타임 1분)",
                "스킬 {죽음 회피}: 다이아몬드 블럭 10개 우클릭",
                "30초간 길드원/자신 무적, 15칸 길드원 3분간 저항3/재생2",
                "3초 이내 70 이상 피해 제한 (쿨타임 15분)"
        };
    }

    @Override
    public void onDeactivate(Player player) {
        UUID id = player.getUniqueId();
        skillInvincibleUntil.remove(id);
        damageCapUntil.remove(id);
        damageWindowEnd.remove(id);
        damageWindowTotal.remove(id);
        invincibleUntil.remove(id);
        player.removePotionEffect(PotionEffectType.RESISTANCE);
        player.removePotionEffect(PotionEffectType.REGENERATION);
    }

    @Override
    public void onSneakSkill(Player player) {
        long now = System.currentTimeMillis();
        if (now < totemCooldown.getOrDefault(player.getUniqueId(), 0L)) return;

        player.getInventory().addItem(new ItemStack(Material.TOTEM_OF_UNDYING, 3));
        player.sendMessage(Component.text("[!] 불사의 토템 3개를 획득했습니다.", NamedTextColor.GOLD));
        totemCooldown.put(player.getUniqueId(), now + 60000);
    }

    @EventHandler
    public void onRightClick(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!isHasAbility(player)) return;
        if (!event.getAction().isRightClick()) return;
        if (shouldIgnoreSneakRightClickBlock(event)) return;
        SkillCost cost = plugin.getAbilityConfigManager()
                .getSkillCost(getName(), "death_evade", Material.DIAMOND_BLOCK, 10);
        if (player.getInventory().getItemInMainHand().getType() != cost.getItem()) return;
        if (Disruptor.tryFailSkill(plugin, player)) return;

        long now = System.currentTimeMillis();
        if (now < evadeCooldown.getOrDefault(player.getUniqueId(), 0L)) {
            player.sendMessage(Component.text("죽음 회피 쿨타임입니다.", NamedTextColor.RED));
            return;
        }

        if (!cost.consumeFromInventory(player)) return;
        long cooldownMs = plugin.getAbilityConfigManager()
                .getLong(getName(), "skills.death_evade.cooldown-ms", 900000L);
        evadeCooldown.put(player.getUniqueId(), now + cooldownMs);

        for (Player target : Bukkit.getOnlinePlayers()) {
            if (!isSameGuild(player, target)) continue;
            skillInvincibleUntil.put(target.getUniqueId(), now + 30000);

            if (target.getWorld().equals(player.getWorld())
                    && target.getLocation().distanceSquared(player.getLocation()) <= 225) {
                damageCapUntil.put(target.getUniqueId(), now + 180000);
                damageWindowEnd.put(target.getUniqueId(), now + 3000);
                damageWindowTotal.put(target.getUniqueId(), 0.0);

                target.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 3600, 2, false, false));
                target.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 3600, 1, false, false));
                SkillParticles.phoenixIITarget(target);
            }
        }

        skillInvincibleUntil.put(player.getUniqueId(), now + 30000);
        damageCapUntil.put(player.getUniqueId(), now + 180000);
        damageWindowEnd.put(player.getUniqueId(), now + 3000);
        damageWindowTotal.put(player.getUniqueId(), 0.0);

        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 3600, 2, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 3600, 1, false, false));
        SkillParticles.phoenixIIEvade(player);
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        UUID id = player.getUniqueId();
        if (!isHasAbility(player)
                && !skillInvincibleUntil.containsKey(id)
                && !damageCapUntil.containsKey(id)
                && !invincibleUntil.containsKey(id)) {
            return;
        }

        long now = System.currentTimeMillis();

        Long invUntil = skillInvincibleUntil.get(id);
        if (invUntil != null) {
            if (now <= invUntil) {
                event.setCancelled(true);
                return;
            }
            skillInvincibleUntil.remove(id);
        }

        Long passiveInv = invincibleUntil.get(id);
        if (passiveInv != null) {
            if (now <= passiveInv) {
                event.setCancelled(true);
                return;
            }
            invincibleUntil.remove(id);
        }

        if (isHasAbility(player)) {
            if (player.getHealth() - event.getFinalDamage() <= 6.0) {
                if (now >= deathImmunityCooldown.getOrDefault(id, 0L)) {
                    event.setCancelled(true);
                    if (player.getAttribute(Attribute.MAX_HEALTH) != null) {
                        player.setHealth(Math.min(player.getAttribute(Attribute.MAX_HEALTH).getValue(), 6.0));
                    } else {
                        player.setHealth(Math.min(player.getHealth(), 6.0));
                    }
                    invincibleUntil.put(id, now + 60000);
                    deathImmunityCooldown.put(id, now + 10800000);
                    player.sendMessage(Component.text("[!] 죽음 면제 발동! 1분간 무적입니다.", NamedTextColor.GOLD));
                }
                return;
            }
        }

        if (!damageCapUntil.containsKey(id)) return;
        long capUntil = damageCapUntil.get(id);
        if (now > capUntil) {
            damageCapUntil.remove(id);
            damageWindowEnd.remove(id);
            damageWindowTotal.remove(id);
            return;
        }

        long windowEnd = damageWindowEnd.getOrDefault(id, 0L);
        if (now > windowEnd) {
            damageWindowEnd.put(id, now + 3000);
            damageWindowTotal.put(id, 0.0);
        }

        double total = damageWindowTotal.getOrDefault(id, 0.0);
        double incoming = event.getFinalDamage();
        if (total + incoming > 70.0) {
            double allowed = Math.max(0.0, 70.0 - total);
            event.setDamage(allowed);
            incoming = allowed;
        }
        damageWindowTotal.put(id, total + incoming);
    }

    private boolean isSameGuild(Player p1, Player p2) {
        if (p1.equals(p2)) return true;
        GuildData g1 = guildManager.getGuildByMember(p1.getUniqueId());
        GuildData g2 = guildManager.getGuildByMember(p2.getUniqueId());
        return g1 != null && g2 != null && g1.name.equalsIgnoreCase(g2.name);
    }

    private boolean isHasAbility(Player player) {
        return getName().equals(plugin.getAbilityManager().getPlayerData(player.getUniqueId()).getAbilityName());
    }
}
