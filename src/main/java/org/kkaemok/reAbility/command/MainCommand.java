package org.kkaemok.reAbility.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.kkaemok.reAbility.ReAbility;
import org.kkaemok.reAbility.ability.AbilityBase;
import org.kkaemok.reAbility.ability.AbilityManager;
import org.kkaemok.reAbility.ability.RerollTicket;
import org.kkaemok.reAbility.data.PlayerData;
import org.kkaemok.reAbility.guild.GuildManager;
import org.kkaemok.reAbility.item.TicketItemManager;
import org.jetbrains.annotations.NotNull;

public class MainCommand implements CommandExecutor {

    private final ReAbility plugin;
    private final TicketItemManager itemManager;
    private final AbilityManager abilityManager;
    private final GuildManager guildManager;

    public MainCommand(ReAbility plugin, TicketItemManager itemManager,
                       AbilityManager abilityManager, GuildManager guildManager) {
        this.plugin = plugin;
        this.itemManager = itemManager;
        this.abilityManager = abilityManager;
        this.guildManager = guildManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
                             @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("콘솔에서는 사용할 수 없습니다.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        if (args[0].equalsIgnoreCase("설명")) {
            sendAbilityDescription(player);
            return true;
        }

        if (args[0].equalsIgnoreCase("리로드")) {
            if (!player.isOp()) {
                player.sendMessage("권한이 없습니다.");
                return true;
            }
            plugin.reloadConfig();
            abilityManager.reloadPlayerData();
            guildManager.reloadGuilds();
            player.sendMessage("[!] 리로드 완료.");
            return true;
        }

        if (args[0].equalsIgnoreCase("뽑기")) {
            if (!player.isOp()) {
                player.sendMessage("권한이 없습니다.");
                return true;
            }
            if (args.length < 2) {
                player.sendMessage("사용법: /능력 뽑기 <B1/B2/C/A/S>");
                return true;
            }

            RerollTicket ticketType;
            switch (args[1].toUpperCase()) {
                case "B1" -> ticketType = RerollTicket.TICKET_B1;
                case "B2" -> ticketType = RerollTicket.TICKET_B2;
                case "C" -> ticketType = RerollTicket.TICKET_C;
                case "A" -> ticketType = RerollTicket.TICKET_A;
                case "S" -> ticketType = RerollTicket.TICKET_S;
                default -> {
                    player.sendMessage("없는 종류입니다. (B1, B2, C, A, S)");
                    return true;
                }
            }

            player.getInventory().addItem(itemManager.createTicket(ticketType));
            player.sendMessage("[!] " + ticketType.getName() + "을(를) 지급했습니다.");
            return true;
        }

        sendHelp(player);
        return true;
    }

    private void sendHelp(Player player) {
        player.sendMessage("사용법: /능력 설명 <능력>");
        if (player.isOp()) {
            player.sendMessage("사용법: /능력 뽑기 <B1/B2/C/A/S>");
            player.sendMessage("사용법: /능력 리로드");
        }
    }

    private void sendAbilityDescription(Player player) {
        PlayerData data = abilityManager.getPlayerData(player.getUniqueId());
        if (data.getAbilityName() == null) {
            player.sendMessage("현재 능력이 없습니다.");
            return;
        }

        AbilityBase ability = abilityManager.getAbilityByName(data.getAbilityName());
        if (ability == null) {
            player.sendMessage("능력 정보를 찾을 수 없습니다.");
            return;
        }

        player.sendMessage("============");
        player.sendMessage("능력: " + ability.getDisplayName());
        player.sendMessage("등급: " + ability.getGrade().getLabel());
        player.sendMessage("설명:");
        for (String line : ability.getDescription()) {
            player.sendMessage(line);
        }
        player.sendMessage("============");
    }
}
