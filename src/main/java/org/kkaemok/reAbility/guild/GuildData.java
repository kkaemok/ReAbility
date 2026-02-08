package org.kkaemok.reAbility.guild;
import java.util.UUID;
public class GuildData {
    public String name;
    public UUID master;
    public String color;
    public int maxMembers;
    public GuildData(String name, UUID master, String color, int maxMembers) {
        this.name = name; this.master = master; this.color = color; this.maxMembers = maxMembers;
    }
}