package org.kkaemok.reAbility;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;
import org.kkaemok.reAbility.ability.AbilityConfigManager;
import org.kkaemok.reAbility.ability.AbilityManager;
import org.kkaemok.reAbility.command.AdminAbilityCommand;
import org.kkaemok.reAbility.command.DomainHomeCommand;
import org.kkaemok.reAbility.command.GuildCommand;
import org.kkaemok.reAbility.command.PlayerAbilityCommand;
import org.kkaemok.reAbility.command.ShopCommand;
import org.kkaemok.reAbility.command.SoulCommand;
import org.kkaemok.reAbility.command.TeleportCommand;
import org.kkaemok.reAbility.command.UserMarketCommand;
import org.kkaemok.reAbility.event.InfiniteFireworkListener;
import org.kkaemok.reAbility.event.TicketListener;
import org.kkaemok.reAbility.guild.GuildChatListener;
import org.kkaemok.reAbility.guild.GuildListener;
import org.kkaemok.reAbility.guild.GuildManager;
import org.kkaemok.reAbility.integration.NicknamesBridge;
import org.kkaemok.reAbility.item.TicketItemManager;
import org.kkaemok.reAbility.shop.ShopMenu;
import org.kkaemok.reAbility.system.AbilityListener;
import org.kkaemok.reAbility.system.AbilityUpdateTask;
import org.kkaemok.reAbility.system.CombatManager;
import org.kkaemok.reAbility.system.JokerCommandAliasListener;
import org.kkaemok.reAbility.system.MilkEffectListener;
import org.kkaemok.reAbility.system.MiningManager;
import org.kkaemok.reAbility.system.PlaytimeRewardTask;
import org.kkaemok.reAbility.system.ReAbilityScoreboard;
import org.kkaemok.reAbility.system.SneakSkillListener;
import org.kkaemok.reAbility.system.SpectatorLockListener;
import org.kkaemok.reAbility.system.TeleportManager;
import org.kkaemok.reAbility.system.WorldSettingsListener;
import org.kkaemok.reAbility.usermarket.UserMarketManager;

import java.util.Objects;
import java.util.logging.Level;

public class ReAbility extends JavaPlugin {
    private AbilityManager abilityManager;
    private AbilityConfigManager abilityConfigManager;
    private GuildManager guildManager;
    private TicketItemManager ticketItemManager;
    private ReAbilityScoreboard scoreboard;
    private ShopMenu shopMenu;
    private UserMarketManager userMarketManager;
    private SoulCommand soulCommand;
    private DomainHomeCommand domainHomeCommand;

