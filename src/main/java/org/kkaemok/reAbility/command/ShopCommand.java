package org.kkaemok.reAbility.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.kkaemok.reAbility.shop.ShopMenu;

public class ShopCommand implements CommandExecutor {
    private final ShopMenu shopMenu;

    public ShopCommand(ShopMenu shopMenu) {
        this.shopMenu = shopMenu;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
                             @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("콘솔에서는 사용할 수 없습니다.");
            return true;
        }
        shopMenu.open(player);
        return true;
    }
}
