package org.kkaemok.reAbility.integration;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.kkaemok.reAbility.utils.JokerNameRegistry;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class NicknamesBridge {
    private static final String[] NICKNAME_SETTER_METHODS = {
            "setNickname", "setNickName", "setNick", "applyNickname", "updateNickname", "setPlayerNickname"
    };
    private static final String[] NICKNAME_CLEAR_METHODS = {
            "clearNickname", "removeNickname", "resetNickname", "clearNickName", "removeNick", "resetNick",
            "clearPlayerNickname"
    };

    private static volatile Object api;
    private static volatile Method findOnlinePlayerMethod;
    private static volatile Method buildDisplayMethod;
    private static volatile Method refreshPlayerMethod;
    private static volatile Method setNicknameByPlayerMethod;
    private static volatile Method setNicknameByUuidMethod;
    private static volatile Method clearNicknameByPlayerMethod;
    private static volatile Method clearNicknameByUuidMethod;
    private static final Set<UUID> jokerManagedByNicknames = ConcurrentHashMap.newKeySet();

    private NicknamesBridge() {}

    public static void initialize(JavaPlugin plugin) {
        try {
            if (Bukkit.getPluginManager().getPlugin("nicknames") == null) {
                clear();
                return;
            }

            Class<?> providerClass = Class.forName("org.kkaemok.nicknames.api.NicknamesApiProvider");
            Method getMethod = providerClass.getMethod("get");
            Object optionalObj = getMethod.invoke(null);
            if (!(optionalObj instanceof Optional<?> optional) || optional.isEmpty()) {
                clear();
                return;
            }

            Object resolvedApi = optional.get();
            Class<?> apiClass = resolvedApi.getClass().getInterfaces().length > 0
                    ? resolvedApi.getClass().getInterfaces()[0]
                    : resolvedApi.getClass();

            api = resolvedApi;
            findOnlinePlayerMethod = resolveMethod(apiClass, new String[]{"findOnlinePlayer"}, "online", String.class);
            buildDisplayMethod = resolveMethod(apiClass, new String[]{"buildDisplay"}, "display", Player.class);
            refreshPlayerMethod = resolveMethod(apiClass, new String[]{"refreshPlayer"}, "refresh", Player.class);
            setNicknameByPlayerMethod = resolveMethod(apiClass, NICKNAME_SETTER_METHODS, "nick", Player.class, String.class);
            setNicknameByUuidMethod = resolveMethod(apiClass, NICKNAME_SETTER_METHODS, "nick", UUID.class, String.class);
            clearNicknameByPlayerMethod = resolveMethod(apiClass, NICKNAME_CLEAR_METHODS, "nick", Player.class);
            clearNicknameByUuidMethod = resolveMethod(apiClass, NICKNAME_CLEAR_METHODS, "nick", UUID.class);

            plugin.getLogger().info("[ReAbility] nicknames API 연동 활성화");
        } catch (Throwable t) {
            clear();
            plugin.getLogger().warning("[ReAbility] nicknames API 연동 실패: " + t.getClass().getSimpleName());
        }
    }

    public static Player findOnlinePlayer(String query) {
        if (query == null || query.isBlank()) return null;

        Object currentApi = api;
        Method currentMethod = findOnlinePlayerMethod;
        if (currentApi != null && currentMethod != null) {
            try {
                Object optionalObj = currentMethod.invoke(currentApi, query);
                if (optionalObj instanceof Optional<?> optional && optional.isPresent() && optional.get() instanceof Player p) {
                    return p;
                }
            } catch (Throwable ignored) {
                // fallback below
            }
        }

        Player exact = Bukkit.getPlayerExact(query);
        if (exact != null) return exact;
        return Bukkit.getPlayer(query);
    }

    public static Component getChatDisplayName(Player player) {
        if (player == null) return Component.empty();

        UUID uuid = player.getUniqueId();
        String fake = JokerNameRegistry.getFakeName(uuid);
        if (fake != null && !fake.isBlank()) {
            if (jokerManagedByNicknames.contains(uuid)) {
                Component nicknamesDisplay = tryBuildDisplay(player);
                if (nicknamesDisplay != null) {
                    return nicknamesDisplay;
                }
            }
            return Component.text(fake);
        }

        Component nicknamesDisplay = tryBuildDisplay(player);
        return nicknamesDisplay != null ? nicknamesDisplay : Component.text(player.getName());
    }

    public static boolean applyJokerNickname(Player player, String fakeName) {
        if (player == null || fakeName == null || fakeName.isBlank()) return false;

        Object currentApi = api;
        if (currentApi == null) return false;

        try {
            if (setNicknameByPlayerMethod != null) {
                setNicknameByPlayerMethod.invoke(currentApi, player, fakeName);
            } else if (setNicknameByUuidMethod != null) {
                setNicknameByUuidMethod.invoke(currentApi, player.getUniqueId(), fakeName);
            } else {
                return false;
            }

            jokerManagedByNicknames.add(player.getUniqueId());
            refreshPlayerInternal(player);
            return true;
        } catch (Throwable ignored) {
            jokerManagedByNicknames.remove(player.getUniqueId());
            return false;
        }
    }

    public static boolean clearJokerNickname(Player player) {
        if (player == null) return false;

        Object currentApi = api;
        if (currentApi == null) {
            jokerManagedByNicknames.remove(player.getUniqueId());
            return false;
        }

        boolean invoked = false;
        try {
            if (clearNicknameByPlayerMethod != null) {
                clearNicknameByPlayerMethod.invoke(currentApi, player);
                invoked = true;
            } else if (clearNicknameByUuidMethod != null) {
                clearNicknameByUuidMethod.invoke(currentApi, player.getUniqueId());
                invoked = true;
            } else if (setNicknameByPlayerMethod != null) {
                try {
                    setNicknameByPlayerMethod.invoke(currentApi, player, (String) null);
                } catch (Throwable ignored) {
                    setNicknameByPlayerMethod.invoke(currentApi, player, "");
                }
                invoked = true;
            } else if (setNicknameByUuidMethod != null) {
                try {
                    setNicknameByUuidMethod.invoke(currentApi, player.getUniqueId(), (String) null);
                } catch (Throwable ignored) {
                    setNicknameByUuidMethod.invoke(currentApi, player.getUniqueId(), "");
                }
                invoked = true;
            }
        } catch (Throwable ignored) {
            invoked = false;
        } finally {
            jokerManagedByNicknames.remove(player.getUniqueId());
        }

        if (invoked) {
            refreshPlayerInternal(player);
        }
        return invoked;
    }

    public static void refreshPlayerIfAvailable(Player player) {
        if (player == null) return;
        if (JokerNameRegistry.getFakeName(player.getUniqueId()) != null
                && !jokerManagedByNicknames.contains(player.getUniqueId())) {
            return;
        }
        refreshPlayerInternal(player);
    }

    private static void refreshPlayerInternal(Player player) {
        Object currentApi = api;
        Method currentMethod = refreshPlayerMethod;
        if (currentApi == null || currentMethod == null) return;

        try {
            currentMethod.invoke(currentApi, player);
        } catch (Throwable ignored) {
            // ignore
        }
    }

    private static Component tryBuildDisplay(Player player) {
        Object currentApi = api;
        Method currentBuildMethod = buildDisplayMethod;
        if (currentApi == null || currentBuildMethod == null) return null;

        try {
            Object snapshot = currentBuildMethod.invoke(currentApi, player);
            if (snapshot == null) return null;

            Method displayMethod = snapshot.getClass().getMethod("display");
            Object display = displayMethod.invoke(snapshot);
            if (display instanceof Component component) {
                return component;
            }
        } catch (Throwable ignored) {
            // ignore and fallback
        }
        return null;
    }

    private static Method resolveMethod(Class<?> apiClass, String[] names, String fallbackHint, Class<?>... params) {
        for (String name : names) {
            try {
                return apiClass.getMethod(name, params);
            } catch (NoSuchMethodException ignored) {
                // try fallback scanning
            }
        }

        for (Method method : apiClass.getMethods()) {
            if (method.getParameterCount() != params.length) continue;
            String lower = method.getName().toLowerCase(Locale.ROOT);
            if (fallbackHint != null && !fallbackHint.isBlank() && !lower.contains(fallbackHint.toLowerCase(Locale.ROOT))) {
                continue;
            }

            Class<?>[] methodParams = method.getParameterTypes();
            boolean matches = true;
            for (int i = 0; i < params.length; i++) {
                if (!methodParams[i].isAssignableFrom(params[i])) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                return method;
            }
        }
        return null;
    }

    private static void clear() {
        api = null;
        findOnlinePlayerMethod = null;
        buildDisplayMethod = null;
        refreshPlayerMethod = null;
        setNicknameByPlayerMethod = null;
        setNicknameByUuidMethod = null;
        clearNicknameByPlayerMethod = null;
        clearNicknameByUuidMethod = null;
        jokerManagedByNicknames.clear();
    }
}