    @Override
    public void onEnable() {
        try {
            saveDefaultConfig();
            applyWorldSettings();

            this.guildManager = new GuildManager(this);
            this.abilityConfigManager = new AbilityConfigManager(this);
            this.abilityManager = new AbilityManager(this);
            this.ticketItemManager = new TicketItemManager(this);
            this.shopMenu = new ShopMenu(this, abilityManager);
            this.userMarketManager = new UserMarketManager(this, abilityManager);
            this.soulCommand = new SoulCommand(this, abilityManager);
            this.domainHomeCommand = new DomainHomeCommand(abilityManager);
            this.scoreboard = new ReAbilityScoreboard(this, abilityManager, guildManager);
            NicknamesBridge.initialize(this);

            CombatManager combatManager = new CombatManager(this);
            TeleportManager teleportManager = new TeleportManager(this, abilityManager, combatManager);

            guildManager.loadGuilds();
            abilityManager.loadPlayerData();
            abilityConfigManager.ensureAbilityFiles(abilityManager.getAllAbilities());

            registerCommands(teleportManager);
            registerEvents(combatManager, teleportManager);

            new AbilityUpdateTask(abilityManager).runTaskTimer(this, 100L, 1200L);
            new PlaytimeRewardTask(abilityManager).runTaskTimer(this, 1200L, 1200L);
            scoreboard.start();

            getLogger().info("모든 시스템이 활성화되었습니다.");
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "플러그인 활성화 중 치명적인 오류 발생!", e);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    private void registerCommands(TeleportManager teleportManager) {
        try {
            AdminAbilityCommand adminCmd = new AdminAbilityCommand(this, ticketItemManager, abilityManager, guildManager);
            Objects.requireNonNull(getCommand("reabilityadmin")).setExecutor(adminCmd);
            Objects.requireNonNull(getCommand("reabilityadmin")).setTabCompleter(adminCmd);

            PlayerAbilityCommand playerCmd = new PlayerAbilityCommand(this, abilityManager, shopMenu);
            Objects.requireNonNull(getCommand("ability")).setExecutor(playerCmd);
            Objects.requireNonNull(getCommand("ability")).setTabCompleter(playerCmd);

            ShopCommand shopCmd = new ShopCommand(shopMenu);
            Objects.requireNonNull(getCommand("shop")).setExecutor(shopCmd);

            UserMarketCommand userMarketCmd = new UserMarketCommand(userMarketManager);
            Objects.requireNonNull(getCommand("usermarket")).setExecutor(userMarketCmd);
            Objects.requireNonNull(getCommand("usermarket")).setTabCompleter(userMarketCmd);
            Objects.requireNonNull(getCommand("sell")).setExecutor(userMarketCmd);
            Objects.requireNonNull(getCommand("sell")).setTabCompleter(userMarketCmd);
            Objects.requireNonNull(getCommand("setprice")).setExecutor(userMarketCmd);
            Objects.requireNonNull(getCommand("setprice")).setTabCompleter(userMarketCmd);

            Objects.requireNonNull(getCommand("soul")).setExecutor(soulCommand);
            Objects.requireNonNull(getCommand("soul")).setTabCompleter(soulCommand);

            Objects.requireNonNull(getCommand("domainhome")).setExecutor(domainHomeCommand);
            Objects.requireNonNull(getCommand("domainhome")).setTabCompleter(domainHomeCommand);

            GuildCommand guildCmd = new GuildCommand(guildManager);
            Objects.requireNonNull(getCommand("guild")).setExecutor(guildCmd);
            Objects.requireNonNull(getCommand("guild")).setTabCompleter(guildCmd);
            Objects.requireNonNull(getCommand("guildchat")).setExecutor(guildCmd);
            Objects.requireNonNull(getCommand("guildchat")).setTabCompleter(guildCmd);

            TeleportCommand tpCmd = new TeleportCommand(teleportManager);
            Objects.requireNonNull(getCommand("tpa")).setExecutor(tpCmd);
            Objects.requireNonNull(getCommand("tpa")).setTabCompleter(tpCmd);
            Objects.requireNonNull(getCommand("tphere")).setExecutor(tpCmd);
            Objects.requireNonNull(getCommand("tphere")).setTabCompleter(tpCmd);
            Objects.requireNonNull(getCommand("tpaccept")).setExecutor(tpCmd);
            Objects.requireNonNull(getCommand("tpaccept")).setTabCompleter(tpCmd);
            Objects.requireNonNull(getCommand("tpadeny")).setExecutor(tpCmd);
            Objects.requireNonNull(getCommand("tpadeny")).setTabCompleter(tpCmd);
            Objects.requireNonNull(getCommand("tpacancel")).setExecutor(tpCmd);
            Objects.requireNonNull(getCommand("tpacancel")).setTabCompleter(tpCmd);
            Objects.requireNonNull(getCommand("rtp")).setExecutor(tpCmd);
            Objects.requireNonNull(getCommand("rtp")).setTabCompleter(tpCmd);
        } catch (NullPointerException e) {
            getLogger().log(Level.WARNING, "plugin.yml 명령어 설정을 확인하세요.", e);
        }
    }

    private void registerEvents(CombatManager combatManager, TeleportManager teleportManager) {
        var pm = getServer().getPluginManager();
        pm.registerEvents(combatManager, this);
        pm.registerEvents(teleportManager, this);
        pm.registerEvents(new TicketListener(this, abilityManager), this);
        pm.registerEvents(new InfiniteFireworkListener(this, ticketItemManager), this);
        pm.registerEvents(shopMenu, this);
        pm.registerEvents(userMarketManager, this);
        pm.registerEvents(new GuildChatListener(guildManager), this);
        pm.registerEvents(new WorldSettingsListener(this), this);
        pm.registerEvents(new MiningManager(this, abilityManager), this);
        pm.registerEvents(new AbilityListener(abilityManager), this);
        pm.registerEvents(new SneakSkillListener(abilityManager), this);
        pm.registerEvents(new MilkEffectListener(this, abilityManager), this);
        pm.registerEvents(new SpectatorLockListener(), this);
        pm.registerEvents(new JokerCommandAliasListener(), this);
        pm.registerEvents(scoreboard, this);
        pm.registerEvents(new GuildListener(guildManager), this);
        pm.registerEvents(soulCommand, this);
    }

    @Override
    public void onDisable() {
        if (scoreboard != null) scoreboard.stop();
        if (userMarketManager != null) userMarketManager.shutdown();
        if (guildManager != null) guildManager.saveGuilds();
        if (abilityManager != null) abilityManager.saveAll();
        getLogger().info("데이터 저장 완료. 플러그인이 종료되었습니다.");
    }

    public void applyWorldSettings() {
        ConfigurationSection border = getConfig().getConfigurationSection("world-settings.border");
        if (border == null) return;

        double minX = border.getDouble("min-x", -30000.0);
        double maxX = border.getDouble("max-x", 30000.0);
        double minZ = border.getDouble("min-z", -30000.0);
        double maxZ = border.getDouble("max-z", 30000.0);

        double sizeX = maxX - minX;
        double sizeZ = maxZ - minZ;
        if (sizeX <= 0 || sizeZ <= 0) {
            getLogger().warning("Invalid world border size in config.yml");
            return;
        }

        double centerX = minX + (sizeX / 2.0);
        double centerZ = minZ + (sizeZ / 2.0);
        double size = Math.max(sizeX, sizeZ);

        for (World world : Bukkit.getWorlds()) {
            WorldBorder wb = world.getWorldBorder();
            wb.setCenter(centerX, centerZ);
            wb.setSize(size);
        }
    }

    public AbilityManager getAbilityManager() {
        return abilityManager;
    }

    public AbilityConfigManager getAbilityConfigManager() {
        return abilityConfigManager;
    }

    public GuildManager getGuildManager() {
        return guildManager;
    }
}
