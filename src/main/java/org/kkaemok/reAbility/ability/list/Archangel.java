package org.kkaemok.reAbility.ability.list;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
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
import org.kkaemok.reAbility.utils.SkillParticles;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Archangel extends AbilityBase {
    private final ReAbility plugin;
    private final GuildManager guildManager;
    private final Map<UUID, Long> soulCooldown = new HashMap<>();
    private final Map<UUID, Long> spearCooldown = new HashMap<>();
    private final Map<UUID, Long> noAttackUntil = new HashMap<>();
    private final Map<UUID, Location> respawnLocations = new HashMap<>();
    private final Map<UUID, UUID> pendingDonor = new HashMap<>();
    private final Map<UUID, Long> spearWeaknessUntil = new HashMap<>();

    public Archangel(ReAbility plugin) {
        this.plugin = plugin;
        this.guildManager = plugin.getGuildManager();
    }

    @Override public String getName() { return "ARCHANGEL"; }
    @Override public String getDisplayName() { return "대천사"; }
    @Override public AbilityGrade getGrade() { return AbilityGrade.SS; }

    @Override
    public String[] getDescription() {
        return new String[]{
                "플레이어 한 명의 HP 5칸을 빼앗아 원하는 사람에게 부여.",
                "자신의 HP 한 줄 증가, 재생2/저항2 상시.",
                "패시브 {대천사의 영혼}: 사망 시 즉시 부활 (쿨타임 24시간)",
                "부활 후 10초 비행, 공격 불가.",
                "스킬 {심판의 창}: 네더라이트 창 우클릭",
                "10칸 앞 범위 적 HP 10 고정 피해, 처치 시 쿨타임 초기화",
                "스킬 {천사의 날개짓}: 깃털 64개 우클릭, 1분간 바람 효과"
        };
    }

    @Override
    public void onActivate(Player player) {
        applyArchangelHealth(player);
        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, PotionEffect.INFINITE_DURATION, 1, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, PotionEffect.INFINITE_DURATION, 1, false, false));
    }

    @Override
    public void onDeactivate(Player player) {
        removeArchangelHealth(player);
        player.removePotionEffect(PotionEffectType.REGENERATION);
        player.removePotionEffect(PotionEffectType.RESISTANCE);
        player.removePotionEffect(PotionEffectType.SPEED);
    }

    @EventHandler
    public void onSelectTarget(PlayerInteractEntityEvent event) {
        Player angel = event.getPlayer();
        if (!isHasAbility(angel)) return;
        if (!angel.isSneaking()) return;
        if (!(event.getRightClicked() instanceof Player target)) return;

        UUID key = angel.getUniqueId();
        UUID donor = pendingDonor.get(key);
        if (donor == null) {
            pendingDonor.put(key, target.getUniqueId());
            angel.sendMessage(Component.text("[!] 체력을 빼앗을 대상을 선택했습니다.", NamedTextColor.GOLD));
            return;
        }

        Player donorPlayer = Bukkit.getPlayer(donor);
        pendingDonor.remove(key);
        if (donorPlayer == null || !donorPlayer.isOnline()) {
            angel.sendMessage(Component.text("대상이 오프라인입니다.", NamedTextColor.RED));
            return;
        }

        double donorHealth = donorPlayer.getHealth();
        donorPlayer.setHealth(Math.max(0.0, donorHealth - 10.0));
        if (target.getAttribute(Attribute.MAX_HEALTH) != null) {
            target.setHealth(Math.min(target.getAttribute(Attribute.MAX_HEALTH).getValue(), target.getHealth() + 10.0));
        } else {
            target.setHealth(Math.max(0.0, target.getHealth() + 10.0));
        }
        angel.sendMessage(Component.text("[!] 체력 이전이 완료되었습니다.", NamedTextColor.GREEN));
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (!isHasAbility(player)) return;

        long now = System.currentTimeMillis();
        if (now < soulCooldown.getOrDefault(player.getUniqueId(), 0L)) return;

        soulCooldown.put(player.getUniqueId(), now + 86400000);
        event.setKeepInventory(true);
        event.setKeepLevel(true);
        respawnLocations.put(player.getUniqueId(), player.getLocation());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (isHasAbility(player)) {
            Location loc = respawnLocations.remove(player.getUniqueId());
            if (loc != null) {
                event.setRespawnLocation(loc);
                player.setAllowFlight(true);
                player.setFlying(true);
                noAttackUntil.put(player.getUniqueId(), System.currentTimeMillis() + 10000);
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (!player.isOnline()) return;
                    player.setFlying(false);
                    player.setAllowFlight(false);
                }, 200L);
            }
        }

        Long weaknessUntil = spearWeaknessUntil.remove(player.getUniqueId());
        if (weaknessUntil != null && System.currentTimeMillis() < weaknessUntil) {
            int ticks = (int) Math.max(1, (weaknessUntil - System.currentTimeMillis()) / 50);
            player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, ticks, 1, false, false));
        }
    }

    @EventHandler
    public void onNoAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;
        Long until = noAttackUntil.get(attacker.getUniqueId());
        if (until == null) return;
        if (System.currentTimeMillis() > until) {
            noAttackUntil.remove(attacker.getUniqueId());
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler
    public void onRightClick(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!isHasAbility(player)) return;
        if (!event.getAction().isRightClick()) return;

        ItemStack item = player.getInventory().getItemInMainHand();
        long now = System.currentTimeMillis();

        if (isNetheriteSpear(item.getType())) {
            if (now < spearCooldown.getOrDefault(player.getUniqueId(), 0L)) {
                player.sendMessage(Component.text("심판의 창 쿨타임입니다.", NamedTextColor.RED));
                return;
            }
            long cooldownMs = plugin.getAbilityConfigManager()
                    .getLong(getName(), "skills.spear.cooldown-ms", 180000L);
            spearCooldown.put(player.getUniqueId(), now + cooldownMs);

            Location strikeLoc = player.getLocation().add(player.getLocation().getDirection().normalize().multiply(10));
            SkillParticles.archangelSpear(player, strikeLoc);
            boolean killed = false;

            for (Player target : Bukkit.getOnlinePlayers()) {
                if (target.equals(player)) continue;
                if (!target.getWorld().equals(player.getWorld())) continue;
                if (isSameGuild(player, target)) continue;
                if (target.getLocation().distanceSquared(strikeLoc) > 144) continue;

                double hp = target.getHealth();
                if (hp <= 10.0) {
                    target.getInventory().addItem(new ItemStack(Material.GOLDEN_APPLE, 5));
                    target.setHealth(0.0);
                    spearWeaknessUntil.put(target.getUniqueId(), now + 600000);
                    killed = true;
                } else {
                    target.setHealth(hp - 10.0);
                    target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 1200, 0, false, false));
                    target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 1200, 3, false, false));
                    target.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 1200, 0, false, false));
                    target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 1200, 1, false, false));
                }
            }

            if (killed) {
                if (player.getAttribute(Attribute.MAX_HEALTH) != null) {
                    player.setHealth(player.getAttribute(Attribute.MAX_HEALTH).getValue());
                }
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 1200, 2, false, false));
                spearCooldown.remove(player.getUniqueId());
            }
            return;
        }

        SkillCost wingCost = plugin.getAbilityConfigManager()
                .getSkillCost(getName(), "wingbeat", Material.FEATHER, 64);
        if (item.getType() == wingCost.getItem()) {
            if (!wingCost.consumeFromInventory(player)) return;
            SkillParticles.archangelWingbeat(player);

            new BukkitRunnable() {
                int tick = 0;
                final Map<UUID, Location> lastLoc = new HashMap<>();

                @Override
                public void run() {
                    if (tick >= 1200 || !player.isOnline() || !isHasAbility(player)) {
                        cancel();
                        return;
                    }

                    for (Player target : Bukkit.getOnlinePlayers()) {
                        if (!target.getWorld().equals(player.getWorld())) continue;
                        if (target.getLocation().distanceSquared(player.getLocation()) > 2025) continue;

                        if (isSameGuild(player, target)) {
                            target.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 40, 2, false, false));
                            target.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 40, 2, false, false));
                            target.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 40, 0, false, false));
                        } else {
                            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 0, false, false));
                            target.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 40, 0, false, false));
                            target.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, 40, 5, false, false));
                            target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 40, 0, false, false));

                            Location prev = lastLoc.get(target.getUniqueId());
                            Location nowLoc = target.getLocation();
                            if (prev != null && prev.distanceSquared(nowLoc) >= 1.0) {
                                if (Math.random() < 0.3) {
                                    Location back = nowLoc.clone().subtract(prev);
                                    target.setVelocity(back.toVector().normalize().multiply(-1.2));
                                }
                            }
                            lastLoc.put(target.getUniqueId(), nowLoc);
                        }
                    }

                    if (tick % 300 == 0 && tick > 0) {
                        for (Player target : Bukkit.getOnlinePlayers()) {
                            if (isSameGuild(player, target)) continue;
                            if (!target.getWorld().equals(player.getWorld())) continue;
                            if (target.getLocation().distanceSquared(player.getLocation()) > 2025) continue;
                            target.damage(50.0, player);
                        }
                    }
                    tick += 20;
                }
            }.runTaskTimer(plugin, 0L, 20L);
        }
    }

    private void applyArchangelHealth(Player player) {
        AttributeInstance attr = player.getAttribute(Attribute.MAX_HEALTH);
        if (attr == null) return;
        removeArchangelHealth(player);
        attr.addModifier(new AttributeModifier(getArchangelHealthKey(), 20.0, AttributeModifier.Operation.ADD_NUMBER));
    }

    private void removeArchangelHealth(Player player) {
        AttributeInstance attr = player.getAttribute(Attribute.MAX_HEALTH);
        if (attr == null) return;
        NamespacedKey key = getArchangelHealthKey();
        attr.getModifiers().stream()
                .filter(mod -> mod.getKey().equals(key))
                .forEach(attr::removeModifier);
    }

    private NamespacedKey getArchangelHealthKey() {
        return new NamespacedKey(plugin, "archangel-health");
    }

    private boolean isNetheriteSpear(Material type) {
        return type == Material.NETHERITE_SPEAR;
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
