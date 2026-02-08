package org.kkaemok.reAbility.ability;

public enum AbilityGrade {
    D("D", 1),
    C("C", 1),
    B("B", 1),
    A("A", 2),   // 2일
    S("S", 3),   // 3일
    SS("SS", 5); // 5일

    private final String label;
    private final int durationDays;

    AbilityGrade(String label, int durationDays) {
        this.label = label;
        this.durationDays = durationDays;
    }

    public String getLabel() {
        return label;
    }

    // 밀리초 단위로 기간 반환
    public long getDurationInMillis() {
        return (long) durationDays * 24 * 60 * 60 * 1000;
    }
}