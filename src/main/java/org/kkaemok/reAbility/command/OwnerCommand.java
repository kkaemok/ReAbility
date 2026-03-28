package org.kkaemok.reAbility.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.kkaemok.reAbility.ability.AbilityManager;
import org.kkaemok.reAbility.data.PlayerData;
import org.kkaemok.reAbility.integration.NicknamesBridge;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class OwnerCommand implements CommandExecutor, TabCompleter {
    private final AbilityManager abilityManager;

    public OwnerCommand(AbilityManager abilityManager) {
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
        if (!"PUPPY".equals(data.getAbilityName())) {
            player.sendMessage("강아지 능력일 때만 사용할 수 있습니다.");
            return true;
        }

        if (args.length == 0) {
            sendUsage(player);
            sendCurrentOwner(player, data.getDogOwnerUuid());
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "설정" -> {
                if (args.length < 2) {
                    player.sendMessage("사용법: /주인 설정 <플레이어>");
                    return true;
                }

                Player target = NicknamesBridge.findOnlinePlayer(args[1]);
                if (target == null) {
                    player.sendMessage("해당 플레이어가 온라인이 아닙니다.");
                    return true;
                }
                if (target.equals(player)) {
                    player.sendMessage("자기 자신은 주인으로 설정할 수 없습니다.");
                    return true;
                }

                data.setDogOwnerUuid(target.getUniqueId());
                abilityManager.savePlayerData(player.getUniqueId());
                player.sendMessage("주인이 " + target.getName() + "님으로 설정되었습니다.");
                return true;
            }
            case "해제" -> {
                if (data.getDogOwnerUuid() == null) {
                    player.sendMessage("현재 설정된 주인이 없습니다.");
                    return true;
                }
                data.setDogOwnerUuid(null);
                abilityManager.savePlayerData(player.getUniqueId());
                player.sendMessage("주인 설정을 해제했습니다.");
                return true;
            }
            default -> {
                sendUsage(player);
                return true;
            }
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (!(sender instanceof Player)) return List.of();

        if (args.length == 1) {
            return partialMatches(args[0], List.of("설정", "해제"));
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("설정")) {
            List<String> names = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                names.add(p.getName());
            }
            return partialMatches(args[1], names);
        }

        return List.of();
    }

    private void sendUsage(Player player) {
        player.sendMessage("사용법: /주인 설정 <플레이어>");
        player.sendMessage("사용법: /주인 해제");
    }

    private void sendCurrentOwner(Player player, UUID ownerUuid) {
        if (ownerUuid == null) {
            player.sendMessage("현재 설정된 주인이 없습니다.");
            return;
        }

        Player owner = Bukkit.getPlayer(ownerUuid);
        if (owner != null) {
            player.sendMessage("현재 주인: " + owner.getName());
        } else {
            player.sendMessage("현재 주인: " + ownerUuid);
        }
    }

    private List<String> partialMatches(String token, List<String> candidates) {
        List<String> matches = new ArrayList<>();
        StringUtil.copyPartialMatches(token, candidates, matches);
        return matches;
    }
}
