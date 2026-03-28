package org.kkaemok.reAbility.data;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private String domainHome;
    private int soulCount;
    private final ArrayDeque<Long> soulExpiries;
    private final Map<String, Integer> soulInvestments;
    private long coins;
    private long playtimeHoursRewarded;

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
        this.domainHome = null;
        this.soulCount = 0;
        this.soulExpiries = new ArrayDeque<>();
        this.soulInvestments = new HashMap<>();
        this.coins = 0L;
        this.playtimeHoursRewarded = -1L;
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

    public String getDomainHome() { return domainHome; }
    public void setDomainHome(String domainHome) { this.domainHome = domainHome; }

    public int getSoulCount() { return soulCount; }
    public void setSoulCount(int soulCount) { this.soulCount = Math.max(0, soulCount); }
    public void addSoul(int amount) { this.soulCount += amount; }

    public int getAvailableSouls() {
        purgeExpiredSouls();
        return soulCount;
    }

    public List<Long> getSoulExpiries() {
        return new ArrayList<>(soulExpiries);
    }

    public void setSoulExpiries(List<Long> expiries) {
        this.soulExpiries.clear();
        if (expiries != null && !expiries.isEmpty()) {
            List<Long> sorted = new ArrayList<>(expiries);
            sorted.removeIf(ts -> ts == null || ts <= 0L);
            sorted.sort(Long::compareTo);
            this.soulExpiries.addAll(sorted);
        }
        purgeExpiredSouls();
    }

    public void addExpiringSouls(int amount, long ttlMs) {
        if (amount <= 0) return;
        purgeExpiredSouls();
        long expiresAt = System.currentTimeMillis() + Math.max(1000L, ttlMs);
        for (int i = 0; i < amount; i++) {
            soulExpiries.addLast(expiresAt);
        }
        this.soulCount = soulExpiries.size();
    }

    public int consumeSouls(int amount) {
        if (amount <= 0) return 0;
        purgeExpiredSouls();
        int consumed = Math.min(amount, soulExpiries.size());
        for (int i = 0; i < consumed; i++) {
            soulExpiries.pollFirst();
        }
        this.soulCount = soulExpiries.size();
        return consumed;
    }

    public int consumeAllSouls() {
        purgeExpiredSouls();
        int consumed = soulExpiries.size();
        soulExpiries.clear();
        soulCount = 0;
        return consumed;
    }

    public int purgeExpiredSouls() {
        long now = System.currentTimeMillis();
        int removed = 0;
        while (!soulExpiries.isEmpty() && soulExpiries.peekFirst() <= now) {
            soulExpiries.pollFirst();
            removed++;
        }
        soulCount = soulExpiries.size();
        return removed;
    }

    public Map<String, Integer> getSoulInvestments() {
        return Collections.unmodifiableMap(soulInvestments);
    }

    public void setSoulInvestments(Map<String, Integer> investments) {
        soulInvestments.clear();
        if (investments == null || investments.isEmpty()) return;
        for (Map.Entry<String, Integer> entry : investments.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank()) continue;
            soulInvestments.put(entry.getKey(), Math.max(0, entry.getValue() == null ? 0 : entry.getValue()));
        }
    }

    public int getSoulInvestment(String skillKey) {
        if (skillKey == null) return 0;
        return soulInvestments.getOrDefault(skillKey, 0);
    }

    public int addSoulInvestment(String skillKey, int amount) {
        if (skillKey == null || skillKey.isBlank() || amount <= 0) {
            return getSoulInvestment(skillKey);
        }
        int value = getSoulInvestment(skillKey) + amount;
        soulInvestments.put(skillKey, value);
        return value;
    }

    public void setSoulInvestment(String skillKey, int value) {
        if (skillKey == null || skillKey.isBlank()) return;
        soulInvestments.put(skillKey, Math.max(0, value));
    }

    public long getCoins() { return coins; }
    public void setCoins(long coins) { this.coins = Math.max(0L, coins); }
    public void addCoins(long amount) { this.coins = Math.max(0L, this.coins + amount); }

    public long getPlaytimeHoursRewarded() { return playtimeHoursRewarded; }
    public void setPlaytimeHoursRewarded(long playtimeHoursRewarded) { this.playtimeHoursRewarded = playtimeHoursRewarded; }
}
