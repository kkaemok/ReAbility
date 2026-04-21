package org.kkaemok.reAbility.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.kkaemok.reAbility.ability.AbilityManager;
import org.kkaemok.reAbility.data.PlayerData;
import org.kkaemok.reAbility.integration.NicknamesBridge;

import java.util.ArrayList;
import java.util.List;

public class HomeLimitAddCommand implements CommandExecutor, TabCompleter {
    private final AbilityManager abilityManager;

    public HomeLimitAddCommand(AbilityManager abilityManager) {
        this.abilityManager = abilityManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
                             @NotNull String[] args) {
        if (!sender.isOp()) {
            sender.sendMessage("§c권한이 없습니다.");
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage("사용법: /homeadd <플레이어>");
            return true;
        }

        Player target = NicknamesBridge.findOnlinePlayer(args[0]);
        if (target == null) {
            sender.sendMessage("플레이어를 찾을 수 없습니다.");
            return true;
        }

        PlayerData data = abilityManager.getPlayerData(target.getUniqueId());
        data.addHomeLimit(1);
        abilityManager.savePlayerData(target.getUniqueId());

        sender.sendMessage("§a" + target.getName() + "님의 홈 설정 가능 개수를 1개 늘렸습니다. (현재: "
                + data.getHomeLimit() + "개)");
        target.sendMessage("§b관리자가 당신의 홈 설정 제한을 늘려주었습니다! (현재: "
                + data.getHomeLimit() + "개)");
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length != 1) return List.of();
        List<String> names = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            names.add(online.getName());
        }
        return names;
    }
}
