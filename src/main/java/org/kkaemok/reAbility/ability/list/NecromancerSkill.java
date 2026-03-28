package org.kkaemok.reAbility.ability.list;

import org.bukkit.Material;

import java.util.Locale;

public enum NecromancerSkill {
    SOUL_HARVEST("soul_harvest", "영혼 수확", 50, Material.SOUL_LANTERN),
    DEATH_KNIGHT("death_knight", "소울 나이트", 100, Material.NETHERITE_INGOT),
    DEATH_INSTINCT("death_instinct", "죽음의 본능", 300, Material.DIAMOND_BLOCK),
    SCULK_SOUL("sculk_soul", "스컬크 소울", 1000, Material.SCULK_CATALYST),
    DEATH_FLOWER("death_flower", "죽음의 꽃", 2000, Material.WITHER_ROSE),
    END_OF_WORLD("end_of_world", "세계의 종말", 10000, Material.CRYING_OBSIDIAN);

    private final String key;
    private final String displayName;
    private final int unlockCost;
    private final Material icon;

    NecromancerSkill(String key, String displayName, int unlockCost, Material icon) {
        this.key = key;
        this.displayName = displayName;
        this.unlockCost = unlockCost;
        this.icon = icon;
    }

    public String getKey() {
        return key;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getUnlockCost() {
        return unlockCost;
    }

    public Material getIcon() {
        return icon;
    }

    public static NecromancerSkill fromKey(String key) {
        if (key == null || key.isBlank()) return null;
        String normalized = key.toLowerCase(Locale.ROOT);
        for (NecromancerSkill skill : values()) {
            if (skill.key.equals(normalized)) return skill;
        }
        return null;
    }
}
