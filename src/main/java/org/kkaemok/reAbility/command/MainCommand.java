package org.kkaemok.reAbility.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.kkaemok.reAbility.ability.RerollTicket;
import org.kkaemok.reAbility.item.TicketItemManager;
import org.jetbrains.annotations.NotNull;

public class MainCommand implements CommandExecutor {

    private final TicketItemManager itemManager;

    public MainCommand(TicketItemManager itemManager) {
        this.itemManager = itemManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
                             @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("콘솔에서는 사용할 수 없습니다.");
            return true;
        }

        if (!player.isOp()) {
            player.sendMessage("권한이 없습니다.");
            return true;
        }

        if (args.length == 0) {
            player.sendMessage("사용법: /능력 뽑기 <종류>");
            player.sendMessage("종류: B1, B2, A, S");
            return true;
        }

        if (args[0].equalsIgnoreCase("뽑기")) {
            if (args.length < 2) {
                player.sendMessage("사용법: /능력 뽑기 <B1/B2/A/S>");
                return true;
            }

            RerollTicket ticketType;
            switch (args[1].toUpperCase()) {
                case "B1" -> ticketType = RerollTicket.TICKET_B1;
                case "B2" -> ticketType = RerollTicket.TICKET_B2;
                case "A" -> ticketType = RerollTicket.TICKET_A;
                case "S" -> ticketType = RerollTicket.TICKET_S;
                default -> {
                    player.sendMessage("없는 종류입니다. (B1, B2, A, S)");
                    return true;
                }
            }

            player.getInventory().addItem(itemManager.createTicket(ticketType));
            player.sendMessage("[!] " + ticketType.getName() + "을(를) 지급했습니다.");
            return true;
        }

        return true;
    }
}
