package org.kkaemok.reAbility.command;

import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.kkaemok.reAbility.ability.AbilityManager;
import org.kkaemok.reAbility.data.PlayerData;
import org.kkaemok.reAbility.utils.HomeLocationCodec;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class HomeCommand implements CommandExecutor, TabCompleter {
    private final AbilityManager abilityManager;

    public HomeCommand(AbilityManager abilityManager) {
        this.abilityManager = abilityManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
                             @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("콘솔에서는 사용할 수 없습니다.");
            return true;
        }

        if (args.length < 1) {
            player.sendMessage("사용법: /home <이름>");
            return true;
        }

        String name = args[0].trim();
        PlayerData data = abilityManager.getPlayerData(player.getUniqueId());
        String raw = data.getHome(name);
        if (raw == null) {
            player.sendMessage("§c'" + name + "'(이)라는 이름의 홈이 없습니다.");
            return true;
        }

        Location home = HomeLocationCodec.deserialize(raw);
        if (home == null) {
            player.sendMessage("§c해당 홈의 위치 데이터가 손상되었습니다.");
            return true;
        }

        player.teleport(home);
        player.sendMessage("§a홈 '" + name + "'(으)로 이동했습니다.");
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (!(sender instanceof Player player)) return List.of();
        if (args.length != 1) return List.of();

        PlayerData data = abilityManager.getPlayerData(player.getUniqueId());
        String token = args[0].toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String name : data.getHomeNames()) {
            if (name.toLowerCase(Locale.ROOT).startsWith(token)) {
                result.add(name);
            }
        }
        return result;
    }
}
