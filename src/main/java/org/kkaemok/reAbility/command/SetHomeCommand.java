package org.kkaemok.reAbility.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.kkaemok.reAbility.ability.AbilityManager;
import org.kkaemok.reAbility.data.PlayerData;
import org.kkaemok.reAbility.utils.HomeLocationCodec;

import java.util.List;

public class SetHomeCommand implements CommandExecutor, TabCompleter {

    private final AbilityManager abilityManager;

    public SetHomeCommand(AbilityManager abilityManager) {
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
            player.sendMessage("사용법: /sethome <이름>");
            return true;
        }

        String name = args[0].trim();
        if (name.isEmpty()) {
            player.sendMessage("홈 이름을 입력해주세요.");
            return true;
        }

        PlayerData data = abilityManager.getPlayerData(player.getUniqueId());
        int count = data.getHomeCount();
        int limit = data.getHomeLimit();
        String encoded = HomeLocationCodec.serialize(player.getLocation());
        if (encoded == null) {
            player.sendMessage("현재 위치를 홈으로 저장할 수 없습니다.");
            return true;
        }

        if (data.hasHome(name)) {
            data.setHome(name, encoded);
            abilityManager.savePlayerData(player.getUniqueId());
            player.sendMessage("§a홈 '" + name + "' 위치를 현재 위치로 수정했습니다.");
            return true;
        }

        if (count >= limit) {
            player.sendMessage("§c홈을 더 이상 설정할 수 없습니다! (현재 최대: " + limit + "개)");
            player.sendMessage("§e관리자에게 홈 개수 추가를 요청하세요.");
            return true;
        }

        data.setHome(name, encoded);
        abilityManager.savePlayerData(player.getUniqueId());
        player.sendMessage("§a홈 '" + name + "'(이)가 설정되었습니다. (사용 중: "
                + data.getHomeCount() + "/" + limit + ")");
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        return List.of();
    }
}
