package org.kkaemok.reAbility.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import org.kkaemok.reAbility.integration.NicknamesBridge;
import org.kkaemok.reAbility.system.TeleportManager;

import java.util.ArrayList;
import java.util.List;

public class TeleportCommand implements CommandExecutor, TabCompleter {
    private final TeleportManager tpManager;

    public TeleportCommand(TeleportManager tpManager) {
        this.tpManager = tpManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) return true;

        String cmd = command.getName().toLowerCase();
        switch (cmd) {
            case "rtp" -> tpManager.performRTP(player);
            case "tpa" -> {
                if (args.length == 0) {
                    player.sendMessage("사용법 /tpa <플레이어>");
                    return true;
                }
                Player target = NicknamesBridge.findOnlinePlayer(args[0]);
                if (target == null) {
                    player.sendMessage("플레이어를 찾을 수 없습니다.");
                    return true;
                }
                tpManager.requestTPA(player, target);
            }
            case "tphere" -> {
                if (args.length == 0) {
                    player.sendMessage("사용법 /tphere <플레이어>");
                    return true;
                }
                Player target = NicknamesBridge.findOnlinePlayer(args[0]);
                if (target == null) {
                    player.sendMessage("플레이어를 찾을 수 없습니다.");
                    return true;
                }
                tpManager.requestTPAHere(player, target);
            }
            case "tpaccept" -> {
                if (args.length == 0) {
                    player.sendMessage("사용법 /tpaccept <플레이어>");
                    return true;
                }
                Player requester = NicknamesBridge.findOnlinePlayer(args[0]);
                if (requester == null) {
                    player.sendMessage("플레이어를 찾을 수 없습니다.");
                    return true;
                }
                tpManager.acceptTPA(player, requester);
            }
            case "tpadeny" -> {
                if (args.length == 0) {
                    player.sendMessage("사용법 /tpadeny <플레이어>");
                    return true;
                }
                Player requester = NicknamesBridge.findOnlinePlayer(args[0]);
                if (requester == null) {
                    player.sendMessage("플레이어를 찾을 수 없습니다.");
                    return true;
                }
                tpManager.denyTPA(player, requester);
            }
            case "tpacancel" -> {
                if (args.length == 0) {
                    tpManager.cancelTPA(player, null);
                    return true;
                }
                Player target = NicknamesBridge.findOnlinePlayer(args[0]);
                if (target == null) {
                    player.sendMessage("플레이어를 찾을 수 없습니다.");
                    return true;
                }
                tpManager.cancelTPA(player, target);
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player)) return List.of();

        String cmd = command.getName().toLowerCase();
        if (cmd.equals("rtp")) return List.of();

        if ((cmd.equals("tpa") || cmd.equals("tphere")) && args.length == 1) {
            List<String> candidates = new ArrayList<>();
            for (Player online : Bukkit.getOnlinePlayers()) {
                candidates.add(online.getName());
            }
            List<String> matches = new ArrayList<>();
            StringUtil.copyPartialMatches(args[0], candidates, matches);
            return matches;
        }

        if ((cmd.equals("tpaccept") || cmd.equals("tpadeny")) && args.length == 1) {
            List<String> candidates = new ArrayList<>(tpManager.getPendingRequesters(player));
            if (candidates.isEmpty()) {
                for (Player online : Bukkit.getOnlinePlayers()) {
                    candidates.add(online.getName());
                }
            }
            List<String> matches = new ArrayList<>();
            StringUtil.copyPartialMatches(args[0], candidates, matches);
            return matches;
        }

        if (cmd.equals("tpacancel") && args.length == 1) {
            List<String> candidates = new ArrayList<>(tpManager.getPendingTargets(player));
            if (candidates.isEmpty()) {
                for (Player online : Bukkit.getOnlinePlayers()) {
                    candidates.add(online.getName());
                }
            }
            List<String> matches = new ArrayList<>();
            StringUtil.copyPartialMatches(args[0], candidates, matches);
            return matches;
        }

        return List.of();
    }
}
