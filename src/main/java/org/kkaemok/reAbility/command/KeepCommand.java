package org.kkaemok.reAbility.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.kkaemok.reAbility.item.TicketItemManager;

public class KeepCommand implements CommandExecutor {

    private final TicketItemManager itemManager;

    public KeepCommand(TicketItemManager itemManager) {
        this.itemManager = itemManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
                             @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("콘솔에서 사용할 수 없습니다.");
            return true;
        }

        if (!player.isOp()) {
            player.sendMessage("권한이 없습니다.");
            return true;
        }

        if (args.length < 1) {
            player.sendMessage("사용법: /유지권 <일수>");
            return true;
        }

        int days;
        try {
            days = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            player.sendMessage("일수는 숫자로 입력하세요.");
            return true;
        }

        if (days <= 0) {
            player.sendMessage("일수는 1 이상이어야 합니다.");
            return true;
        }

        player.getInventory().addItem(itemManager.createKeepTicket(days));
        player.sendMessage("[!] 능력 유지권(" + days + "일)을 지급했습니다.");
        return true;
    }
}
