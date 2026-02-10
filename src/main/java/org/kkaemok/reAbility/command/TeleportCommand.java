package org.kkaemok.reAbility.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.kkaemok.reAbility.system.TeleportManager;

public class TeleportCommand implements CommandExecutor {
    private final TeleportManager tpManager;

    public TeleportCommand(TeleportManager tpManager) {
        this.tpManager = tpManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) return true;

        if (label.equalsIgnoreCase("rtp")) {
            tpManager.performRTP(player);
            return true;
        }

        if (label.equalsIgnoreCase("tpa")) {
            if (args.length == 0) {
                player.sendMessage("사용법: /tpa <닉네임> | /tpa 수락 | /tpa 거절");
                return true;
            }

            if (args[0].equalsIgnoreCase("수락") || args[0].equalsIgnoreCase("accept")) {
                tpManager.acceptTPA(player);
            } else if (args[0].equalsIgnoreCase("거절") || args[0].equalsIgnoreCase("deny")) {
                player.sendMessage("TPA 요청을 거절했습니다.");
            } else {
                Player target = Bukkit.getPlayer(args[0]);
                if (target != null) tpManager.requestTPA(player, target);
                else player.sendMessage("플레이어를 찾을 수 없습니다.");
            }
        }
        return true;
    }
}
