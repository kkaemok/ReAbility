package org.kkaemok.reAbility.command;

import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.kkaemok.reAbility.guild.GuildManager;
import org.jetbrains.annotations.NotNull;

public class GuildCommand implements CommandExecutor {

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
            case "창설":
                if (args.length < 3) {
                    player.sendMessage("§c사용법: /길드 창설 [길드이름] [색깔]");
                    return true;
                }
                if (guildManager.consumeItem(player, Material.DIAMOND, 100)) {
                    guildManager.createGuild(player, args[1], args[2]);
                } else {
                    player.sendMessage("§c다이아몬드 100개가 부족합니다.");
                }
                break;

            case "요청":
                if (args.length < 2) {
                    player.sendMessage("§c사용법: /길드 요청 [길드이름]");
                    return true;
                }
                guildManager.requestJoin(player, args[1]);
                break;

            case "수락":
                if (args.length < 2) {
                    player.sendMessage("§c사용법: /길드 수락 [플레이어이름]");
                    return true;
                }
                guildManager.acceptJoin(player, args[1]);
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
            player.sendMessage("§c소속된 길드가 없습니다.");
            return;
        }
        if (currentLimit >= 10) {
            player.sendMessage("§c이미 최대 인원(10명)입니다.");
            return;
        }

        Material costMat = Material.DIAMOND;
        int amount = switch (currentLimit) {
            case 5 -> 50;
            case 6 -> 100;
            case 7 -> 200;
            case 8 -> 300;
            case 9 -> 1; // 네더라이트 주괴
            default -> 0;
        };

        if (currentLimit == 9) costMat = Material.NETHERITE_INGOT;

        if (guildManager.consumeItem(player, costMat, amount)) {
            guildManager.expandCapacity(player);
        } else {
            String name = (costMat == Material.DIAMOND) ? "다이아몬드" : "네더라이트 주괴";
            player.sendMessage("§c비용이 부족합니다! (" + name + " " + amount + "개 필요)");
        }
    }

    private void sendHelp(Player p) {
        p.sendMessage("§6§l[ 길드 시스템 ]");
        p.sendMessage("§f/길드 창설 [이름] [색깔] §7(다이아 100개)");
        p.sendMessage("§f/길드 요청 [길드이름]");
        p.sendMessage("§f/길드 수락 [닉네임]");
        p.sendMessage("§f/길드 확장 §7(다이아/네더라이트 소모)");
        p.sendMessage("§f/길드챗 §7(토글 방식)");
    }
}