package org.kkaemok.reAbility;

import org.bukkit.plugin.java.JavaPlugin;
import org.kkaemok.reAbility.ability.AbilityManager;
import org.kkaemok.reAbility.command.GuildCommand;
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
            // 1. 기본 설정 저장
            saveDefaultConfig();

            // 2. 매니저 초기화 (순서 중요: GuildManager를 먼저 생성해야 Lovebird 등록 가능)
            this.guildManager = new GuildManager(this);
            this.abilityManager = new AbilityManager(this);
            this.ticketItemManager = new TicketItemManager(this);

            CombatManager combatManager = new CombatManager();
            TeleportManager teleportManager = new TeleportManager(this, abilityManager, combatManager);

            // 3. 데이터 파일 로드
            this.guildManager.loadGuilds();
            this.abilityManager.loadPlayerData();

            // 4. 명령어 및 이벤트 등록
            registerCommands(teleportManager);
            registerEvents(combatManager, teleportManager);

            // 5. 실시간 능력 감시 태스크
            new AbilityUpdateTask(this.abilityManager).runTaskTimer(this, 100L, 1200L);

            getLogger().info("모든 시스템이 활성화되었습니다.");
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "플러그인 활성화 중 치명적인 오류 발생!", e);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    private void registerCommands(TeleportManager teleportManager) {
        try {
            Objects.requireNonNull(getCommand("능력")).setExecutor(new MainCommand(ticketItemManager));

            GuildCommand guildCmd = new GuildCommand(guildManager);
            Objects.requireNonNull(getCommand("길드")).setExecutor(guildCmd);
            Objects.requireNonNull(getCommand("길드챗")).setExecutor(guildCmd);

            TeleportCommand tpCmd = new TeleportCommand(teleportManager);
            Objects.requireNonNull(getCommand("tpa")).setExecutor(tpCmd);
            Objects.requireNonNull(getCommand("rtp")).setExecutor(tpCmd);
        } catch (NullPointerException e) {
            getLogger().log(Level.WARNING, "plugin.yml에 명령어가 누락되었습니다.", e);
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
        pm.registerEvents(new AbilityListener(abilityManager), this); // AbilityListener 등록 추가
    }

    @Override
    public void onDisable() {
        if (guildManager != null) guildManager.saveGuilds();
        if (abilityManager != null) abilityManager.saveAll();
        getLogger().info("데이터 저장 완료 후 플러그인이 종료되었습니다.");
    }

    public AbilityManager getAbilityManager() { return abilityManager; }
    public GuildManager getGuildManager() { return guildManager; }
    public TicketItemManager getTicketItemManager() { return ticketItemManager; }
}