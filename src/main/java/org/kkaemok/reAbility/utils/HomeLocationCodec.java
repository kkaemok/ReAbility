package org.kkaemok.reAbility.utils;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

public final class HomeLocationCodec {
    private HomeLocationCodec() {}

    public static String serialize(Location location) {
        if (location == null || location.getWorld() == null) return null;
        return location.getWorld().getName() + ","
                + location.getX() + ","
                + location.getY() + ","
                + location.getZ() + ","
                + location.getYaw() + ","
                + location.getPitch();
    }

    public static Location deserialize(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            String[] parts = raw.split(",");
            if (parts.length < 4) return null;

            World world = Bukkit.getWorld(parts[0]);
            if (world == null) return null;

            double x = Double.parseDouble(parts[1]);
            double y = Double.parseDouble(parts[2]);
            double z = Double.parseDouble(parts[3]);
            float yaw = parts.length >= 5 ? Float.parseFloat(parts[4]) : 0f;
            float pitch = parts.length >= 6 ? Float.parseFloat(parts[5]) : 0f;
            return new Location(world, x, y, z, yaw, pitch);
        } catch (Exception ignored) {
            return null;
        }
    }
}
