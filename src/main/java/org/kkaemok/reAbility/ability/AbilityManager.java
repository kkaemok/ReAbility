package org.kkaemok.reAbility.ability;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.kkaemok.reAbility.ReAbility;
import org.kkaemok.reAbility.ability.list.*;
import org.kkaemok.reAbility.data.PlayerData;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;

public class AbilityManager {

    private static final long SAVE_DEBOUNCE_TICKS = 20L;

    private final ReAbility plugin;
    private final Map<UUID, PlayerData> playerDataMap = new HashMap<>();
    private final Map<String, AbilityBase> registeredAbilities = new HashMap<>();
    private final Random random = new Random();
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    private File playerFile;
    private FileConfiguration playerConfig;
    private boolean saveScheduled;

    public AbilityManager(ReAbility plugin) {
        this.plugin = plugin;
        setupFile();
        registerAbilities();
    }

    private void setupFile() {
        playerFile = new File(plugin.getDataFolder(), "playerdata.yml");
        if (!playerFile.exists()) {
            try {
                if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
                    plugin.getLogger().warning("[ReAbility] 플러그인 폴더를 생성할 수 없습니다.");
                }
                if (playerFile.createNewFile()) {
                    plugin.getLogger().info("[ReAbility] playerdata.yml 파일을 생성하였습니다.");
                }
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "[ReAbility] playerdata.yml 생성 중 오류 발생", e);
            }
        }
        playerConfig = YamlConfiguration.loadConfiguration(playerFile);
    }

    public void loadPlayerData() {
        ConfigurationSection section = playerConfig.getConfigurationSection("players");
        if (section == null) return;

        for (String uuidStr : section.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(uuidStr);
                PlayerData data = new PlayerData(uuid);

                String path = "players." + uuidStr + ".";
                String savedAbility = playerConfig.getString(path + "ability");
                if ("SPLUS_UNKNOWN".equals(savedAbility)) {
                    savedAbility = "NECROMANCER";
                }
                data.setAbilityName(savedAbility);
                data.setExpiryTime(playerConfig.getLong(path + "expiry"));

                data.setMinedDiamond(playerConfig.getInt(path + "mined-diamond", 0));
                data.setLastDiamondReset(playerConfig.getLong(path + "last-diamond-reset", System.currentTimeMillis()));
                data.setMinedDebris(playerConfig.getInt(path + "mined-debris", 0));
                data.setLastDebrisReset(playerConfig.getLong(path + "last-debris-reset", System.currentTimeMillis()));

                String dogOwnerStr = playerConfig.getString(path + "dog-owner");
                if (dogOwnerStr != null && !dogOwnerStr.isEmpty()) {
                    try {
                        data.setDogOwnerUuid(UUID.fromString(dogOwnerStr));
                    } catch (IllegalArgumentException ignored) {
                        data.setDogOwnerUuid(null);
                    }
                }
                data.setDomainHome(playerConfig.getString(path + "domain-home"));
                int legacySoulCount = playerConfig.getInt(path + "souls", 0);
                data.setSoulCount(legacySoulCount);
                List<Long> soulExpiries = playerConfig.getLongList(path + "soul-expiries");
                if (soulExpiries.isEmpty() && legacySoulCount > 0) {
                    long migratedExpiry = System.currentTimeMillis() + (60L * 60L * 1000L);
                    for (int i = 0; i < legacySoulCount; i++) {
                        soulExpiries.add(migratedExpiry);
                    }
                }
                data.setSoulExpiries(soulExpiries);
                ConfigurationSection investmentSection = playerConfig.getConfigurationSection(path + "soul-investments");
                if (investmentSection != null) {
                    Map<String, Integer> loadedInvestments = new HashMap<>();
                    for (String key : investmentSection.getKeys(false)) {
                        loadedInvestments.put(key, investmentSection.getInt(key, 0));
                    }
                    data.setSoulInvestments(loadedInvestments);
                }
                data.setCoins(playerConfig.getLong(path + "coins", 0L));
                if (playerConfig.contains(path + "playtime-hours-rewarded")) {
                    data.setPlaytimeHoursRewarded(playerConfig.getLong(path + "playtime-hours-rewarded"));
                } else {
                    data.setPlaytimeHoursRewarded(-1L);
                }

                playerDataMap.put(uuid, data);
            } catch (IllegalArgumentException ignored) {}
        }
    }

    public void reloadPlayerData() {
        playerConfig = YamlConfiguration.loadConfiguration(playerFile);
        playerDataMap.clear();
        saveScheduled = false;
        loadPlayerData();
    }

    private void registerAbilities() {
        register(new Eater(plugin));
        register(new Lighter());
        register(new HomeLover());
        register(new Husk(plugin));
        register(new Disruptor(plugin));
        register(new MichaelJack(plugin));
        register(new ShoddyWizard(plugin, plugin.getGuildManager()));
        register(new FashionLeader(plugin));
        register(new Fisherman(plugin));
        register(new SoloLeveler(plugin));
        register(new Puppy(plugin));
        register(new Lovebird(plugin, plugin.getGuildManager()));
        register(new Zombie());
        register(new Bomber(plugin));
        register(new TreasureAppraiser(plugin));
        register(new GanggangYakyak(plugin));
        register(new Bulldozer(plugin));
        register(new Phoenix(plugin));
        register(new Gunslinger(plugin));
        register(new Fighter(plugin));
        register(new Elementer(plugin, plugin.getGuildManager()));
        register(new Counter(plugin, plugin.getGuildManager()));
        register(new Miner(plugin));
        register(new Werewolf(plugin));
        register(new Ghost(plugin));
        register(new Joker(plugin));
        register(new SpaceRuler(plugin));
        register(new Beauty(plugin));
        register(new Demon(plugin));
        register(new OnlySword(plugin));
        register(new PhoenixII(plugin));
        register(new DomainCaster(plugin));
        register(new Chef(plugin));
        register(new Valkyrie(plugin));
        register(new Chronos(plugin));
        register(new Archangel(plugin));
        register(new Necromancer(plugin));
    }

    @SuppressWarnings("unused")
    private void register(AbilityBase ability) {
        registeredAbilities.put(ability.getName(), ability);
        Bukkit.getPluginManager().registerEvents(ability, plugin);
    }

    public AbilityGrade rollGrade(RerollTicket ticket) {
        double chance = random.nextDouble() * 100;
        double cumulative = 0.0;

        for (Map.Entry<AbilityGrade, Double> entry : ticket.getWeights().entrySet()) {
            cumulative += entry.getValue();
            if (chance <= cumulative) {
                return entry.getKey();
            }
        }
        return AbilityGrade.B;
    }

    public void useRerollTicket(Player player, RerollTicket ticket) {
        AbilityGrade rolledGrade = rollGrade(ticket);
        assignAbilityByGrade(player, rolledGrade);
    }

    public void assignAbilityByGrade(Player player, AbilityGrade grade) {
        List<AbilityBase> possibleAbilities = registeredAbilities.values().stream()
                .filter(a -> a.getGrade() == grade)
                .toList();

        if (possibleAbilities.isEmpty()) {
            player.sendMessage(Component.text("해당 등급(" + grade.getLabel() + ")에 등록된 능력이 없습니다!",
                    NamedTextColor.RED));
            return;
        }

        AbilityBase picked = possibleAbilities.get(random.nextInt(possibleAbilities.size()));
        applyAbility(player, picked);
    }

    private void applyAbility(Player player, AbilityBase ability) {
        PlayerData data = getPlayerData(player.getUniqueId());

        if (data.getAbilityName() != null) {
            AbilityBase old = registeredAbilities.get(data.getAbilityName());
            if (old != null) {
                old.onDeactivate(player);
                if (isHighGrade(old.getGrade())) {
                    removeHighGradeBuffs(player);
                }
            }
        }

        data.setAbilityName(ability.getName());
        long duration = getDurationMillis(ability.getGrade());
        data.setExpiryTime(System.currentTimeMillis() + duration);

        ability.onAcquire(player);
        ability.onActivate(player);
        if (isHighGrade(ability.getGrade())) {
            applyHighGradeBuffs(player);
        }

        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("============", NamedTextColor.GOLD));
        player.sendMessage(Component.text("  새로운 능력을 획득하였습니다!", NamedTextColor.YELLOW));
        player.sendMessage(Component.text("  등급: ").color(NamedTextColor.WHITE)
                .append(Component.text(ability.getGrade().getLabel()).decorate(TextDecoration.BOLD)));
        player.sendMessage(Component.text("  능력: ").color(NamedTextColor.WHITE)
                .append(Component.text(ability.getDisplayName(), NamedTextColor.AQUA)));
        player.sendMessage(Component.text("============", NamedTextColor.GOLD));

        savePlayerData(player.getUniqueId());
    }

    public boolean assignAbility(Player player, AbilityBase ability) {
        if (ability == null) return false;
        applyAbility(player, ability);
        return true;
    }

    public void handleJoin(Player player) {
        UUID uuid = player.getUniqueId();
        PlayerData data = getPlayerData(uuid);

        if (data.getAbilityName() == null || data.isExpired()) {
            Map<AbilityGrade, Double> dailyWeights = new EnumMap<>(AbilityGrade.class);
            dailyWeights.put(AbilityGrade.D, 45.0);
            dailyWeights.put(AbilityGrade.C, 40.0);
            dailyWeights.put(AbilityGrade.B, 9.0);
            dailyWeights.put(AbilityGrade.A, 5.0);
            dailyWeights.put(AbilityGrade.S, 0.86);
            dailyWeights.put(AbilityGrade.S_PLUS, 0.04);
            dailyWeights.put(AbilityGrade.SS, 0.1);

            double chance = random.nextDouble() * 100;
            double cumulative = 0.0;
            AbilityGrade rolledGrade = AbilityGrade.D;

            for (Map.Entry<AbilityGrade, Double> entry : dailyWeights.entrySet()) {
                cumulative += entry.getValue();
                if (chance <= cumulative) {
                    rolledGrade = entry.getKey();
                    break;
                }
            }

            assignAbilityByGrade(player, rolledGrade);
            AbilityBase current = registeredAbilities.get(getPlayerData(uuid).getAbilityName());
            if (current != null) {
                sendRerollMessage(player, current);
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
            }
        } else {
            AbilityBase current = registeredAbilities.get(data.getAbilityName());
            if (current != null) {
                current.onActivate(player);
                if (isHighGrade(current.getGrade())) {
                    applyHighGradeBuffs(player);
                }
                player.sendMessage(Component.text("남은 능력 시간: "
                        + formatRemainingAbilityTime(data.getExpiryTime()), NamedTextColor.YELLOW));
                long timeLeftHours = (data.getExpiryTime() - System.currentTimeMillis()) / (1000 * 60 * 60);
                player.sendMessage(Component.text("현재 능력: " + current.getDisplayName()
                        + " (남은 시간: " + timeLeftHours + "시간)", NamedTextColor.YELLOW));
            }
        }
    }

    public String formatRemainingAbilityTime(long expiryTime) {
        if (expiryTime == Long.MAX_VALUE) {
            return "영구";
        }

        long remainMs = Math.max(0L, expiryTime - System.currentTimeMillis());
        long totalMinutes = remainMs / 60000L;
        long hours = totalMinutes / 60L;
        long minutes = totalMinutes % 60L;
        return hours + "시간 " + minutes + "분";
    }

    private long getDurationMillis(AbilityGrade grade) {
        String key = "ability-settings.durations." + grade.name();
        return plugin.getConfig().getLong(key, grade.getDurationInMillis());
    }

    private void sendRerollMessage(Player player, AbilityBase ability) {
        String prefix = plugin.getConfig().getString("messages.prefix", "");
        String template = plugin.getConfig().getString("messages.reroll-announce",
                "<yellow>오늘은 <white>%ability% <yellow>능력이 지급되었습니다");
        String raw = (prefix == null ? "" : prefix) + (template == null ? "" : template);
        String message = raw.replace("%ability%", ability.getDisplayName());
        player.sendMessage(miniMessage.deserialize(message));
    }

    private boolean isHighGrade(AbilityGrade grade) {
        return grade == AbilityGrade.S || grade == AbilityGrade.S_PLUS || grade == AbilityGrade.SS;
    }

    private void applyHighGradeBuffs(Player player) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, PotionEffect.INFINITE_DURATION, 0, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, PotionEffect.INFINITE_DURATION, 0, false, false));
    }

    private void removeHighGradeBuffs(Player player) {
        player.removePotionEffect(PotionEffectType.RESISTANCE);
        player.removePotionEffect(PotionEffectType.REGENERATION);
    }

    public PlayerData getPlayerData(UUID uuid) {
        return playerDataMap.computeIfAbsent(uuid, PlayerData::new);
    }

    public AbilityBase getAbilityByName(String name) {
        return registeredAbilities.get(name);
    }

    public Collection<AbilityBase> getAllAbilities() {
        return Collections.unmodifiableCollection(registeredAbilities.values());
    }

    public void savePlayerData(UUID uuid) {
        PlayerData data = playerDataMap.get(uuid);
        if (data == null) return;

        writePlayerData(uuid, data);
        scheduleSave();
    }

    public void saveAll() {
        for (Map.Entry<UUID, PlayerData> entry : playerDataMap.entrySet()) {
            writePlayerData(entry.getKey(), entry.getValue());
        }
        flushPlayerConfig();
        saveScheduled = false;
    }

    private void writePlayerData(UUID uuid, PlayerData data) {
        String path = "players." + uuid + ".";
        playerConfig.set(path + "ability", data.getAbilityName());
        playerConfig.set(path + "expiry", data.getExpiryTime());
        playerConfig.set(path + "mined-diamond", data.getMinedDiamond());
        playerConfig.set(path + "last-diamond-reset", data.getLastDiamondReset());
        playerConfig.set(path + "mined-debris", data.getMinedDebris());
        playerConfig.set(path + "last-debris-reset", data.getLastDebrisReset());
        playerConfig.set(path + "dog-owner", data.getDogOwnerUuid() != null ? data.getDogOwnerUuid().toString() : null);
        playerConfig.set(path + "domain-home", data.getDomainHome());
        data.purgeExpiredSouls();
        playerConfig.set(path + "souls", data.getSoulCount());
        playerConfig.set(path + "soul-expiries", data.getSoulExpiries());
        String investmentPath = path + "soul-investments";
        playerConfig.set(investmentPath, null);
        for (Map.Entry<String, Integer> entry : data.getSoulInvestments().entrySet()) {
            playerConfig.set(investmentPath + "." + entry.getKey(), entry.getValue());
        }
        playerConfig.set(path + "coins", data.getCoins());
        playerConfig.set(path + "playtime-hours-rewarded", data.getPlaytimeHoursRewarded());
    }

    private void scheduleSave() {
        if (saveScheduled) return;
        saveScheduled = true;

        if (!plugin.isEnabled()) {
            saveScheduled = false;
            flushPlayerConfig();
            return;
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            saveScheduled = false;
            flushPlayerConfig();
        }, SAVE_DEBOUNCE_TICKS);
    }

    private void flushPlayerConfig() {
        try {
            playerConfig.save(playerFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "[ReAbility] playerdata.yml 저장 중 오류 발생", e);
        }
    }
}
