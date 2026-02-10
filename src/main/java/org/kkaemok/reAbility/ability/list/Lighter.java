package org.kkaemok.reAbility.ability.list;

import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.kkaemok.reAbility.ability.AbilityBase;
import org.kkaemok.reAbility.ability.AbilityGrade;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class Lighter extends AbilityBase {
    private final Set<UUID> fireEnabled = new HashSet<>();

    @Override
    public String getName() { return "LIGHTER"; }

    @Override
    public String getDisplayName() { return "라이터"; }

    @Override
    public AbilityGrade getGrade() { return AbilityGrade.D; }

    @Override
    public String[] getDescription() {
        return new String[]{
                "24시간 화염저항 버프 획득.",
                "지나간 자리에 불길을 남기고 주변 몹/플레이어를 불태움.",
                "(웅크리기로 불길 On/Off)"
        };
    }

    @Override
    public void onActivate(Player player) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, Integer.MAX_VALUE, 0, false, false));
    }

    @Override
    public void onDeactivate(Player player) {
        player.removePotionEffect(PotionEffectType.FIRE_RESISTANCE);
        fireEnabled.remove(player.getUniqueId());
    }

    @Override
    public void onSneakSkill(Player player) {
        UUID uuid = player.getUniqueId();
        if (fireEnabled.contains(uuid)) {
            fireEnabled.remove(uuid);
            player.sendMessage("[!] 불길 생성 모드를 껐습니다.");
        } else {
            fireEnabled.add(uuid);
            player.sendMessage("[!] 불길 생성 모드를 켰습니다.");
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!fireEnabled.contains(player.getUniqueId())) return;

        if (player.getLocation().getBlock().getType() == Material.AIR) {
            player.getLocation().getBlock().setType(Material.FIRE);
        }

        for (Entity entity : player.getNearbyEntities(2, 2, 2)) {
            if (entity instanceof LivingEntity living && !entity.equals(player)) {
                living.setFireTicks(60);
            }
        }
    }
}
