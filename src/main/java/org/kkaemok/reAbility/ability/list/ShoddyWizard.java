package org.kkaemok.reAbility.ability.list;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerToggleSneakEvent;
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

public class ShoddyWizard extends AbilityBase {
    private final ReAbility plugin;
    private final GuildManager guildManager;

    public ShoddyWizard(ReAbility plugin, GuildManager guildManager) {
        this.plugin = plugin;
        this.guildManager = guildManager;
    }

    @Override public String getName() { return "SHODDY_WIZARD"; }
    @Override public String getDisplayName() { return "엉터리 마법사"; }
    @Override public AbilityGrade getGrade() { return AbilityGrade.D; }

    @Override
    public String[] getDescription() {
        return new String[]{
                "웅크릴 시 신속 10 효과 획득, 비웅크림 시 구속 1 효과 획득.",
                "스킬 {허술한 마법진}: 다이아 5개 소모,",
                "자신 주변 5칸 원형 내 적에게 구속 1, 허기 5 부여.",
                "자신과 같은 길드원은 마법진 효과를 받지 않음."
        };
    }

    @Override
    public void onActivate(Player player) {
        updateSneakEffects(player, player.isSneaking());
    }

    @Override
    public void onDeactivate(Player player) {
        player.removePotionEffect(PotionEffectType.SPEED);
        player.removePotionEffect(PotionEffectType.SLOWNESS);
    }

    @EventHandler
    public void onToggleSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        if (!isHasAbility(player)) return;
        updateSneakEffects(player, event.isSneaking());
    }

    @Override
    public void onSneakSkill(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        SkillCost cost = plugin.getAbilityConfigManager()
                .getSkillCost(getName(), "magic_circle", Material.DIAMOND, 5);
        if (!cost.matchesHand(item)) return;

        if (!cost.consumeFromHand(player)) return;
        int range = plugin.getAbilityConfigManager()
                .getInt(getName(), "skills.magic_circle.range", 5);
        int debuffTicks = plugin.getAbilityConfigManager()
                .getInt(getName(), "skills.magic_circle.debuff-ticks", 200);
        int hungerAmp = plugin.getAbilityConfigManager()
                .getInt(getName(), "skills.magic_circle.hunger-amplifier", 4);
        for (Entity entity : player.getNearbyEntities(range, range, range)) {
            if (entity instanceof Player target && !target.equals(player) && !isSameGuild(player, target)) {
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, debuffTicks, 0, false, false));
                target.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, debuffTicks, hungerAmp, false, false));
            }
        }
        player.sendMessage(Component.text("[!] 스킬 {허술한 마법진} 발동!", NamedTextColor.GRAY));
        SkillParticles.shoddyCircle(player);
    }

    private void updateSneakEffects(Player player, boolean sneaking) {
        if (sneaking) {
            player.removePotionEffect(PotionEffectType.SLOWNESS);
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, PotionEffect.INFINITE_DURATION, 9, false, false));
        } else {
            player.removePotionEffect(PotionEffectType.SPEED);
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, PotionEffect.INFINITE_DURATION, 0, false, false));
        }
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
