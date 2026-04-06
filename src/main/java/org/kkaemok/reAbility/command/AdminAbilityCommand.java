package org.kkaemok.reAbility.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.kkaemok.reAbility.ReAbility;
import org.kkaemok.reAbility.ability.AbilityBase;
import org.kkaemok.reAbility.ability.AbilityManager;
import org.kkaemok.reAbility.ability.RerollTicket;
import org.kkaemok.reAbility.data.PlayerData;
import org.kkaemok.reAbility.guild.GuildManager;
import org.kkaemok.reAbility.integration.NicknamesBridge;
import org.kkaemok.reAbility.item.TicketItemManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class AdminAbilityCommand implements CommandExecutor, TabCompleter {
    private static final String SUB_RELOAD = "리로드";
    private static final String SUB_KEEP = "유지권";
    private static final String SUB_REROLL = "리롤권";
    private static final String SUB_FIREWORK = "무한폭죽";
    private static final String SUB_ASSIGN = "능력부여";
    private static final String SUB_VIEW = "능력보기";
    private static final String SUB_DESCRIBE = "능력설명";
    private static final String SUB_PLAYER_ABILITIES = "플레이어능력";
    private static final String SUB_COINS = "코인";
    private static final String SUB_COINS_SET = "코인설정";
    private static final String PERMANENT = "영구";

    private final ReAbility plugin;
    private final TicketItemManager itemManager;
    private final AbilityManager abilityManager;
    private final GuildManager guildManager;

    public AdminAbilityCommand(ReAbility plugin, TicketItemManager itemManager,
                               AbilityManager abilityManager, GuildManager guildManager) {
        this.plugin = plugin;
        this.itemManager = itemManager;
        this.abilityManager = abilityManager;
        this.guildManager = guildManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
                             @NotNull String[] args) {
        if (!sender.isOp()) {
            sender.sendMessage("권한이 없습니다.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case SUB_RELOAD, "reload" -> handleReload(sender);
            case SUB_KEEP -> handleKeepTicket(sender, args);
            case SUB_REROLL -> handleRerollTicket(sender, args);
            case SUB_FIREWORK -> handleInfiniteFirework(sender, args);
            case SUB_ASSIGN -> handleAssign(sender, args);
            case SUB_VIEW -> handleView(sender, args);
            case SUB_DESCRIBE -> handleDescribe(sender, args);
            case SUB_PLAYER_ABILITIES -> handlePlayerAbilities(sender);
            case SUB_COINS, "coins", "coin" -> handleCoinsAdd(sender, args);
            case SUB_COINS_SET, "coinset", "setcoins" -> handleCoinsSet(sender, args);
            default -> sendHelp(sender);
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("사용법 /레능 <리로드|유지권|리롤권|무한폭죽|능력부여|능력보기|능력설명|플레이어능력|코인|코인설정> ...");
        sender.sendMessage("사용법 /레능 코인 <플레이어> <증감액>");
        sender.sendMessage("사용법 /레능 코인설정 <플레이어> <최종액수>");
    }

    private void handleReload(CommandSender sender) {
        plugin.reloadConfig();
        plugin.applyWorldSettings();
        plugin.getAbilityConfigManager().reloadAll();
        plugin.getAbilityConfigManager().ensureAbilityFiles(abilityManager.getAllAbilities());
        abilityManager.reloadPlayerData();
        guildManager.reloadGuilds();
        sender.sendMessage("[!] 리로드 완료.");
    }

    private void handleKeepTicket(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("사용법 /레능 유지권 <플레이어> <일수|영구>");
            return;
        }

        Player target = NicknamesBridge.findOnlinePlayer(args[1]);
        if (target == null) {
            sender.sendMessage("대상 플레이어가 오프라인입니다.");
            return;
        }

        String value = args[2];
        if (value.equalsIgnoreCase(PERMANENT)) {
            target.getInventory().addItem(itemManager.createKeepPermanentTicket());
            sender.sendMessage("[!] " + target.getName() + "에게 유지권 영구를 지급했습니다.");
            target.sendMessage("[!] 능력 유지권 영구를 받았습니다.");
            return;
        }

        int days;
        try {
            days = Integer.parseInt(value);
        } catch (NumberFormatException e) {
            sender.sendMessage("일수는 숫자로 입력해주세요.");
            return;
        }

        if (days <= 0) {
            sender.sendMessage("일수는 1 이상이어야 합니다.");
            return;
        }

        target.getInventory().addItem(itemManager.createKeepTicket(days));
        sender.sendMessage("[!] " + target.getName() + "에게 유지권 " + days + "일을 지급했습니다.");
        target.sendMessage("[!] 능력 유지권 " + days + "일을 받았습니다.");
    }

    private void handleRerollTicket(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("사용법 /레능 리롤권 <플레이어> [B1|B2|C|A|S]");
            return;
        }

        Player target = NicknamesBridge.findOnlinePlayer(args[1]);
        if (target == null) {
            sender.sendMessage("대상 플레이어가 오프라인입니다.");
            return;
        }

        RerollTicket ticket = RerollTicket.TICKET_B1;
        if (args.length >= 3) {
            ticket = parseTicketType(args[2]);
            if (ticket == null) {
                sender.sendMessage("리롤권 타입이 올바르지 않습니다. (B1, B2, C, A, S)");
                return;
            }
        }

        target.getInventory().addItem(itemManager.createTicket(ticket));
        sender.sendMessage("[!] " + target.getName() + "에게 " + ticket.getName() + "을 지급했습니다.");
        target.sendMessage("[!] " + ticket.getName() + "을 받았습니다.");
    }

    private void handleInfiniteFirework(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("사용법 /레능 무한폭죽 <플레이어>");
            return;
        }

        Player target = NicknamesBridge.findOnlinePlayer(args[1]);
        if (target == null) {
            sender.sendMessage("대상 플레이어가 오프라인입니다.");
            return;
        }

        target.getInventory().addItem(itemManager.createInfiniteFirework());
        sender.sendMessage("[!] " + target.getName() + "에게 무한폭죽을 지급했습니다.");
        target.sendMessage("[!] 무한폭죽을 받았습니다.");
    }

    private void handleAssign(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("사용법 /레능 능력부여 <플레이어> <능력>");
            return;
        }

        Player target = NicknamesBridge.findOnlinePlayer(args[1]);
        if (target == null) {
            sender.sendMessage("대상 플레이어가 오프라인입니다.");
            return;
        }

        String abilityName = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        AbilityBase ability = findAbilityByName(abilityName);
        if (ability == null) {
            sender.sendMessage("해당 능력을 찾을 수 없습니다.");
            return;
        }

        abilityManager.assignAbility(target, ability);
        sender.sendMessage("[!] " + target.getName() + "에게 " + ability.getDisplayName() + " 능력을 부여했습니다.");
        if (!target.equals(sender)) {
            target.sendMessage("[!] " + ability.getDisplayName() + " 능력이 부여되었습니다.");
        }
    }

    private void handleView(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("사용법 /레능 능력보기 <플레이어>");
            return;
        }

        Player target = NicknamesBridge.findOnlinePlayer(args[1]);
        if (target == null) {
            sender.sendMessage("대상 플레이어가 오프라인입니다.");
            return;
        }

        PlayerData data = abilityManager.getPlayerData(target.getUniqueId());
        String abilityKey = data.getAbilityName();
        if (abilityKey == null) {
            sender.sendMessage(target.getName() + ": 능력 없음");
            return;
        }

        AbilityBase ability = abilityManager.getAbilityByName(abilityKey);
        String display = ability != null ? ability.getDisplayName() : abilityKey;
        String grade = ability != null ? ability.getGrade().getLabel() : "?";
        String timeLeft;
        if (data.getExpiryTime() == Long.MAX_VALUE) {
            timeLeft = "영구";
        } else {
            long remainMs = Math.max(0L, data.getExpiryTime() - System.currentTimeMillis());
            long hours = remainMs / (1000 * 60 * 60);
            timeLeft = hours + "시간";
        }

        if (data.getExpiryTime() != Long.MAX_VALUE) {
            timeLeft = abilityManager.formatRemainingAbilityTime(data.getExpiryTime());
        }
        sender.sendMessage(target.getName() + ": " + display + " (" + grade + ", " + timeLeft + ")");
        sender.sendMessage("남은 시간(시/분): " + abilityManager.formatRemainingAbilityTime(data.getExpiryTime()));
    }

    private void handleDescribe(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("사용법 /레능 능력설명 <능력>");
            return;
        }

        String abilityName = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        AbilityBase ability = findAbilityByName(abilityName);
        if (ability == null) {
            sender.sendMessage("해당 능력을 찾을 수 없습니다.");
            return;
        }

        sender.sendMessage("============");
        sender.sendMessage("능력: " + ability.getDisplayName());
        sender.sendMessage("등급: " + ability.getGrade().getLabel());
        sender.sendMessage("설명:");
        for (String line : ability.getDescription()) {
            sender.sendMessage(line);
        }
        sender.sendMessage("============");
    }

    private void handlePlayerAbilities(CommandSender sender) {
        sender.sendMessage("============ 플레이어 능력 ============");
        for (Player target : Bukkit.getOnlinePlayers()) {
            PlayerData data = abilityManager.getPlayerData(target.getUniqueId());
            String abilityKey = data.getAbilityName();
            if (abilityKey == null) {
                sender.sendMessage(target.getName() + ": 없음");
                continue;
            }

            AbilityBase ability = abilityManager.getAbilityByName(abilityKey);
            if (ability == null) {
                sender.sendMessage(target.getName() + ": " + abilityKey);
                continue;
            }

            sender.sendMessage(target.getName() + ": " + ability.getDisplayName()
                    + " (" + ability.getGrade().getLabel() + ")");
        }
        sender.sendMessage("================================");
    }

    private void handleCoinsAdd(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("사용법 /레능 코인 <플레이어> <증감액>");
            return;
        }

        Player target = NicknamesBridge.findOnlinePlayer(args[1]);
        if (target == null) {
            sender.sendMessage("대상 플레이어가 오프라인입니다.");
            return;
        }

        long delta;
        try {
            delta = Long.parseLong(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage("금액은 숫자로 입력해주세요.");
            return;
        }

        PlayerData data = abilityManager.getPlayerData(target.getUniqueId());
        long before = data.getCoins();
        data.addCoins(delta);
        abilityManager.savePlayerData(target.getUniqueId());

        sender.sendMessage("[!] " + target.getName() + " 코인 " + delta + " 추가 완료. (" + before + " -> " + data.getCoins() + ")");
        target.sendMessage("[!] 코인이 " + delta + " 만큼 변경되었습니다. 현재 코인: " + data.getCoins());
    }

    private void handleCoinsSet(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("사용법 /레능 코인설정 <플레이어> <최종액수>");
            return;
        }

        Player target = NicknamesBridge.findOnlinePlayer(args[1]);
        if (target == null) {
            sender.sendMessage("대상 플레이어가 오프라인입니다.");
            return;
        }

        long amount;
        try {
            amount = Long.parseLong(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage("금액은 숫자로 입력해주세요.");
            return;
        }

        PlayerData data = abilityManager.getPlayerData(target.getUniqueId());
        long before = data.getCoins();
        data.setCoins(amount);
        abilityManager.savePlayerData(target.getUniqueId());

        sender.sendMessage("[!] " + target.getName() + " 코인 설정 완료. (" + before + " -> " + data.getCoins() + ")");
        target.sendMessage("[!] 코인이 설정되었습니다. 현재 코인: " + data.getCoins());
    }

    private AbilityBase findAbilityByName(String input) {
        String normalized = input.trim();
        if (normalized.isEmpty()) return null;

        AbilityBase direct = abilityManager.getAbilityByName(normalized.toUpperCase(Locale.ROOT));
        if (direct != null) return direct;

        for (AbilityBase ability : abilityManager.getAllAbilities()) {
            if (ability.getName().equalsIgnoreCase(normalized)) return ability;
            if (ability.getDisplayName().equalsIgnoreCase(normalized)) return ability;
        }
        return null;
    }

    private RerollTicket parseTicketType(String input) {
        if (input == null) return null;
        return switch (input.toUpperCase(Locale.ROOT)) {
            case "B1" -> RerollTicket.TICKET_B1;
            case "B2" -> RerollTicket.TICKET_B2;
            case "C" -> RerollTicket.TICKET_C;
            case "A" -> RerollTicket.TICKET_A;
            case "S" -> RerollTicket.TICKET_S;
            default -> null;
        };
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (!sender.isOp()) return List.of();

        if (args.length == 1) {
            return partialMatches(args[0], List.of(
                    SUB_RELOAD, SUB_KEEP, SUB_REROLL, SUB_FIREWORK, SUB_ASSIGN, SUB_VIEW, SUB_DESCRIBE,
                    SUB_PLAYER_ABILITIES, SUB_COINS, SUB_COINS_SET
            ));
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2 && needsPlayerTarget(sub)) {
            List<String> names = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                names.add(p.getName());
            }
            return partialMatches(args[1], names);
        }

        if (sub.equals(SUB_DESCRIBE)) {
            return suggestAbilityNames(args, 1);
        }

        if (sub.equals(SUB_ASSIGN)) {
            return suggestAbilityNames(args, 2);
        }

        if (sub.equals(SUB_REROLL) && args.length == 3) {
            return partialMatches(args[2], List.of("B1", "B2", "C", "A", "S"));
        }

        if (sub.equals(SUB_KEEP) && args.length == 3) {
            return partialMatches(args[2], List.of("1", "3", "7", "14", "30", PERMANENT));
        }

        if ((sub.equals(SUB_COINS) || sub.equals("coins") || sub.equals("coin")) && args.length == 3) {
            return partialMatches(args[2], List.of("100", "1000", "-100"));
        }

        if ((sub.equals(SUB_COINS_SET) || sub.equals("coinset") || sub.equals("setcoins")) && args.length == 3) {
            return partialMatches(args[2], List.of("0", "100", "1000"));
        }

        return List.of();
    }

    private boolean needsPlayerTarget(String sub) {
        return sub.equals(SUB_KEEP)
                || sub.equals(SUB_REROLL)
                || sub.equals(SUB_FIREWORK)
                || sub.equals(SUB_ASSIGN)
                || sub.equals(SUB_VIEW)
                || sub.equals(SUB_COINS)
                || sub.equals(SUB_COINS_SET)
                || sub.equals("coins")
                || sub.equals("coin")
                || sub.equals("coinset")
                || sub.equals("setcoins");
    }

    private List<String> suggestAbilityNames(String[] args, int startIndex) {
        if (args.length <= startIndex) return List.of();
        Collection<AbilityBase> abilities = abilityManager.getAllAbilities();
        String current = args[args.length - 1];
        Set<String> results = new LinkedHashSet<>();

        if (args.length == startIndex + 1) {
            for (AbilityBase ability : abilities) {
                String key = ability.getName();
                if (StringUtil.startsWithIgnoreCase(key, current)) results.add(key);

                String display = ability.getDisplayName();
                String[] tokens = display.split("\\s+");
                if (tokens.length > 0 && StringUtil.startsWithIgnoreCase(tokens[0], current)) {
                    results.add(tokens[0]);
                }
                if (tokens.length == 1 && StringUtil.startsWithIgnoreCase(display, current)) {
                    results.add(display);
                }
            }
            return new ArrayList<>(results);
        }

        String[] typed = Arrays.copyOfRange(args, startIndex, args.length - 1);
        for (AbilityBase ability : abilities) {
            String[] tokens = ability.getDisplayName().split("\\s+");
            if (tokens.length <= typed.length) continue;

            boolean prefixMatch = true;
            for (int i = 0; i < typed.length; i++) {
                if (!tokens[i].equalsIgnoreCase(typed[i])) {
                    prefixMatch = false;
                    break;
                }
            }
            if (!prefixMatch) continue;

            String next = tokens[typed.length];
            if (StringUtil.startsWithIgnoreCase(next, current)) results.add(next);
        }
        return new ArrayList<>(results);
    }

    private List<String> partialMatches(String token, List<String> candidates) {
        List<String> matches = new ArrayList<>();
        StringUtil.copyPartialMatches(token, candidates, matches);
        return matches;
    }
}
