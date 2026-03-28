package org.kkaemok.reAbility.guild;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.matcher.NodeMatcher;
import net.luckperms.api.node.types.InheritanceNode;
import net.luckperms.api.node.types.SuffixNode;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.kkaemok.reAbility.ReAbility;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class GuildManager {
    private final ReAbility plugin;
    private final LuckPerms lp = LuckPermsProvider.get();

    private File guildFile;
    private FileConfiguration guildConfig;

    private final MiniMessage mm = MiniMessage.miniMessage();
    private final LegacyComponentSerializer legacySerializer = LegacyComponentSerializer.builder()
            .character('&').hexColors().useUnusualXRepeatedCharacterHexFormat().build();

    public final Map<String, GuildData> guilds = new ConcurrentHashMap<>();
    public final Map<UUID, List<String>> pendingRequests = new ConcurrentHashMap<>();
    private final Set<UUID> guildChatMode = ConcurrentHashMap.newKeySet();

    public GuildManager(ReAbility plugin) {
        this.plugin = plugin;
        setupFile();
    }

    private void setupFile() {
        guildFile = new File(plugin.getDataFolder(), "guilds.yml");
        if (!guildFile.exists()) {
            try {
                File folder = plugin.getDataFolder();
                if (!folder.exists() && !folder.mkdirs()) {
                    plugin.getLogger().warning("[ReAbility] 플러그인 폴더를 생성할 수 없습니다.");
                }
                if (guildFile.createNewFile()) {
                    plugin.getLogger().info("[ReAbility] guilds.yml 파일을 생성하였습니다.");
                }
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "[ReAbility] guilds.yml 생성 중 오류 발생", e);
            }
        }
        guildConfig = YamlConfiguration.loadConfiguration(guildFile);
    }

    public void saveGuilds() {
        guildConfig.set("guilds", null);
        for (GuildData data : guilds.values()) {
            String path = "guilds." + data.name;
            guildConfig.set(path + ".master", data.master.toString());
            guildConfig.set(path + ".color", data.color);
            guildConfig.set(path + ".maxMembers", data.maxMembers);
        }
        try {
            guildConfig.save(guildFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "[ReAbility] guilds.yml 저장 중 오류 발생", e);
        }
    }

    public void loadGuilds() {
        ConfigurationSection section = guildConfig.getConfigurationSection("guilds");
        if (section == null) return;

        for (String name : section.getKeys(false)) {
            String path = "guilds." + name;
            String masterStr = guildConfig.getString(path + ".master");
            if (masterStr == null) continue;

            UUID master = UUID.fromString(masterStr);
            String color = guildConfig.getString(path + ".color", "§f");
            int maxMembers = guildConfig.getInt(path + ".maxMembers", 5);
            guilds.put(name, new GuildData(name, master, color, maxMembers));
        }
    }

    public void reloadGuilds() {
        guildConfig = YamlConfiguration.loadConfiguration(guildFile);
        guilds.clear();
        pendingRequests.clear();
        guildChatMode.clear();
        loadGuilds();
    }

    public Component parseColor(String text) {
        if (text == null || text.isEmpty()) return Component.empty();
        if (text.contains("<") && text.contains(">")) {
            try { return mm.deserialize(text); } catch (Exception ignored) {}
        }
        return legacySerializer.deserialize(text);
    }

    public ReAbility getPlugin() { return plugin; }

    public boolean isGuildChatMode(UUID uuid) { return guildChatMode.contains(uuid); }

    public boolean hasGuildName(String guildName) {
        if (guildName == null || guildName.isBlank()) return false;
        return guilds.keySet().stream().anyMatch(name -> name.equalsIgnoreCase(guildName));
    }

    public List<String> getGuildNames() {
        return new ArrayList<>(guilds.keySet());
    }

    public List<String> getPendingRequests(UUID masterUuid) {
        List<String> requests = pendingRequests.get(masterUuid);
        if (requests == null) return List.of();
        synchronized (requests) {
            return new ArrayList<>(requests);
        }
    }

    public void toggleGuildChat(Player player) {
        if (getGuildByMember(player.getUniqueId()) == null) {
            player.sendMessage(parseColor("<red>[!] 가입한 길드가 없어 길드 채팅 모드를 사용할 수 없습니다."));
            guildChatMode.remove(player.getUniqueId());
            return;
        }

        if (guildChatMode.remove(player.getUniqueId())) {
            player.sendMessage(parseColor("<red>[!] 길드 채팅 모드가 비활성화되었습니다."));
        } else {
            guildChatMode.add(player.getUniqueId());
            player.sendMessage(parseColor("<green>[!] 길드 채팅 모드가 활성화되었습니다."));
        }
    }

    public void createGuild(Player owner, String name, String colorName) {
        String guildName = name == null ? "" : name.trim();
        if (guildName.isEmpty()) {
            owner.sendMessage(parseColor("<red>[!] 길드 이름을 입력해 주세요.</red>"));
            return;
        }
        if (getGuildByMember(owner.getUniqueId()) != null) {
            owner.sendMessage(parseColor("<red>[!] 이미 길드에 소속되어 있습니다."));
            return;
        }
        if (hasGuildName(guildName)) {
            owner.sendMessage(parseColor("<red>[!] 이미 존재하는 길드명입니다.</red>"));
            return;
        }

        String colorCode = getColorCode(colorName);
        String groupId = "guild_" + guildName.toLowerCase(Locale.ROOT);

        lp.getGroupManager().createAndLoadGroup(groupId).thenAccept(group -> {
            String suffix = " §f[" + colorCode + guildName + "§f]";
            group.data().clear(n -> n.getType().name().equals("SUFFIX"));
            group.data().add(SuffixNode.builder(suffix, 100).build());
            lp.getGroupManager().saveGroup(group);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (hasGuildName(guildName)) {
                    owner.sendMessage(parseColor("<red>[!] 동일한 길드명이 이미 등록되었습니다.</red>"));
                    return;
                }
                guilds.put(guildName, new GuildData(guildName, owner.getUniqueId(), colorCode, 5));
                saveGuilds();
                addUserToGuild(owner.getUniqueId(), group);
                owner.sendMessage(parseColor("<green>길드 '<white>" + guildName + "</white>' 생성 완료.</green>"));
            });
        }).exceptionally(ex -> {
            plugin.getLogger().log(Level.SEVERE, "[ReAbility] 길드 생성 중 오류 발생", ex);
            Bukkit.getScheduler().runTask(plugin, () ->
                    owner.sendMessage(parseColor("<red>[!] 길드 생성에 실패했습니다.</red>")));
            return null;
        });
    }

    public void acceptJoin(Player master, String requesterName) {
        GuildData guild = getGuildByMaster(master.getUniqueId());
        if (guild == null) {
            master.sendMessage(parseColor("<red>길드장만 수락할 수 있습니다.</red>"));
            return;
        }

        String requestedName = findPendingRequestName(master.getUniqueId(), requesterName);
        if (requestedName == null) {
            master.sendMessage(parseColor("<red>해당 플레이어의 가입 요청이 없습니다.</red>"));
            return;
        }

        OfflinePlayer requester = Bukkit.getOfflinePlayer(requestedName);
        if (isInAnyGuild(requester.getUniqueId())) {
            removePendingRequest(master.getUniqueId(), requestedName);
            master.sendMessage(parseColor("<red>" + requestedName + "님은 이미 다른 길드에 소속되어 있습니다.</red>"));
            return;
        }

        String groupId = "guild_" + guild.name.toLowerCase(Locale.ROOT);
        Group group = lp.getGroupManager().getGroup(groupId);
        if (group == null) {
            master.sendMessage(parseColor("<red>길드 그룹을 찾을 수 없습니다. 관리자에게 문의해 주세요.</red>"));
            return;
        }

        int memberCount = getGuildMemberCount(groupId);
        if (memberCount >= guild.maxMembers) {
            master.sendMessage(parseColor("<red>길드 정원이 가득 찼습니다. /길드 확장 후 다시 시도해 주세요.</red>"));
            return;
        }

        addUserToGuild(requester.getUniqueId(), group);
        master.sendMessage(parseColor("<green>" + requestedName + "님을 길드에 수락했습니다.</green>"));
        Player onlineRequester = requester.getPlayer();
        if (onlineRequester != null) {
            onlineRequester.sendMessage(parseColor("<green>" + guild.name + " 길드에 가입되었습니다!</green>"));
        }
        removePendingRequest(master.getUniqueId(), requestedName);
    }

    public int getGuildMemberLimit(Player player) {
        GuildData guild = getGuildByMember(player.getUniqueId());
        return (guild != null) ? guild.maxMembers : -1;
    }

    public void expandCapacity(Player player) {
        GuildData guild = getGuildByMaster(player.getUniqueId());
        if (guild == null) {
            player.sendMessage(parseColor("<red>[!] 길드장만 인원 확장 가능합니다.</red>"));
            return;
        }
        guild.maxMembers++;
        saveGuilds();
        player.sendMessage(parseColor("<green>길드 최대 인원이 <yellow>" + guild.maxMembers + "</yellow>명으로 확장되었습니다.</green>"));
    }

    public boolean consumeItem(Player p, Material mat, int amount) {
        if (!p.getInventory().containsAtLeast(new ItemStack(mat), amount)) return false;
        p.getInventory().removeItem(new ItemStack(mat, amount));
        return true;
    }

    public GuildData getGuildByMaster(UUID masterUUID) {
        return guilds.values().stream().filter(g -> g.master.equals(masterUUID)).findFirst().orElse(null);
    }

    public GuildData getGuildByMember(UUID uuid) {
        for (GuildData g : guilds.values()) {
            if (g.master.equals(uuid)) return g;
        }
        User user = lp.getUserManager().getUser(uuid);
        if (user != null) {
            String gName = user.getNodes().stream()
                    .filter(n -> n instanceof InheritanceNode)
                    .map(n -> ((InheritanceNode) n).getGroupName())
                    .filter(name -> name.startsWith("guild_"))
                    .findFirst().orElse("");

            if (!gName.isEmpty()) {
                String realName = gName.replace("guild_", "");
                return guilds.values().stream()
                        .filter(g -> g.name.equalsIgnoreCase(realName))
                        .findFirst().orElse(null);
            }
        }
        return null;
    }

    public void requestJoin(Player requester, String guildName) {
        if (getGuildByMember(requester.getUniqueId()) != null) {
            requester.sendMessage(parseColor("<red>[!] 이미 길드에 소속되어 있습니다.</red>"));
            return;
        }

        GuildData guild = findGuildByName(guildName);
        if (guild == null) {
            requester.sendMessage(parseColor("<red>존재하지 않는 길드입니다.</red>"));
            return;
        }

        String groupId = "guild_" + guild.name.toLowerCase(Locale.ROOT);
        int memberCount = getGuildMemberCount(groupId);
        if (memberCount >= guild.maxMembers) {
            requester.sendMessage(parseColor("<red>해당 길드는 정원이 가득 찼습니다.</red>"));
            return;
        }

        List<String> requests = pendingRequests.computeIfAbsent(
                guild.master, k -> Collections.synchronizedList(new ArrayList<>()));
        synchronized (requests) {
            boolean exists = requests.stream().anyMatch(name -> name.equalsIgnoreCase(requester.getName()));
            if (!exists) {
                requests.add(requester.getName());
            }
        }

        Player master = Bukkit.getPlayer(guild.master);
        if (master != null) {
            master.sendMessage(parseColor("<yellow>[!] <white>" + requester.getName()
                    + "</white>의 가입 요청: <gray>/길드 수락 " + requester.getName() + "</gray>"));
        }
        requester.sendMessage(parseColor("<green>가입 요청을 보냈습니다.</green>"));
    }

    public void leaveGuild(Player player) {
        GuildData guild = getGuildByMember(player.getUniqueId());
        if (guild == null) {
            player.sendMessage(parseColor("<red>[!] 길드에 속해있지 않습니다.</red>"));
            return;
        }

        String groupId = "guild_" + guild.name.toLowerCase();
        guildChatMode.remove(player.getUniqueId());

        if (guild.master.equals(player.getUniqueId())) {
            removeUserFromGuild(player.getUniqueId(), groupId);
            guilds.remove(guild.name);
            pendingRequests.remove(player.getUniqueId());
            saveGuilds();

            Group group = lp.getGroupManager().getGroup(groupId);
            if (group != null) {
                lp.getGroupManager().deleteGroup(group);
            }

            player.sendMessage(parseColor("<yellow>[!] 길드장이 탈퇴하여 길드가 삭제되었습니다.</yellow>"));
            return;
        }

        removeUserFromGuild(player.getUniqueId(), groupId);
        player.sendMessage(parseColor("<green>길드에서 탈퇴했습니다.</green>"));
    }

    private void addUserToGuild(UUID uuid, Group group) {
        lp.getUserManager().modifyUser(uuid, user -> user.data().add(InheritanceNode.builder(group).build()));
    }

    private void removeUserFromGuild(UUID uuid, String groupId) {
        lp.getUserManager().modifyUser(uuid, user -> user.data().remove(InheritanceNode.builder(groupId).build()));
    }

    private GuildData findGuildByName(String guildName) {
        if (guildName == null || guildName.isBlank()) return null;
        return guilds.values().stream()
                .filter(g -> g.name.equalsIgnoreCase(guildName))
                .findFirst().orElse(null);
    }

    private String findPendingRequestName(UUID masterUuid, String requesterName) {
        List<String> requests = pendingRequests.get(masterUuid);
        if (requests == null) return null;
        synchronized (requests) {
            return requests.stream()
                    .filter(name -> name.equalsIgnoreCase(requesterName))
                    .findFirst().orElse(null);
        }
    }

    private void removePendingRequest(UUID masterUuid, String requesterName) {
        List<String> requests = pendingRequests.get(masterUuid);
        if (requests == null) return;
        synchronized (requests) {
            requests.removeIf(name -> name.equalsIgnoreCase(requesterName));
            if (requests.isEmpty()) {
                pendingRequests.remove(masterUuid);
            }
        }
    }

    private boolean isInAnyGuild(UUID uuid) {
        if (guilds.values().stream().anyMatch(g -> g.master.equals(uuid))) {
            return true;
        }
        try {
            User user = lp.getUserManager().loadUser(uuid).join();
            return user.getNodes().stream()
                    .filter(node -> node instanceof InheritanceNode)
                    .map(node -> ((InheritanceNode) node).getGroupName())
                    .anyMatch(groupName -> groupName.startsWith("guild_"));
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "[ReAbility] 길드 소속 조회 실패: " + uuid, e);
            return false;
        }
    }

    private int getGuildMemberCount(String groupId) {
        try {
            Map<UUID, Collection<Node>> users = lp.getUserManager()
                    .searchAll(NodeMatcher.key("group." + groupId))
                    .join();
            return users.size();
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "[ReAbility] 길드 인원 수 조회 실패: " + groupId, e);
            return Integer.MAX_VALUE;
        }
    }

    private String getColorCode(String name) {
        return switch (name) {
            case "빨강" -> "§c";
            case "파랑" -> "§9";
            case "분홍" -> "§d";
            case "하늘" -> "§b";
            case "주황" -> "§6";
            case "노랑" -> "§e";
            case "검정" -> "§0";
            case "초록" -> "§2";
            case "연두" -> "§a";
            case "보라" -> "§5";
            default -> "§f";
        };
    }
}
