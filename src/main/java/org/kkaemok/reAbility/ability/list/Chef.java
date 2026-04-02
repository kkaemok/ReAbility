package org.kkaemok.reAbility.ability.list;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
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

public class Chef extends AbilityBase {
    private final ReAbility plugin;
    private final GuildManager guildManager;
    private final Map<UUID, Long> overeatUntil = new HashMap<>();
    private final Map<UUID, Long> overeatCooldown = new HashMap<>();
    private final Map<UUID, Long> explosionImmuneUntil = new HashMap<>();
    private final Map<UUID, Long> harmfulImmuneUntil = new HashMap<>();
    private final Map<UUID, Long> healthBonusUntil = new HashMap<>();

    public Chef(ReAbility plugin) {
        this.plugin = plugin;
        this.guildManager = plugin.getGuildManager();
        startMaintenanceTask();
    }

    @Override public String getName() { return "CHEF"; }
    @Override public String getDisplayName() { return "셰프"; }
    @Override public AbilityGrade getGrade() { return AbilityGrade.A; }

    @Override
    public String[] getDescription() {
        return new String[]{
                "먹는 음식의 버프가 길드원과 공유됨.",
                "황금사과: 힘2/저항2 30초",
                "스테이크: 해로운 효과 면역 1분",
                "치킨: 재생5 10초",
                "양고기: 체력 3줄 증가 2분",
                "돼지고기: 폭발 면역 5분",
                "마황: 힘3/저항2/체력 3줄/폭발 면역 2분",
                "스킬 {대식가}: 다이아 64개 웅크림, 10분간 포만 상태에서도 섭취 가능"
        };
    }

    @Override
    public void onDeactivate(Player player) {
        UUID id = player.getUniqueId();
        overeatUntil.remove(id);
        overeatCooldown.remove(id);
        explosionImmuneUntil.remove(id);
        harmfulImmuneUntil.remove(id);
        healthBonusUntil.remove(id);
        removeHealthBonus(player);
    }

    @Override
    public void onSneakSkill(Player player) {
        long now = System.currentTimeMillis();
        boolean eaterSynergy = hasEaterInGuild(player);
        if (!eaterSynergy && now < overeatCooldown.getOrDefault(player.getUniqueId(), 0L)) {
            player.sendMessage(Component.text("대식가 쿨타임입니다.", NamedTextColor.RED));
            return;
        }
        SkillCost cost = plugin.getAbilityConfigManager()
                .getSkillCost(getName(), "overeat", Material.DIAMOND, 64);
        if (player.getInventory().getItemInMainHand().getType() != cost.getItem()) return;

        if (!cost.consumeFromInventory(player)) return;
        long cooldownMs = plugin.getAbilityConfigManager()
                .getLong(getName(), "skills.overeat.cooldown-ms", 60000L);
        long durationMs = plugin.getAbilityConfigManager()
                .getLong(getName(), "skills.overeat.duration-ms", 60000L);
        if (eaterSynergy) {
            overeatCooldown.remove(player.getUniqueId());
        } else {
            overeatCooldown.put(player.getUniqueId(), now + cooldownMs);
        }
        overeatUntil.put(player.getUniqueId(), now + durationMs);
        player.sendMessage(Component.text("[!] 대식가 발동! 1분간 포만 상태에서도 섭취 가능합니다.", NamedTextColor.YELLOW));
        SkillParticles.chefOvereat(player);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!event.getAction().isRightClick()) return;
        if (shouldIgnoreSneakRightClickBlock(event)) return;

