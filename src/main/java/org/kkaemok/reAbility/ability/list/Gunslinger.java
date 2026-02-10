package org.kkaemok.reAbility.ability.list;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.kkaemok.reAbility.ReAbility;
import org.kkaemok.reAbility.ability.AbilityBase;
import org.kkaemok.reAbility.ability.AbilityGrade;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Gunslinger extends AbilityBase {
    private final ReAbility plugin;
    private final Map<UUID, Long> basicCooldown = new HashMap<>();
    private final Map<UUID, Long> ultimateCooldown = new HashMap<>();
    private final String BULLET_KEY = "gunslinger_bullet";

    public Gunslinger(ReAbility plugin) { this.plugin = plugin; }

    @Override public String getName() { return "GUNSLINGER"; }
    @Override public String getDisplayName() { return "건슬링어"; }
    @Override public AbilityGrade getGrade() { return AbilityGrade.B; }

    @Override
    public String[] getDescription() {
        return new String[]{
                "24시간 저항 1 효과 획득.",
                "웅크릴 시 가스트 투사체 모양의 총알 발사",
                "맞을 시 피해 20 + 5초 동안 불탐 (쿨타임 5초)",
                "스킬 {총알 난사}: 다이아 50개 소모, 3초 후 15발 난사"
        };
    }

    @Override
    public void onActivate(Player player) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, PotionEffect.INFINITE_DURATION, 0, false, false));
    }

    @Override
    public void onDeactivate(Player player) {
        player.removePotionEffect(PotionEffectType.RESISTANCE);
    }

    @Override
    public void onSneakSkill(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        long now = System.currentTimeMillis();

        if (item.getType() == Material.DIAMOND && item.getAmount() >= 50) {
            if (now < ultimateCooldown.getOrDefault(player.getUniqueId(), 0L)) {
                player.sendMessage(Component.text("총알 난사 쿨타임입니다.", NamedTextColor.RED));
                return;
            }
            item.setAmount(item.getAmount() - 50);
            ultimateCooldown.put(player.getUniqueId(), now + 60000);

            player.sendMessage(Component.text("[!] 3초 후 총알 난사를 시작합니다.", NamedTextColor.RED));
            new BukkitRunnable() {
                int count = 0;
                @Override
                public void run() {
                    if (count >= 15) { this.cancel(); return; }
                    shootBullet(player);
                    count++;
                }
            }.runTaskTimer(plugin, 60L, 5L);
            return;
        }

        if (now < basicCooldown.getOrDefault(player.getUniqueId(), 0L)) return;
        shootBullet(player);
        basicCooldown.put(player.getUniqueId(), now + 5000);
    }

    private void shootBullet(Player player) {
        Fireball ball = player.launchProjectile(Fireball.class);
        ball.setYield(0);
        ball.setIsIncendiary(true);
        ball.setMetadata(BULLET_KEY, new FixedMetadataValue(plugin, true));
        player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 2.0f);
    }

    @EventHandler
    public void onBulletHit(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Fireball ball) {
            if (ball.hasMetadata(BULLET_KEY)) {
                event.setDamage(20.0);
            }
        }
    }
}
