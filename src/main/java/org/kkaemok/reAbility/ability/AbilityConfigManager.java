package org.kkaemok.reAbility.ability;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.kkaemok.reAbility.ReAbility;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

public class AbilityConfigManager {
    private final ReAbility plugin;
    private final File folder;
    private final Map<String, FileConfiguration> configs = new HashMap<>();
    private final Map<String, File> files = new HashMap<>();

    public AbilityConfigManager(ReAbility plugin) {
        this.plugin = plugin;
        this.folder = new File(plugin.getDataFolder(), "abilities");
        if (!folder.exists() && !folder.mkdirs()) {
            plugin.getLogger().warning("[ReAbility] abilities 폴더 생성 실패");
        }
    }

    public void ensureAbilityFiles(Collection<AbilityBase> abilities) {
        for (AbilityBase ability : abilities) {
            String key = normalize(ability.getName());
            FileConfiguration config = getConfig(key);
            boolean changed = false;
            if (!config.contains("info.display-name")) {
                config.set("info.display-name", ability.getDisplayName());
                changed = true;
            }
            if (!config.contains("info.grade")) {
                config.set("info.grade", ability.getGrade().name());
                changed = true;
            }
            if (!config.contains("info.show-in-list")) {
                config.set("info.show-in-list", ability.isDefaultVisibleInList());
                changed = true;
            }
            if (!config.contains("info.show-in-description")) {
                config.set("info.show-in-description", ability.isDefaultVisibleInDescription());
                changed = true;
            }
            if (!config.contains("info.random-assignable")) {
                config.set("info.random-assignable", ability.isDefaultRandomAssignable());
                changed = true;
            }
            if (changed) {
                save(key);
            }
        }
    }

    public void reloadAll() {
        configs.clear();
        files.clear();
        if (!folder.exists()) return;

        File[] list = folder.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (list == null) return;

        for (File file : list) {
            String name = file.getName();
            String key = name.substring(0, name.length() - 4).toUpperCase(Locale.ROOT);
            files.put(key, file);
            configs.put(key, YamlConfiguration.loadConfiguration(file));
        }
    }

    public int getInt(String abilityName, String path, int def) {
        FileConfiguration config = getConfig(abilityName);
        if (!config.contains(path)) {
            config.set(path, def);
            save(abilityName);
        }
        return config.getInt(path, def);
    }

    public long getLong(String abilityName, String path, long def) {
        FileConfiguration config = getConfig(abilityName);
        if (!config.contains(path)) {
            config.set(path, def);
            save(abilityName);
        }
        return config.getLong(path, def);
    }

    public double getDouble(String abilityName, String path, double def) {
        FileConfiguration config = getConfig(abilityName);
        if (!config.contains(path)) {
            config.set(path, def);
            save(abilityName);
        }
        return config.getDouble(path, def);
    }

    public boolean getBoolean(String abilityName, String path, boolean def) {
        FileConfiguration config = getConfig(abilityName);
        if (!config.contains(path)) {
            config.set(path, def);
            save(abilityName);
        }
        return config.getBoolean(path, def);
    }

    public void setBoolean(String abilityName, String path, boolean value) {
        FileConfiguration config = getConfig(abilityName);
        config.set(path, value);
        save(abilityName);
    }

    public String getString(String abilityName, String path, String def) {
        FileConfiguration config = getConfig(abilityName);
        if (!config.contains(path)) {
            config.set(path, def);
            save(abilityName);
        }
        return config.getString(path, def);
    }

    public Material getMaterial(String abilityName, String path, Material def) {
        FileConfiguration config = getConfig(abilityName);
        String raw = config.getString(path);
        if (raw == null || raw.isBlank()) {
            config.set(path, def != null ? def.name() : null);
            save(abilityName);
            return def;
        }
        Material material = Material.matchMaterial(raw);
        if (material == null) {
            return def;
        }
        return material;
    }

    public SkillCost getSkillCost(String abilityName, String skillKey, Material item, int amount) {
        return getSkillCost(abilityName, skillKey, item, amount, Material.AIR, 0);
    }

    public SkillCost getSkillCost(String abilityName, String skillKey, Material item, int amount,
                                  Material extraItem, int extraAmount) {
        String base = "skills." + skillKey + ".cost.";
        Material costItem = getMaterial(abilityName, base + "item", item);
        int costAmount = getInt(abilityName, base + "amount", amount);
        Material extraMat = getMaterial(abilityName, base + "extra-item", extraItem);
        int extraAmt = getInt(abilityName, base + "extra-amount", extraAmount);
        return new SkillCost(costItem, costAmount, extraMat, extraAmt);
    }

    private FileConfiguration getConfig(String abilityName) {
        String key = normalize(abilityName);
        FileConfiguration config = configs.get(key);
        if (config != null) return config;

        File file = files.get(key);
        if (file == null) {
            file = new File(folder, key + ".yml");
            if (!file.exists()) {
                try {
                    if (file.createNewFile()) {
                        plugin.getLogger().info("[ReAbility] abilities/" + file.getName() + " 생성");
                    }
                } catch (IOException e) {
                    plugin.getLogger().log(Level.SEVERE, "[ReAbility] abilities 설정 파일 생성 실패", e);
                }
            }
            files.put(key, file);
        }
        config = YamlConfiguration.loadConfiguration(file);
        configs.put(key, config);
        return config;
    }

    private void save(String abilityName) {
        String key = normalize(abilityName);
        FileConfiguration config = configs.get(key);
        File file = files.get(key);
        if (config == null || file == null) return;
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "[ReAbility] abilities 설정 저장 실패", e);
        }
    }

    private String normalize(String name) {
        return name == null ? "" : name.toUpperCase(Locale.ROOT);
    }
}
