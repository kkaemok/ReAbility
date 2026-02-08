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
                player.sendMessage("§c사용법: /tpa <닉네임> | /tpa 수락 | /tpa 거절");
                return true;
            }

            if (args[0].equals("수락")) {
                tpManager.acceptTPA(player);
            } else if (args[0].equals("거절")) {
                player.sendMessage("§c요청을 거절했습니다."); // 필요시 기능 확장
            } else {
                Player target = Bukkit.getPlayer(args[0]);
                if (target != null) tpManager.requestTPA(player, target);
                else player.sendMessage("§c플레이어를 찾을 수 없습니다.");
            }
        }
        return true;
    }
}