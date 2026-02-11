package org.kkaemok.reAbility;

import org.bukkit.plugin.java.JavaPlugin;
import org.kkaemok.reAbility.ability.AbilityManager;
import org.kkaemok.reAbility.command.GuildCommand;
import org.kkaemok.reAbility.command.KeepCommand;
import org.kkaemok.reAbility.command.MainCommand;
import org.kkaemok.reAbility.command.TeleportCommand;
import org.kkaemok.reAbility.event.TicketListener;
import org.kkaemok.reAbility.guild.GuildChatListener;
import org.kkaemok.reAbility.guild.GuildManager;
import org.kkaemok.reAbility.item.TicketItemManager;
import org.kkaemok.reAbility.system.*;

import java.util.Objects;
import java.util.logging.Level;

public class ReAbility extends JavaPlugin {

    private AbilityManager abilityManager;
    private GuildManager guildManager;
    private TicketItemManager ticketItemManager;

    @Override
    public void onEnable() {
        try {
            saveDefaultConfig();

            this.guildManager = new GuildManager(this);
            this.abilityManager = new AbilityManager(this);
            this.ticketItemManager = new TicketItemManager(this);

            CombatManager combatManager = new CombatManager();
            TeleportManager teleportManager = new TeleportManager(this, abilityManager, combatManager);

            this.guildManager.loadGuilds();
            this.abilityManager.loadPlayerData();

            registerCommands(teleportManager);
            registerEvents(combatManager, teleportManager);

            new AbilityUpdateTask(this.abilityManager).runTaskTimer(this, 100L, 1200L);

            getLogger().info("모든 시스템이 활성화되었습니다.");
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "플러그인 활성화 중 치명적인 오류 발생!", e);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    private void registerCommands(TeleportManager teleportManager) {
        try {
            Objects.requireNonNull(getCommand("능력")).setExecutor(
                    new MainCommand(this, ticketItemManager, abilityManager, guildManager));
            Objects.requireNonNull(getCommand("유지권")).setExecutor(new KeepCommand(ticketItemManager));

            GuildCommand guildCmd = new GuildCommand(guildManager);
            Objects.requireNonNull(getCommand("길드")).setExecutor(guildCmd);
            Objects.requireNonNull(getCommand("길드챗")).setExecutor(guildCmd);

            TeleportCommand tpCmd = new TeleportCommand(teleportManager);
            Objects.requireNonNull(getCommand("tpa")).setExecutor(tpCmd);
            Objects.requireNonNull(getCommand("rtp")).setExecutor(tpCmd);
        } catch (NullPointerException e) {
            getLogger().log(Level.WARNING, "plugin.yml의 명령어 설정을 확인하세요.", e);
        }
    }

    private void registerEvents(CombatManager combatManager, TeleportManager teleportManager) {
        var pm = getServer().getPluginManager();
        pm.registerEvents(combatManager, this);
        pm.registerEvents(teleportManager, this);
        pm.registerEvents(new TicketListener(this, abilityManager), this);
        pm.registerEvents(new GuildChatListener(guildManager), this);
        pm.registerEvents(new WorldSettingsListener(), this);
        pm.registerEvents(new MiningManager(abilityManager), this);
        pm.registerEvents(new AbilityListener(abilityManager), this);
        pm.registerEvents(new SneakSkillListener(abilityManager), this);
        pm.registerEvents(new MilkEffectListener(this, abilityManager), this);
    }

    @Override
    public void onDisable() {
        if (guildManager != null) guildManager.saveGuilds();
        if (abilityManager != null) abilityManager.saveAll();
        getLogger().info("데이터 저장 완료. 플러그인이 종료되었습니다.");
    }

    public AbilityManager getAbilityManager() { return abilityManager; }
    public GuildManager getGuildManager() { return guildManager; }
    public TicketItemManager getTicketItemManager() { return ticketItemManager; }
}
