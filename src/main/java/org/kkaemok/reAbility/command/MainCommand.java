package org.kkaemok.reAbility.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import org.kkaemok.reAbility.ReAbility;
import org.kkaemok.reAbility.ability.AbilityBase;
import org.kkaemok.reAbility.ability.AbilityManager;
import org.kkaemok.reAbility.ability.RerollTicket;
import org.kkaemok.reAbility.data.PlayerData;
import org.kkaemok.reAbility.guild.GuildManager;
import org.kkaemok.reAbility.item.TicketItemManager;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainCommand implements CommandExecutor, TabCompleter {

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
            String targetName = args.length >= 2 ? String.join(" ", Arrays.copyOfRange(args, 1, args.length)) : null;
            sendAbilityDescription(player, targetName);
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

        if (args[0].equalsIgnoreCase("주인")) {
            handleOwnerCommand(player, args);
            return true;
        }

        if (args[0].equalsIgnoreCase("리스트") || args[0].equalsIgnoreCase("list")) {
            if (!player.isOp()) {
                player.sendMessage("권한이 없습니다.");
                return true;
            }
            sendAbilityList(player);
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
        player.sendMessage("사용법: /능력 설명 [능력]");
        player.sendMessage("사용법: /능력 주인 <플레이어|해제>");
        if (player.isOp()) {
            player.sendMessage("사용법: /능력 뽑기 <B1/B2/C/A/S>");
            player.sendMessage("사용법: /능력 리로드");
            player.sendMessage("사용법: /능력 리스트");
        }
    }

    private void sendAbilityDescription(Player player, String targetName) {
        AbilityBase ability;
        if (targetName == null || targetName.isBlank()) {
            PlayerData data = abilityManager.getPlayerData(player.getUniqueId());
            if (data.getAbilityName() == null) {
                player.sendMessage("현재 능력이 없습니다.");
                return;
            }
            ability = abilityManager.getAbilityByName(data.getAbilityName());
            if (ability == null) {
                player.sendMessage("능력 정보를 찾을 수 없습니다.");
                return;
            }
        } else {
            ability = findAbilityByName(targetName);
            if (ability == null) {
                player.sendMessage("해당 능력을 찾을 수 없습니다.");
                return;
            }
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

    private AbilityBase findAbilityByName(String input) {
        String normalized = input.trim();
        if (normalized.isEmpty()) return null;

        AbilityBase direct = abilityManager.getAbilityByName(normalized.toUpperCase());
        if (direct != null) return direct;

        for (AbilityBase ability : abilityManager.getAllAbilities()) {
            if (ability.getName().equalsIgnoreCase(normalized)) return ability;
            if (ability.getDisplayName().equalsIgnoreCase(normalized)) return ability;
        }
        return null;
    }

    private void sendAbilityList(Player player) {
        player.sendMessage("============ 능력 리스트 ============");
        for (Player target : Bukkit.getOnlinePlayers()) {
            PlayerData data = abilityManager.getPlayerData(target.getUniqueId());
            String abilityKey = data != null ? data.getAbilityName() : null;
            if (abilityKey == null) {
                player.sendMessage(target.getName() + ": (없음)");
                continue;
            }

            AbilityBase ability = abilityManager.getAbilityByName(abilityKey);
            if (ability == null) {
                player.sendMessage(target.getName() + ": " + abilityKey);
                continue;
            }

            player.sendMessage(target.getName() + ": " + ability.getDisplayName()
                    + " (" + ability.getGrade().getLabel() + ")");
        }
        player.sendMessage("================================");
    }

    private void handleOwnerCommand(Player player, String[] args) {
        PlayerData data = abilityManager.getPlayerData(player.getUniqueId());
        if (data.getAbilityName() == null || !data.getAbilityName().equals("PUPPY")) {
            player.sendMessage("강아지 능력만 사용할 수 있습니다.");
            return;
        }

        if (args.length < 2) {
            player.sendMessage("사용법: /능력 주인 <플레이어|해제>");
            return;
        }

        if (args[1].equalsIgnoreCase("해제")) {
            data.setDogOwnerUuid(null);
            abilityManager.savePlayerData(player.getUniqueId());
            player.sendMessage("주인이 해제되었습니다.");
            return;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            player.sendMessage("해당 플레이어가 온라인이 아닙니다.");
            return;
        }

        data.setDogOwnerUuid(target.getUniqueId());
        abilityManager.savePlayerData(player.getUniqueId());
        player.sendMessage("주인이 " + target.getName() + "으로 설정되었습니다.");
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (!(sender instanceof Player)) return List.of();

        if (args.length == 1) {
            return partialMatches(args[0], List.of("설명", "뽑기", "리로드", "리스트", "주인"));
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("설명")) {
            return suggestAbilityNames(args);
        }

        if (sub.equals("뽑기") && args.length == 2) {
            return partialMatches(args[1], List.of("B1", "B2", "C", "A", "S"));
        }

        if (sub.equals("주인") && args.length == 2) {
            List<String> names = new ArrayList<>();
            names.add("해제");
            for (Player p : Bukkit.getOnlinePlayers()) {
                names.add(p.getName());
            }
            return partialMatches(args[1], names);
        }

        return List.of();
    }

    private List<String> suggestAbilityNames(String[] args) {
        Collection<AbilityBase> abilities = abilityManager.getAllAbilities();
        String current = args[args.length - 1];
        Set<String> results = new LinkedHashSet<>();

        if (args.length == 2) {
            for (AbilityBase ability : abilities) {
                String key = ability.getName();
                if (StringUtil.startsWithIgnoreCase(key, current)) results.add(key);

                String display = ability.getDisplayName();
                String[] tokens = display.split("\\s+");
                if (tokens.length > 0 && StringUtil.startsWithIgnoreCase(tokens[0], current)) {
                    results.add(tokens[0]);
                }
                if (tokens.length == 1 && StringUtil.startsWithIgnoreCase(display, current)) {
                    results.add(display);
                }
            }
            return new ArrayList<>(results);
        }

        String[] typed = Arrays.copyOfRange(args, 1, args.length - 1);
        for (AbilityBase ability : abilities) {
            String[] tokens = ability.getDisplayName().split("\\s+");
            if (tokens.length <= typed.length) continue;

            boolean prefixMatch = true;
            for (int i = 0; i < typed.length; i++) {
                if (!tokens[i].equalsIgnoreCase(typed[i])) {
                    prefixMatch = false;
                    break;
                }
            }
            if (!prefixMatch) continue;

            String next = tokens[typed.length];
            if (StringUtil.startsWithIgnoreCase(next, current)) results.add(next);
        }
        return new ArrayList<>(results);
    }

    private List<String> partialMatches(String token, List<String> candidates) {
        List<String> matches = new ArrayList<>();
        StringUtil.copyPartialMatches(token, candidates, matches);
        return matches;
    }
}
