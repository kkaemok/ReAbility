package org.kkaemok.reAbility.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.kkaemok.reAbility.ability.AbilityBase;
import org.kkaemok.reAbility.ability.AbilityManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CuteCuteCommand implements CommandExecutor, TabCompleter {
    private static final String SET_LIST = "리스트설정";
    private static final String SET_DESC = "설명설정";

    private final AbilityManager abilityManager;

    public CuteCuteCommand(AbilityManager abilityManager) {
        this.abilityManager = abilityManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
                             @NotNull String[] args) {
        if (!sender.isOp()) {
            sender.sendMessage("권한이 없습니다.");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage("사용법: /큐트큐트 <리스트설정|설명설정> <온|오프>");
            return true;
        }

        AbilityBase cuteCute = abilityManager.getAbilityByName("CUTECUTE");
        if (cuteCute == null) {
            sender.sendMessage("CUTECUTE 능력을 찾을 수 없습니다.");
            return true;
        }

        Boolean enabled = parseOnOff(args[1]);
        if (enabled == null) {
            sender.sendMessage("두 번째 인자는 온 또는 오프만 가능합니다.");
            return true;
        }

        String option = args[0].toLowerCase(Locale.ROOT);
        switch (option) {
            case SET_LIST -> {
                abilityManager.setListVisibility(cuteCute, enabled);
                sender.sendMessage("큐트큐트 리스트 노출이 " + (enabled ? "온" : "오프") + "으로 설정되었습니다.");
            }
            case SET_DESC -> {
                abilityManager.setDescriptionVisibility(cuteCute, enabled);
                sender.sendMessage("큐트큐트 설명 노출이 " + (enabled ? "온" : "오프") + "으로 설정되었습니다.");
            }
            default -> sender.sendMessage("사용법: /큐트큐트 <리스트설정|설명설정> <온|오프>");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (!sender.isOp()) return List.of();
        if (args.length == 1) {
            return partialMatches(args[0], List.of(SET_LIST, SET_DESC));
        }
        if (args.length == 2) {
            return partialMatches(args[1], List.of("온", "오프"));
        }
        return List.of();
    }

    private Boolean parseOnOff(String input) {
        String normalized = input.toLowerCase(Locale.ROOT);
        if (normalized.equals("온") || normalized.equals("on")) return true;
        if (normalized.equals("오프") || normalized.equals("off")) return false;
        return null;
    }

    private List<String> partialMatches(String token, List<String> candidates) {
        List<String> matches = new ArrayList<>();
        StringUtil.copyPartialMatches(token, candidates, matches);
        return matches;
    }
}
