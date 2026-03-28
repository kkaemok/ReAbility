package org.kkaemok.reAbility.ability;

public enum AbilityGrade {
    D("D", 1),
    C("C", 1),
    B("B", 1),
    A("A", 2),
    S("S", 3),
    S_PLUS("S+", 4),
    SS("SS", 5);

    private final String label;
    private final int durationDays;

    AbilityGrade(String label, int durationDays) {
        this.label = label;
        this.durationDays = durationDays;
    }

    public String getLabel() {
        return label;
    }

    public long getDurationInMillis() {
        return (long) durationDays * 24 * 60 * 60 * 1000;
    }
}
