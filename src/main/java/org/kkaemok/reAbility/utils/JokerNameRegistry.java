package org.kkaemok.reAbility.utils;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class JokerNameRegistry {
    private static final Map<UUID, String> fakeByUuid = new ConcurrentHashMap<>();
    private static final Map<String, UUID> uuidByFakeLower = new ConcurrentHashMap<>();

    private JokerNameRegistry() {}

    public static void setFakeName(UUID uuid, String fakeName) {
        if (uuid == null || fakeName == null) return;
        String prev = fakeByUuid.put(uuid, fakeName);
        if (prev != null) {
            uuidByFakeLower.remove(prev.toLowerCase(Locale.ROOT), uuid);
        }
        uuidByFakeLower.put(fakeName.toLowerCase(Locale.ROOT), uuid);
    }

    public static void clearFakeName(UUID uuid) {
        if (uuid == null) return;
        String prev = fakeByUuid.remove(uuid);
        if (prev != null) {
            uuidByFakeLower.remove(prev.toLowerCase(Locale.ROOT), uuid);
        }
    }

    public static boolean isFakeNameTaken(String name) {
        if (name == null) return false;
        return uuidByFakeLower.containsKey(name.toLowerCase(Locale.ROOT));
    }

    public static String getFakeName(UUID uuid) {
        if (uuid == null) return null;
        return fakeByUuid.get(uuid);
    }

    public static Map<UUID, String> snapshot() {
        return new HashMap<>(fakeByUuid);
    }
}
