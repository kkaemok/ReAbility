package org.kkaemok.reAbility.ability.list;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.kkaemok.reAbility.ReAbility;
import org.kkaemok.reAbility.ability.AbilityBase;
import org.kkaemok.reAbility.ability.AbilityGrade;

public class Werewolf extends AbilityBase {
    private final ReAbility plugin;
    private boolean isNightActive = false;

    public Werewolf(ReAbility plugin) {
        this.plugin = plugin;
        startUpdateTask(); // 밤낮 감지를 위한 태스크 시작
    }

    @Override public String getName() { return "WEREWOLF"; }
    @Override public String getDisplayName() { return "늑대인간"; }
    @Override public AbilityGrade getGrade() { return AbilityGrade.A; }

    @Override
    public String[] getDescription() {
        return new String[]{
                "§e[패시브] §f밤이 되면 힘2, 체력 3줄, 저항2, 폭발 데미지 무시를 획득합니다.",
                "§7낮이 되면 모든 버프가 사라집니다.",
                "§6[스킬: 나의 시간] §f다이아몬드 100개를 들고 웅크릴 시 즉시 밤이 됩니다.",
                "§c※ A등급 이상 스킬은 전체 채팅으로 공지됩니다."
        };
    }

    // 밤낮 체크 및 버프 부여 루프
    private void startUpdateTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (!isHasAbility(player)) continue;

                    long time = player.getWorld().getTime();
                    boolean night = (time >= 13000 && time <= 23000);

                    if (night && !isNightActive) {
                        applyNightBuffs(player);
                    } else if (!night && isNightActive) {
                        removeNightBuffs(player);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 20L); // 1초마다 확인
    }

    private void applyNightBuffs(Player player) {
        isNightActive = true;
        player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, Integer.MAX_VALUE, 1, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, Integer.MAX_VALUE, 1, false, false));

        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) maxHealth.setBaseValue(60.0); // 3줄(60)

        player.sendMessage(Component.text("[!] 달이 떴습니다. 늑대인간의 힘이 깨어납니다!", NamedTextColor.RED));
        player.playSound(player.getLocation(), Sound.ENTITY_WOLF_HOWL, 1.0f, 0.8f);
    }

    private void removeNightBuffs(Player player) {
        isNightActive = false;
        player.removePotionEffect(PotionEffectType.STRENGTH);
        player.removePotionEffect(PotionEffectType.RESISTANCE);

        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(40.0); // 기본 2줄로 복구
            if (player.getHealth() > 40.0) player.setHealth(40.0);
        }

        player.sendMessage(Component.text("[!] 해가 떴습니다. 힘이 억제됩니다.", NamedTextColor.GRAY));
    }

    @EventHandler
    public void onExplode(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || !isHasAbility(player)) return;
        if (!isNightActive) return;

        // 폭발 데미지 무효화
        if (event.getCause() == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION ||
                event.getCause() == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION) {
            event.setCancelled(true);
        }
    }

    @Override
    public void onSneakSkill(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType() != Material.DIAMOND || item.getAmount() < 100) return;

        // 소모
        item.setAmount(item.getAmount() - 100);

        // 밤으로 설정
        player.getWorld().setTime(13000);

        // 전체 공지 (A급 이상)
        Bukkit.broadcast(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_RED));
        Bukkit.broadcast(Component.text(player.getName() + "님이 {나의 시간} 스킬을 사용하여 밤을 불렀습니다!", NamedTextColor.RED));
        Bukkit.broadcast(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_RED));

        for (Player online : Bukkit.getOnlinePlayers()) {
            online.playSound(online.getLocation(), Sound.ENTITY_WOLF_HOWL, 1.0f, 0.5f);
        }
    }

    private boolean isHasAbility(Player player) {
        return getName().equals(plugin.getAbilityManager().getPlayerData(player.getUniqueId()).getAbilityName());
    }
}