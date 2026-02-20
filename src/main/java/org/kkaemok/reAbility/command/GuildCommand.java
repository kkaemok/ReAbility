package org.kkaemok.reAbility.command;

import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import org.kkaemok.reAbility.guild.GuildManager;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class GuildCommand implements CommandExecutor, TabCompleter {

    private final GuildManager guildManager;

    public GuildCommand(GuildManager guildManager) {
        this.guildManager = guildManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!(sender instanceof Player player)) return true;

        if (label.equalsIgnoreCase("길드챗")) {
            guildManager.toggleGuildChat(player);
            return true;
        }

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        switch (args[0]) {
            case "생성":
                if (args.length < 3) {
                    player.sendMessage("사용법: /길드 생성 [길드명] [색상]");
                    return true;
                }
                if (guildManager.consumeItem(player, Material.DIAMOND, 100)) {
                    guildManager.createGuild(player, args[1], args[2]);
                } else {
                    player.sendMessage("다이아몬드 100개가 부족합니다.");
                }
                break;

            case "요청":
                if (args.length < 2) {
                    player.sendMessage("사용법: /길드 요청 [길드명]");
                    return true;
                }
                guildManager.requestJoin(player, args[1]);
                break;

            case "수락":
                if (args.length < 2) {
                    player.sendMessage("사용법: /길드 수락 [플레이어명]");
                    return true;
                }
                guildManager.acceptJoin(player, args[1]);
                break;

            case "탈퇴":
                guildManager.leaveGuild(player);
                break;

            case "확장":
                handleExpansion(player);
                break;

            default:
                sendHelp(player);
                break;
        }
        return true;
    }

    private void handleExpansion(Player player) {
        int currentLimit = guildManager.getGuildMemberLimit(player);
        if (currentLimit == -1) {
            player.sendMessage("가입한 길드가 없습니다.");
            return;
        }
        if (currentLimit >= 10) {
            player.sendMessage("최대 인원은 10명입니다.");
            return;
        }

        Material costMat = Material.DIAMOND;
        int amount = switch (currentLimit) {
            case 5 -> 50;
            case 6 -> 100;
            case 7 -> 200;
            case 8 -> 300;
            case 9 -> 1;
            default -> 0;
        };

        if (currentLimit == 9) costMat = Material.NETHERITE_INGOT;

        if (guildManager.consumeItem(player, costMat, amount)) {
            guildManager.expandCapacity(player);
        } else {
            String name = (costMat == Material.DIAMOND) ? "다이아몬드" : "네더라이트 주괴";
            player.sendMessage("비용이 부족합니다! (" + name + " " + amount + "개 필요)");
        }
    }

    private void sendHelp(Player p) {
        p.sendMessage("§6§l[ 길드 시스템 ]");
        p.sendMessage("§f/길드 생성 [이름] [색상] §7(다이아 100개)");
        p.sendMessage("§f/길드 요청 [길드명]");
        p.sendMessage("§f/길드 수락 [플레이어명]");
        p.sendMessage("§f/길드 탈퇴");
        p.sendMessage("§f/길드 확장 §7(다이아/네더라이트 소모)");
        p.sendMessage("§f/길드챗 §7(토글)");
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (!(sender instanceof Player)) return List.of();
        Player player = (Player) sender;

        if (alias.equalsIgnoreCase("길드챗")) return List.of();

        if (args.length == 1) {
            return partialMatches(args[0], List.of("생성", "요청", "수락", "탈퇴", "확장"));
        }

        if (args.length == 2) {
            switch (args[0]) {
                case "요청" -> {
                    return partialMatches(args[1], new ArrayList<>(guildManager.guilds.keySet()));
                }
                case "수락" -> {
                    List<String> reqs = guildManager.pendingRequests.getOrDefault(player.getUniqueId(), List.of());
                    return partialMatches(args[1], new ArrayList<>(reqs));
                }
                default -> {
                    return List.of();
                }
            }
        }

        if (args.length == 3 && args[0].equals("생성")) {
            return partialMatches(args[2], List.of(
                    "빨강", "파랑", "분홍", "하늘", "주황", "노랑",
                    "검정", "하양", "초록", "연두", "보라"
            ));
        }

        return List.of();
    }

    private List<String> partialMatches(String token, List<String> candidates) {
        List<String> matches = new ArrayList<>();
        StringUtil.copyPartialMatches(token, candidates, matches);
        return matches;
    }
}
