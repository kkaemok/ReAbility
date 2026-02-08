package org.kkaemok.reAbility.ability;

import java.util.LinkedHashMap;
import java.util.Map;

public enum RerollTicket {
    TICKET_B1("B급 이상 랜덤 리롤권 I", new LinkedHashMap<>() {{
        put(AbilityGrade.B, 60.0);
        put(AbilityGrade.A, 35.0);
        put(AbilityGrade.S, 4.5);
        put(AbilityGrade.SS, 0.5);
    }}),
    TICKET_B2("B급 이상 랜덤 리롤권 II", new LinkedHashMap<>() {{
        put(AbilityGrade.B, 45.0);
        put(AbilityGrade.A, 48.0);
        put(AbilityGrade.S, 6.3);
        put(AbilityGrade.SS, 0.7);
    }}),
    TICKET_A("A급 이상 랜덤 리롤권", new LinkedHashMap<>() {{
        put(AbilityGrade.A, 80.0);
        put(AbilityGrade.S, 18.0);
        put(AbilityGrade.SS, 2.0);
    }}),
    TICKET_S("S급 이상 랜덤 리롤권", new LinkedHashMap<>() {{
        put(AbilityGrade.S, 90.0);
        put(AbilityGrade.SS, 10.0);
    }});

    private final String name;
    private final Map<AbilityGrade, Double> weights;

    RerollTicket(String name, Map<AbilityGrade, Double> weights) {
        this.name = name;
        this.weights = weights;
    }

    public String getName() { return name; }
    public Map<AbilityGrade, Double> getWeights() { return weights; }
}