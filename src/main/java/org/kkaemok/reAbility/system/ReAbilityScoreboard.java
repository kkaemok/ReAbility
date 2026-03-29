package org.kkaemok.reAbility.system;

import fr.mrmicky.fastboard.adventure.FastBoard;
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
import org.kkaemok.reAbility.ReAbility;
import org.kkaemok.reAbility.ability.AbilityBase;
import org.kkaemok.reAbility.ability.AbilityManager;
import org.kkaemok.reAbility.data.PlayerData;
import org.kkaemok.reAbility.guild.GuildData;
import org.kkaemok.reAbility.guild.GuildManager;

import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class ReAbilityScoreboard implements Listener {
    private static final String HEADER_MM = "<bold><gradient:#FFE066:#FFB000>REABILITY</gradient></bold>";

    private static final TextColor ABILITY_COLOR = TextColor.fromHexString("#FF8A00");
    private static final TextColor COINS_COLOR = TextColor.fromHexString("#F9C74F");
    private static final TextColor KILLS_COLOR = TextColor.fromHexString("#FF4D6D");
    private static final TextColor DEATHS_COLOR = TextColor.fromHexString("#E63946");
    private static final TextColor PLAYTIME_COLOR = TextColor.fromHexString("#00D1B2");

    private final ReAbility plugin;
    private final AbilityManager abilityManager;
    private final GuildManager guildManager;
    private final Map<UUID, FastBoard> boards = new HashMap<>();
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

        for (FastBoard board : boards.values()) {
            deleteBoard(board);
        }
        boards.clear();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> createOrRebuildBoard(player));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        FastBoard board = boards.remove(event.getPlayer().getUniqueId());
        deleteBoard(board);
    }

    private void updateAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            FastBoard board = ensureBoard(player);
            updateBoard(player, board);
        }
    }

    private FastBoard ensureBoard(Player player) {
        FastBoard board = boards.get(player.getUniqueId());
        if (board == null || board.isDeleted()) {
            createOrRebuildBoard(player);
            board = boards.get(player.getUniqueId());
        }

        return Objects.requireNonNull(board, "Board was not created for player " + player.getUniqueId());
    }

    private void createOrRebuildBoard(Player player) {
        FastBoard previous = boards.remove(player.getUniqueId());
        deleteBoard(previous);

        FastBoard board = new FastBoard(player);
        board.updateTitle(miniMessage.deserialize(HEADER_MM));

        boards.put(player.getUniqueId(), board);
        updateBoard(player, board);
    }

    private void deleteBoard(FastBoard board) {
        if (board == null) {
            return;
        }

        try {
            board.delete();
        } catch (RuntimeException ignored) {
            // Ignore state errors during shutdown/quit paths.
        }
    }

    private void updateBoard(Player player, FastBoard board) {
        String abilityName = getAbilityName(player);
        GuildData guild = guildManager.getGuildByMember(player.getUniqueId());
        long coins = getCoins(player);
        int kills = player.getStatistic(Statistic.PLAYER_KILLS);
        int deaths = player.getStatistic(Statistic.DEATHS);
        String playtime = formatPlaytime(player.getStatistic(Statistic.PLAY_ONE_MINUTE));

        List<Component> lines = List.of(
                composeLine("Ability", Component.text(abilityName, ABILITY_COLOR)),
                composeLine("Guild", buildGuildValue(guild)),
                composeLine("Coins", Component.text(numberFormat.format(coins), COINS_COLOR)),
                composeLine("Kills", Component.text(String.valueOf(kills), KILLS_COLOR)),
                composeLine("Deaths", Component.text(String.valueOf(deaths), DEATHS_COLOR)),
                composeLine("Playtime", Component.text(playtime, PLAYTIME_COLOR))
        );

        board.updateLines(lines);
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
}
