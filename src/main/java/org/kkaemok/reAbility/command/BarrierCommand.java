package org.kkaemok.reAbility.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.kkaemok.reAbility.ability.AbilityBase;
import org.kkaemok.reAbility.ability.AbilityManager;
import org.kkaemok.reAbility.ability.list.Chronos;
import org.kkaemok.reAbility.data.PlayerData;

import java.util.List;

public class BarrierCommand implements CommandExecutor, TabCompleter {
    private final AbilityManager abilityManager;

    public BarrierCommand(AbilityManager abilityManager) {
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
        if (data.getAbilityName() == null || !data.getAbilityName().equals("CHRONOS")) {
            player.sendMessage("크로노스 능력만 사용할 수 있습니다.");
            return true;
        }

        AbilityBase ability = abilityManager.getAbilityByName("CHRONOS");
        if (!(ability instanceof Chronos chronos)) {
            player.sendMessage("능력 정보를 찾을 수 없습니다.");
            return true;
        }

        chronos.activateBarrier(player);
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        return List.of();
    }
}
