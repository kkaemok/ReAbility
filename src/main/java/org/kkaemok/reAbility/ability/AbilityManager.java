package org.kkaemok.reAbility.ability;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
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

    private final ReAbility plugin;
    private final Map<UUID, PlayerData> playerDataMap = new HashMap<>();
    private final Map<String, AbilityBase> registeredAbilities = new HashMap<>();
    private final Random random = new Random();

    private File playerFile;
    private FileConfiguration playerConfig;

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
                data.setAbilityName(playerConfig.getString(path + "ability"));
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

                playerDataMap.put(uuid, data);
            } catch (IllegalArgumentException ignored) {}
        }
    }

    public void reloadPlayerData() {
        playerConfig = YamlConfiguration.loadConfiguration(playerFile);
        playerDataMap.clear();
        loadPlayerData();
    }

    private void registerAbilities() {
        register(new Eater(plugin));
        register(new Lighter());
        register(new HomeLover());
        register(new ShoddyWizard(plugin, plugin.getGuildManager()));
        register(new Fisherman(plugin));
        register(new SoloLeveler(plugin));
        register(new Puppy(plugin));
        register(new Lovebird(plugin, plugin.getGuildManager()));
        register(new Zombie());
        register(new Bomber(plugin));
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
        register(new Chronos(plugin));
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
        long duration = ability.getGrade().getDurationInMillis();
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
    }

    public void handleJoin(Player player) {
        UUID uuid = player.getUniqueId();
        PlayerData data = getPlayerData(uuid);

        if (data.getAbilityName() == null || data.isExpired()) {
            Map<AbilityGrade, Double> dailyWeights = new EnumMap<>(AbilityGrade.class);
            dailyWeights.put(AbilityGrade.D, 50.0);
            dailyWeights.put(AbilityGrade.C, 30.0);
            dailyWeights.put(AbilityGrade.B, 15.0);
            dailyWeights.put(AbilityGrade.A, 4.0);
            dailyWeights.put(AbilityGrade.S, 0.9);
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
                player.sendMessage(Component.text("오늘은 " + current.getDisplayName() + " 능력을 획득하였습니다!",
                        NamedTextColor.GREEN));
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
            }
        } else {
            AbilityBase current = registeredAbilities.get(data.getAbilityName());
            if (current != null) {
                current.onActivate(player);
                if (isHighGrade(current.getGrade())) {
                    applyHighGradeBuffs(player);
                }
                long timeLeftHours = (data.getExpiryTime() - System.currentTimeMillis()) / (1000 * 60 * 60);
                player.sendMessage(Component.text("현재 능력: " + current.getDisplayName()
                        + " (남은 시간: " + timeLeftHours + "시간)", NamedTextColor.YELLOW));
            }
        }
    }

    private boolean isHighGrade(AbilityGrade grade) {
        return grade == AbilityGrade.S || grade == AbilityGrade.SS;
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

        String path = "players." + uuid + ".";
        playerConfig.set(path + "ability", data.getAbilityName());
        playerConfig.set(path + "expiry", data.getExpiryTime());
        playerConfig.set(path + "mined-diamond", data.getMinedDiamond());
        playerConfig.set(path + "last-diamond-reset", data.getLastDiamondReset());
        playerConfig.set(path + "mined-debris", data.getMinedDebris());
        playerConfig.set(path + "last-debris-reset", data.getLastDebrisReset());
        playerConfig.set(path + "dog-owner", data.getDogOwnerUuid() != null ? data.getDogOwnerUuid().toString() : null);

        try {
            playerConfig.save(playerFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "[ReAbility] playerdata.yml 저장 중 오류 발생", e);
        }
    }

    public void saveAll() {
        for (UUID uuid : playerDataMap.keySet()) {
            savePlayerData(uuid);
        }
    }
}
