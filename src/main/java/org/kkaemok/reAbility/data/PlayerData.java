package org.kkaemok.reAbility.data;

import java.util.UUID;

public class PlayerData {
    private final UUID uuid;
    private String abilityName; // 현재 능력의 이름 (Key)
    private long expiryTime;    // 능력 만료 시간 (Timestamp)

    // --- [추가] 채굴 제한 시스템 관련 데이터 ---
    private int minedDiamond;
    private long lastDiamondReset;

    private int minedDebris;
    private long lastDebrisReset;

    private UUID dogOwnerUuid;

    public PlayerData(UUID uuid) {
        this.uuid = uuid;
        this.abilityName = null;
        this.expiryTime = 0;

        // 초기화 시 현재 시간으로 리셋 타임 설정
        this.minedDiamond = 0;
        this.lastDiamondReset = System.currentTimeMillis();
        this.minedDebris = 0;
        this.lastDebrisReset = System.currentTimeMillis();
        this.dogOwnerUuid = null;
    }

    public UUID getUuid() { return uuid; }

    public String getAbilityName() { return abilityName; }
    public void setAbilityName(String abilityName) { this.abilityName = abilityName; }

    public long getExpiryTime() { return expiryTime; }
    public void setExpiryTime(long expiryTime) { this.expiryTime = expiryTime; }

    // 만료 여부 확인
    public boolean isExpired() {
        return System.currentTimeMillis() > expiryTime;
    }

    // --- [추가] 다이아몬드 채굴 관련 메서드 ---
    public int getMinedDiamond() { return minedDiamond; }
    public void setMinedDiamond(int minedDiamond) { this.minedDiamond = minedDiamond; }
    public void addMinedDiamond() { this.minedDiamond++; }

    public long getLastDiamondReset() { return lastDiamondReset; }
    public void setLastDiamondReset(long lastDiamondReset) { this.lastDiamondReset = lastDiamondReset; }

    public void resetDiamond() {
        this.minedDiamond = 0;
        this.lastDiamondReset = System.currentTimeMillis();
    }

    // --- [추가] 고대 잔해 채굴 관련 메서드 ---
    public int getMinedDebris() { return minedDebris; }
    public void setMinedDebris(int minedDebris) { this.minedDebris = minedDebris; }
    public void addMinedDebris() { this.minedDebris++; }

    public long getLastDebrisReset() { return lastDebrisReset; }
    public void setLastDebrisReset(long lastDebrisReset) { this.lastDebrisReset = lastDebrisReset; }

    public void resetDebris() {
        this.minedDebris = 0;
        this.lastDebrisReset = System.currentTimeMillis();
    }

    public UUID getDogOwnerUuid() { return dogOwnerUuid; }
    public void setDogOwnerUuid(UUID dogOwnerUuid) { this.dogOwnerUuid = dogOwnerUuid; }
}
