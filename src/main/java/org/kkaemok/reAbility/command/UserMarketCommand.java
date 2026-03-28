package org.kkaemok.reAbility.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.kkaemok.reAbility.usermarket.UserMarketManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class UserMarketCommand implements CommandExecutor, TabCompleter {
    private final UserMarketManager userMarketManager;

    public UserMarketCommand(UserMarketManager userMarketManager) {
        this.userMarketManager = userMarketManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
                             @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("플레이어만 사용할 수 있습니다.");
            return true;
        }

        String name = command.getName().toLowerCase(Locale.ROOT);
        switch (name) {
            case "sell" -> {
                if (args.length > 0) {
                    player.sendMessage("사용법: /팔기");
                    return true;
                }
                userMarketManager.openSellMenu(player);
                return true;
            }
            case "setprice" -> {
                if (args.length != 1) {
                    player.sendMessage("사용법: /가격설정 <가격>");
                    return true;
                }
                long price;
                try {
                    price = Long.parseLong(args[0]);
                } catch (NumberFormatException ex) {
                    player.sendMessage("[유저상점] 가격은 숫자로 입력해주세요.");
                    return true;
                }
                userMarketManager.setPrice(player, price);
                return true;
            }
            case "usermarket" -> {
                int page = 1;
                if (args.length >= 1) {
                    try {
                        page = Integer.parseInt(args[0]);
                    } catch (NumberFormatException ex) {
                        player.sendMessage("[유저상점] 페이지는 숫자로 입력해주세요.");
                        return true;
                    }
                }
                userMarketManager.openMarket(player, page);
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (name.equals("setprice")) {
            if (args.length == 1) {
                return partialMatches(args[0], List.of("100", "500", "1000"));
            }
            return List.of();
        }
        if (name.equals("usermarket")) {
            if (args.length == 1) {
                return partialMatches(args[0], List.of("1", "2", "3"));
            }
            return List.of();
        }
        return List.of();
    }

    private List<String> partialMatches(String token, List<String> candidates) {
        List<String> matches = new ArrayList<>();
        StringUtil.copyPartialMatches(token, candidates, matches);
        return matches;
    }
}