        if (isHasAbility(player) && player.getFoodLevel() >= 20 && canOvereat(player)) {
            if (event.getItem() != null && event.getItem().getType().isEdible()) {
                player.setFoodLevel(19);
            }
        }
    }

    @EventHandler
    public void onConsume(PlayerItemConsumeEvent event) {
        Player chef = event.getPlayer();
        if (isHasAbility(chef)) {
            Material type = event.getItem().getType();
            applyFoodBuffs(chef, type);
        }
    }

    @EventHandler
    public void onExplosionDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.getCause() != EntityDamageEvent.DamageCause.BLOCK_EXPLOSION
                && event.getCause() != EntityDamageEvent.DamageCause.ENTITY_EXPLOSION) return;

        long now = System.currentTimeMillis();
        Long until = explosionImmuneUntil.get(player.getUniqueId());
        if (until == null || now > until) {
            if (until != null) explosionImmuneUntil.remove(player.getUniqueId());
            return;
        }
        event.setCancelled(true);
    }

    private void applyFoodBuffs(Player chef, Material type) {
        long now = System.currentTimeMillis();
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (!isSameGuild(chef, target)) continue;

            switch (type) {
                case GOLDEN_APPLE -> {
                    target.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 600, 1, false, false));
                    target.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 600, 1, false, false));
                }
                case COOKED_BEEF -> {
                    harmfulImmuneUntil.put(target.getUniqueId(), now + 60000);
                    clearHarmfulEffects(target);
                }
                case COOKED_CHICKEN -> target.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 200, 4, false, false));
                case COOKED_MUTTON -> {
                    healthBonusUntil.put(target.getUniqueId(), now + 120000);
                    applyHealthBonus(target, 40.0);
                }
                case COOKED_PORKCHOP -> explosionImmuneUntil.put(target.getUniqueId(), now + 120000);
                case ENCHANTED_GOLDEN_APPLE -> {
                    target.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 2400, 2, false, false));
                    target.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 2400, 1, false, false));
                    healthBonusUntil.put(target.getUniqueId(), now + 120000);
                    applyHealthBonus(target, 40.0);
                    explosionImmuneUntil.put(target.getUniqueId(), now + 120000);
                }
                default -> {}
            }
        }
    }

    private boolean canOvereat(Player chef) {
        long now = System.currentTimeMillis();
        Long until = overeatUntil.get(chef.getUniqueId());
        if (until != null && now <= until) return true;
        if (until != null && now > until) overeatUntil.remove(chef.getUniqueId());
        return false;
    }

    private boolean hasEaterInGuild(Player chef) {
        GuildData guild = guildManager.getGuildByMember(chef.getUniqueId());
        if (guild == null) return false;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.equals(chef)) continue;
            GuildData other = guildManager.getGuildByMember(p.getUniqueId());
            if (other == null || !guild.name.equalsIgnoreCase(other.name)) continue;
            String ability = plugin.getAbilityManager().getPlayerData(p.getUniqueId()).getAbilityName();
            if ("EATER".equals(ability)) return true;
        }
        return false;
    }

    private void startMaintenanceTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                for (Player player : Bukkit.getOnlinePlayers()) {
                    UUID id = player.getUniqueId();
                    Long immuneUntil = harmfulImmuneUntil.get(id);
                    if (immuneUntil != null) {
                        if (now > immuneUntil) {
                            harmfulImmuneUntil.remove(id);
                        } else {
                            clearHarmfulEffects(player);
                        }
                    }

                    Long healthUntil = healthBonusUntil.get(id);
                    if (healthUntil != null && now > healthUntil) {
                        healthBonusUntil.remove(id);
                        removeHealthBonus(player);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void clearHarmfulEffects(Player player) {
        for (PotionEffect effect : player.getActivePotionEffects()) {
            if (effect.getType().getEffectCategory() == PotionEffectType.Category.HARMFUL) {
                player.removePotionEffect(effect.getType());
            }
        }
    }

    private void applyHealthBonus(Player player, double amount) {
        AttributeInstance attr = player.getAttribute(Attribute.MAX_HEALTH);
        if (attr == null) return;
        removeHealthBonus(player);
        attr.addModifier(new AttributeModifier(getChefHealthKey(), amount, AttributeModifier.Operation.ADD_NUMBER));
    }

    private void removeHealthBonus(Player player) {
        AttributeInstance attr = player.getAttribute(Attribute.MAX_HEALTH);
        if (attr == null) return;
        NamespacedKey key = getChefHealthKey();
        attr.getModifiers().stream()
                .filter(mod -> mod.getKey().equals(key))
                .forEach(attr::removeModifier);
    }

    private NamespacedKey getChefHealthKey() {
        return new NamespacedKey(plugin, "chef-health");
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
