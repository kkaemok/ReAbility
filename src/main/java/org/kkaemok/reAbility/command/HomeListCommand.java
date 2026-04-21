package org.kkaemok.reAbility.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.kkaemok.reAbility.ability.AbilityManager;
import org.kkaemok.reAbility.data.PlayerData;

import java.util.List;

public class HomeListCommand implements CommandExecutor, TabCompleter {
    private final AbilityManager abilityManager;

    public HomeListCommand(AbilityManager abilityManager) {
        this.abilityManager = abilityManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
                             @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("콘솔에서는 사용할 수 없습니다.");
            return true;
        }

        PlayerData data = abilityManager.getPlayerData(player.getUniqueId());
        List<String> names = data.getHomeNames();
        String joined = names.isEmpty() ? "없음" : String.join(", ", names);
        player.sendMessage("§f[§6홈 목록§f] §e" + joined + " §7(" + names.size() + "/" + data.getHomeLimit() + ")");
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        return List.of();
    }
}
