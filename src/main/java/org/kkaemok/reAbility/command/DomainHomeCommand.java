package org.kkaemok.reAbility.command;

import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.kkaemok.reAbility.ability.AbilityManager;
import org.kkaemok.reAbility.ability.list.DomainCaster;
import org.kkaemok.reAbility.data.PlayerData;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class DomainHomeCommand implements CommandExecutor, TabCompleter {
    private final AbilityManager abilityManager;

    public DomainHomeCommand(AbilityManager abilityManager) {
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
        if (!"DOMAIN_CASTER".equals(data.getAbilityName())) {
            player.sendMessage("영역술사 능력만 사용할 수 있습니다.");
            return true;
        }

        if (args.length == 0) {
            showHome(player, data);
            player.sendMessage("사용법: /domainhome <set|info|reset>");
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "set", "설정" -> setHome(player, data);
            case "info", "status", "확인", "정보" -> showHome(player, data);
            case "reset", "clear", "초기화" -> resetHome(player, data);
            default -> player.sendMessage("사용법: /domainhome <set|info|reset>");
        }
        return true;
    }

    private void setHome(Player player, PlayerData data) {
        Location base = player.getLocation().clone();
        base.setX(base.getBlockX() + 0.5);
        base.setY(base.getBlockY());
        base.setZ(base.getBlockZ() + 0.5);

        data.setDomainHome(DomainCaster.serializeHome(base));
        abilityManager.savePlayerData(player.getUniqueId());

        player.sendMessage("영역 홈을 현재 위치로 설정했습니다.");
        player.sendMessage("좌표: " + base.getWorld().getName() + " " + base.getBlockX() + ", " + base.getBlockY()
                + ", " + base.getBlockZ());
    }

    private void showHome(Player player, PlayerData data) {
        String raw = data.getDomainHome();
        if (raw == null || raw.isBlank()) {
            player.sendMessage("설정된 영역 홈이 없습니다.");
            return;
        }

        Location home = DomainCaster.deserializeHome(raw);
        if (home == null) {
            player.sendMessage("저장된 영역 홈 데이터가 손상되었습니다. /domainhome set으로 다시 설정하세요.");
            return;
        }

        player.sendMessage("현재 영역 홈:");
        player.sendMessage("- 월드: " + home.getWorld().getName());
        player.sendMessage("- 좌표: " + home.getBlockX() + ", " + home.getBlockY() + ", " + home.getBlockZ());
    }

    private void resetHome(Player player, PlayerData data) {
        data.setDomainHome(null);
        abilityManager.savePlayerData(player.getUniqueId());
        player.sendMessage("영역 홈 설정을 초기화했습니다.");
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length != 1) return List.of();

        List<String> candidates = List.of("set", "info", "reset", "설정", "확인", "초기화");
        String token = args[0].toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String candidate : candidates) {
            if (candidate.toLowerCase(Locale.ROOT).startsWith(token)) {
                result.add(candidate);
            }
        }
        return result;
    }
}
