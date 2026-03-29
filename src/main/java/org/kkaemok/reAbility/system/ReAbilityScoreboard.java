package org.kkaemok.reAbility.system;

import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
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
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public class ReAbilityScoreboard implements Listener {
    private static final String HEADER_MM = "<bold><gradient:#FFE066:#FFB000>REABILITY</gradient></bold>";
    private static final int LINE_COUNT = 6;
    private static final String[] ENTRIES = new String[] {
            "§0", "§1", "§2", "§3", "§4", "§5"
    };

    private static final TextColor ABILITY_COLOR = TextColor.fromHexString("#FF8A00");
    private static final TextColor COINS_COLOR = TextColor.fromHexString("#F9C74F");
    private static final TextColor KILLS_COLOR = TextColor.fromHexString("#FF4D6D");
    private static final TextColor DEATHS_COLOR = TextColor.fromHexString("#E63946");
    private static final TextColor PLAYTIME_COLOR = TextColor.fromHexString("#00D1B2");

    private final ReAbility plugin;
    private final AbilityManager abilityManager;
    private final GuildManager guildManager;
    private final Map<UUID, Board> boards = new HashMap<>();
    private final Set<UUID> mirrorMainTeams = new HashSet<>();
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
            createOrRebuildBoard(player);
        }
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::updateAll, 20L, 20L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (Board board : boards.values()) {
            board.destroy();
        }
        boards.clear();
        mirrorMainTeams.clear();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> createOrRebuildBoard(player));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        mirrorMainTeams.remove(uuid);
        Board board = boards.remove(uuid);
        if (board != null) {
            board.destroy();
        }
    }

    private void updateAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Board board = ensureBoard(player);
            syncMainTeamsIfNeeded(player);
            updateBoard(player, board);
        }
    }

    private Board ensureBoard(Player player) {
        Board board = boards.get(player.getUniqueId());
        Scoreboard liveScoreboard = player.getScoreboard();

        if (board == null || !board.isBoundTo(liveScoreboard) || !board.isAlive()) {
            createOrRebuildBoard(player);
            board = boards.get(player.getUniqueId());
        }

        return Objects.requireNonNull(board, "Board was not created for player " + player.getUniqueId());
    }

    private void createOrRebuildBoard(Player player) {
        Board previous = boards.remove(player.getUniqueId());
        if (previous != null) {
            previous.destroy();
        }

        TargetScoreboard target = resolveTargetScoreboard(player);
        Board board = Board.create(target.scoreboard(), player.getUniqueId(), miniMessage.deserialize(HEADER_MM));

        boards.put(player.getUniqueId(), board);
        if (target.mirrorFromMain()) {
            mirrorMainTeams.add(player.getUniqueId());
        } else {
            mirrorMainTeams.remove(player.getUniqueId());
        }
        updateBoard(player, board);
    }

    private TargetScoreboard resolveTargetScoreboard(Player player) {
        var manager = Objects.requireNonNull(Bukkit.getScoreboardManager(), "Scoreboard manager");
        Scoreboard current = player.getScoreboard();
        Scoreboard main = manager.getMainScoreboard();

        if (current == main) {
            Scoreboard isolated = manager.getNewScoreboard();
            copyTeams(main, isolated);
            player.setScoreboard(isolated);
            return new TargetScoreboard(isolated, true);
        }

        return new TargetScoreboard(current, false);
    }

    private void syncMainTeamsIfNeeded(Player player) {
        if (!mirrorMainTeams.contains(player.getUniqueId())) return;
        var manager = Bukkit.getScoreboardManager();
        if (manager == null) return;

        Scoreboard main = manager.getMainScoreboard();
        Scoreboard target = player.getScoreboard();
        if (target == main) return;

        copyTeams(main, target);
    }

    private void copyTeams(Scoreboard source, Scoreboard target) {
        for (Team sourceTeam : source.getTeams()) {
            Team existing = target.getTeam(sourceTeam.getName());
            if (existing != null) {
                existing.unregister();
            }

            Team copied = target.registerNewTeam(sourceTeam.getName());
            copied.displayName(sourceTeam.displayName());
            copied.prefix(sourceTeam.prefix());
            copied.suffix(sourceTeam.suffix());
            copied.setAllowFriendlyFire(sourceTeam.allowFriendlyFire());
            copied.setCanSeeFriendlyInvisibles(sourceTeam.canSeeFriendlyInvisibles());
            for (Team.Option option : Team.Option.values()) {
                copied.setOption(option, sourceTeam.getOption(option));
            }
            for (String entry : sourceTeam.getEntries()) {
                copied.addEntry(entry);
            }
        }
    }

    private record TargetScoreboard(Scoreboard scoreboard, boolean mirrorFromMain) {
    }

    private void updateBoard(Player player, Board board) {
        String abilityName = getAbilityName(player);
        GuildData guild = guildManager.getGuildByMember(player.getUniqueId());
        long coins = getCoins(player);
        int kills = player.getStatistic(Statistic.PLAYER_KILLS);
        int deaths = player.getStatistic(Statistic.DEATHS);
        String playtime = formatPlaytime(player.getStatistic(Statistic.PLAY_ONE_MINUTE));

        board.setLine(0, composeLine("Ability", Component.text(abilityName, ABILITY_COLOR)));
        board.setLine(1, composeLine("Guild", buildGuildValue(guild)));
        board.setLine(2, composeLine("Coins", Component.text(numberFormat.format(coins), COINS_COLOR)));
        board.setLine(3, composeLine("Kills", Component.text(String.valueOf(kills), KILLS_COLOR)));
        board.setLine(4, composeLine("Deaths", Component.text(String.valueOf(deaths), DEATHS_COLOR)));
        board.setLine(5, composeLine("Playtime", Component.text(playtime, PLAYTIME_COLOR)));
    }

    private String getAbilityName(Player player) {
        PlayerData data = abilityManager.getPlayerData(player.getUniqueId());
        if (data == null || data.getAbilityName() == null) {
            return "None";
        }

        AbilityBase ability = abilityManager.getAbilityByName(data.getAbilityName());
        return ability != null ? ability.getDisplayName() : data.getAbilityName();
    }

    private long getCoins(Player player) {
        PlayerData data = abilityManager.getPlayerData(player.getUniqueId());
        return data != null ? data.getCoins() : 0L;
    }

    private Component composeLine(String label, Component value) {
        return Component.text(label + ": ", NamedTextColor.WHITE)
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

    private static final class Board {
        private final Scoreboard scoreboard;
        private final Objective objective;
        private final Team[] teams = new Team[LINE_COUNT];
        private final String[] teamNames = new String[LINE_COUNT];
        private final String objectiveName;

        private Board(Scoreboard scoreboard, Objective objective, String objectiveName, String token) {
            this.scoreboard = scoreboard;
            this.objective = objective;
            this.objectiveName = objectiveName;
            for (int i = 0; i < LINE_COUNT; i++) {
                teamNames[i] = "ra_l" + i + "_" + token;
            }
        }

        private static Board create(Scoreboard scoreboard, UUID playerId, Component title) {
            String token = playerId.toString().replace("-", "").substring(0, 8);
            String objectiveName = "ra_sb_" + token;

            Objective existingObjective = scoreboard.getObjective(objectiveName);
            if (existingObjective != null) {
                existingObjective.unregister();
            }

            Objective objective = scoreboard.registerNewObjective(objectiveName, Criteria.DUMMY, title);
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
            objective.numberFormat(NumberFormat.blank());

            Board board = new Board(scoreboard, objective, objectiveName, token);
            board.init();
            return board;
        }

        private void init() {
            for (int i = 0; i < LINE_COUNT; i++) {
                Team existingTeam = scoreboard.getTeam(teamNames[i]);
                if (existingTeam != null) {
                    existingTeam.unregister();
                }

                Team team = scoreboard.registerNewTeam(teamNames[i]);
                team.addEntry(ENTRIES[i]);
                objective.getScore(ENTRIES[i]).setScore(LINE_COUNT - i);
                teams[i] = team;
            }
        }

        private boolean isBoundTo(Scoreboard current) {
            return scoreboard == current;
        }

        private boolean isAlive() {
            return scoreboard.getObjective(objectiveName) == objective;
        }

        private void setLine(int index, Component text) {
            if (index < 0 || index >= teams.length) return;
            Team team = teams[index];
            if (team == null) return;

            team.prefix(text == null ? Component.empty() : text);
            team.suffix(Component.empty());
        }

        private void destroy() {
            Objective liveObjective = scoreboard.getObjective(objectiveName);
            if (liveObjective != null) {
                liveObjective.unregister();
            }

            for (String teamName : teamNames) {
                Team team = scoreboard.getTeam(teamName);
                if (team != null) {
                    team.unregister();
                }
            }
        }
    }
}
