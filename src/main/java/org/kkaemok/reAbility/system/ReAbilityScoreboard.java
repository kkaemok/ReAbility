package org.kkaemok.reAbility.system;

import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.kkaemok.reAbility.ReAbility;
import org.kkaemok.reAbility.ability.AbilityBase;
import org.kkaemok.reAbility.ability.AbilityManager;
import org.kkaemok.reAbility.data.PlayerData;
import org.kkaemok.reAbility.guild.GuildData;
import org.kkaemok.reAbility.guild.GuildManager;

import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class ReAbilityScoreboard implements Listener {
    private static final String OBJECTIVE_NAME = "re_ability_sb";
    private static final String HEADER_MM = "<bold><gradient:#FFE066:#FFB000>REABILITY</gradient></bold>";
    private static final TextColor ABILITY_COLOR = TextColor.fromHexString("#FF8A00");
    private static final TextColor GUILD_COLOR = TextColor.fromHexString("#4CC9F0");
    private static final TextColor COINS_COLOR = TextColor.fromHexString("#F9C74F");
    private static final TextColor KILLS_COLOR = TextColor.fromHexString("#FF4D6D");
    private static final TextColor DEATHS_COLOR = TextColor.fromHexString("#E63946");
    private static final TextColor PLAYTIME_COLOR = TextColor.fromHexString("#00D1B2");
    private static final char COLOR_CHAR = (char) 0x00A7;
    private static final int LINE_COUNT = 6;
    private static final String[] ENTRIES = new String[] {
            COLOR_CHAR + "0",
            COLOR_CHAR + "1",
            COLOR_CHAR + "2",
            COLOR_CHAR + "3",
            COLOR_CHAR + "4",
            COLOR_CHAR + "5"
    };

    private final ReAbility plugin;
    private final AbilityManager abilityManager;
    private final GuildManager guildManager;
    private final Map<UUID, Board> boards = new HashMap<>();
    private final DecimalFormat numberFormat = new DecimalFormat("#,###");
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private BukkitTask task;

    public ReAbilityScoreboard(ReAbility plugin, AbilityManager abilityManager, GuildManager guildManager) {
        this.plugin = plugin;
        this.abilityManager = abilityManager;
        this.guildManager = guildManager;
    }

    public void start() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            createBoard(player);
        }
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::updateAll, 20L, 20L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        boards.clear();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        createBoard(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        boards.remove(event.getPlayer().getUniqueId());
    }

    private void createBoard(Player player) {
        var manager = Objects.requireNonNull(Bukkit.getScoreboardManager(), "Scoreboard manager");

        Scoreboard scoreboard = manager.getNewScoreboard();
        Objective objective = scoreboard.registerNewObjective(OBJECTIVE_NAME, Criteria.DUMMY,
                miniMessage.deserialize(HEADER_MM));
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        objective.numberFormat(NumberFormat.blank());

        Board board = new Board(scoreboard, objective);
        board.init();
        boards.put(player.getUniqueId(), board);

        player.setScoreboard(scoreboard);
        updateBoard(player, board);
    }

    private void updateAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Board board = boards.get(player.getUniqueId());
            if (board == null) {
                createBoard(player);
                continue;
            }
            updateBoard(player, board);
        }
    }

    private void updateBoard(Player player, Board board) {
        String abilityName = getAbilityName(player);
        GuildData guild = guildManager.getGuildByMember(player.getUniqueId());
        long coins = getCoins(player);
        int kills = player.getStatistic(Statistic.PLAYER_KILLS);
        int deaths = player.getStatistic(Statistic.DEATHS);
        String playtime = formatPlaytime(player.getStatistic(Statistic.PLAY_ONE_MINUTE));

        board.setLine(0, composeLine("⚡", ABILITY_COLOR, "Ability", Component.text(abilityName, ABILITY_COLOR)));
        board.setLine(1, composeLine("🛡", GUILD_COLOR, "Guild", buildGuildValue(guild)));
        board.setLine(2, composeLine("💰", COINS_COLOR, "Coins", Component.text(numberFormat.format(coins), COINS_COLOR)));
        board.setLine(3, composeLine("🗡", KILLS_COLOR, "Kills", Component.text(String.valueOf(kills), KILLS_COLOR)));
        board.setLine(4, composeLine("☠", DEATHS_COLOR, "Deaths", Component.text(String.valueOf(deaths), DEATHS_COLOR)));
        board.setLine(5, composeLine("⏳", PLAYTIME_COLOR, "Playtime", Component.text(playtime, PLAYTIME_COLOR)));
    }

    private String getAbilityName(Player player) {
        PlayerData data = abilityManager.getPlayerData(player.getUniqueId());
        String key = data.getAbilityName();
        if (key == null) return "None";
        AbilityBase ability = abilityManager.getAbilityByName(key);
        return ability != null ? ability.getDisplayName() : key;
    }

    private long getCoins(Player player) {
        PlayerData data = abilityManager.getPlayerData(player.getUniqueId());
        return data.getCoins();
    }

    private Component composeLine(String emoji, TextColor accent, String label, Component value) {
        return Component.text(emoji + " ", accent)
                .append(Component.text(label + " ", NamedTextColor.WHITE))
                .append(value == null ? Component.empty() : value);
    }

    private Component buildGuildValue(GuildData guild) {
        if (guild == null) {
            return Component.text("None", NamedTextColor.GRAY);
        }
        return Component.text(guild.name, resolveGuildColor(guild.color));
    }

    private TextColor resolveGuildColor(String raw) {
        if (raw == null || raw.isBlank()) {
            return NamedTextColor.WHITE;
        }

        String value = raw.trim();
        if (value.startsWith("#") && value.length() == 7) {
            TextColor hex = TextColor.fromHexString(value);
            if (hex != null) return hex;
        }

        char code = Character.toLowerCase(value.charAt(value.length() - 1));
        return switch (code) {
            case '0' -> NamedTextColor.BLACK;
            case '1' -> NamedTextColor.DARK_BLUE;
            case '2' -> NamedTextColor.DARK_GREEN;
            case '3' -> NamedTextColor.DARK_AQUA;
            case '4' -> NamedTextColor.DARK_RED;
            case '5' -> NamedTextColor.DARK_PURPLE;
            case '6' -> NamedTextColor.GOLD;
            case '7' -> NamedTextColor.GRAY;
            case '8' -> NamedTextColor.DARK_GRAY;
            case '9' -> NamedTextColor.BLUE;
            case 'a' -> NamedTextColor.GREEN;
            case 'b' -> NamedTextColor.AQUA;
            case 'c' -> NamedTextColor.RED;
            case 'd' -> NamedTextColor.LIGHT_PURPLE;
            case 'e' -> NamedTextColor.YELLOW;
            case 'f' -> NamedTextColor.WHITE;
            default -> NamedTextColor.WHITE;
        };
    }

    private String formatPlaytime(int ticks) {
        long seconds = ticks / 20L;
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        return minutes + "m " + secs + "s";
    }

    private static class Board {
        private final Scoreboard scoreboard;
        private final Objective objective;
        private final Team[] teams = new Team[LINE_COUNT];

        private Board(Scoreboard scoreboard, Objective objective) {
            this.scoreboard = scoreboard;
            this.objective = objective;
        }

        private void init() {
            for (int i = 0; i < LINE_COUNT; i++) {
                Team team = scoreboard.registerNewTeam("ra_line_" + i);
                team.addEntry(ENTRIES[i]);
                objective.getScore(ENTRIES[i]).setScore(LINE_COUNT - i);
                teams[i] = team;
            }
        }

        private void setLine(int index, Component text) {
            if (index < 0 || index >= teams.length) return;
            Team team = teams[index];
            if (team == null) return;

            Component safe = text == null ? Component.empty() : text;
            team.prefix(safe);
            team.suffix(Component.empty());
        }
    }
}
