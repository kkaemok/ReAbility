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
import org.kkaemok.reAbility.ability.AbilityGrade;
import org.kkaemok.reAbility.ability.AbilityManager;
import org.kkaemok.reAbility.data.PlayerData;
import org.kkaemok.reAbility.integration.NicknamesBridge;
import org.kkaemok.reAbility.shop.ShopMenu;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class PlayerAbilityCommand implements CommandExecutor, TabCompleter {
    private final ReAbility plugin;
    private final AbilityManager abilityManager;
    private final ShopMenu shopMenu;

    public PlayerAbilityCommand(ReAbility plugin, AbilityManager abilityManager, ShopMenu shopMenu) {
        this.plugin = plugin;
        this.abilityManager = abilityManager;
        this.shopMenu = shopMenu;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
                             @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("콘솔에서는 사용할 수 없습니다.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "설명" -> handleDescribe(player, args);
            case "리스트" -> handleList(player);
            case "메뉴" -> openEmptyBox(player, "메뉴");
            case "핑" -> player.sendMessage("핑: " + player.getPing() + "ms");
            case "버전" -> player.sendMessage("ReAbility v" + plugin.getDescription().getVersion());
            case "상자" -> openEmptyBox(player, "상자");
            case "송금" -> handleTransfer(player, args);
            default -> sendHelp(player);
        }
        return true;
    }

    private void sendHelp(Player player) {
        player.sendMessage("사용법: /능력 <설명|리스트|메뉴|핑|버전|상자|송금> ...");
    }

    private void handleDescribe(Player player, String[] args) {
        AbilityBase ability;
        if (args.length >= 2) {
            String targetName = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
            ability = findAbilityByName(targetName);
            if (ability == null) {
                player.sendMessage("해당 능력을 찾을 수 없습니다.");
                return;
            }
        } else {
            PlayerData data = abilityManager.getPlayerData(player.getUniqueId());
            if (data.getAbilityName() == null) {
                player.sendMessage("현재 능력이 없습니다.");
                return;
            }
            ability = abilityManager.getAbilityByName(data.getAbilityName());
            if (ability == null) {
                player.sendMessage("능력 정보를 찾을 수 없습니다.");
                return;
            }
        }

        player.sendMessage("============");
        player.sendMessage("능력: " + ability.getDisplayName());
        player.sendMessage("등급: " + ability.getGrade().getLabel());
        player.sendMessage("설명:");
        for (String line : ability.getDescription()) {
            player.sendMessage(line);
        }
        player.sendMessage("============");
    }

    private void handleList(Player player) {
        Map<AbilityGrade, List<String>> byGrade = new EnumMap<>(AbilityGrade.class);
        for (AbilityBase ability : abilityManager.getAllAbilities()) {
            byGrade.computeIfAbsent(ability.getGrade(), k -> new ArrayList<>())
                    .add(ability.getDisplayName());
        }

        player.sendMessage("============ 능력 리스트 ============");
        for (AbilityGrade grade : AbilityGrade.values()) {
            List<String> names = byGrade.get(grade);
            if (names == null || names.isEmpty()) continue;
            names.sort(String::compareToIgnoreCase);
            player.sendMessage(grade.getLabel() + ": " + String.join(", ", names));
        }
        player.sendMessage("================================");
    }

    private void handleTransfer(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage("사용법: /능력 송금 <플레이어> <액수>");
            return;
        }
        Player target = NicknamesBridge.findOnlinePlayer(args[1]);
        if (target == null) {
            player.sendMessage("대상 플레이어가 온라인이 아닙니다.");
            return;
        }
        if (target.equals(player)) {
            player.sendMessage("자기 자신에게 송금할 수 없습니다.");
            return;
        }

        long amount;
        try {
            amount = Long.parseLong(args[2]);
        } catch (NumberFormatException e) {
            player.sendMessage("액수는 숫자로 입력하세요.");
            return;
        }
        if (amount <= 0) {
            player.sendMessage("액수는 1 이상이어야 합니다.");
            return;
        }

        PlayerData senderData = abilityManager.getPlayerData(player.getUniqueId());
        if (senderData.getCoins() < amount) {
            player.sendMessage("코인이 부족합니다.");
            return;
        }

        PlayerData targetData = abilityManager.getPlayerData(target.getUniqueId());
        senderData.addCoins(-amount);
        targetData.addCoins(amount);
        abilityManager.savePlayerData(player.getUniqueId());
        abilityManager.savePlayerData(target.getUniqueId());

        player.sendMessage("송금 완료: " + amount + " 코인");
        target.sendMessage(player.getName() + "님이 " + amount + " 코인을 송금했습니다.");
    }

    private void openEmptyBox(Player player, String title) {
        player.openInventory(Bukkit.createInventory(null, 54, title));
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

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (!(sender instanceof Player)) return List.of();

        if (args.length == 1) {
            return partialMatches(args[0], List.of("설명", "리스트", "메뉴", "핑", "버전", "상자", "송금"));
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("설명")) {
            return suggestAbilityNames(args, 1);
        }
        if (sub.equals("송금") && args.length == 2) {
            List<String> names = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                names.add(p.getName());
            }
            return partialMatches(args[1], names);
        }
        return List.of();
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
