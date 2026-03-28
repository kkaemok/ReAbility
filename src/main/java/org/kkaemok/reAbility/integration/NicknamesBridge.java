package org.kkaemok.reAbility.integration;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.kkaemok.reAbility.utils.JokerNameRegistry;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;

public final class NicknamesBridge {
    private static volatile Object api;
    private static volatile Method findOnlinePlayerMethod;
    private static volatile Method buildDisplayMethod;
    private static volatile Method refreshPlayerMethod;

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
            findOnlinePlayerMethod = apiClass.getMethod("findOnlinePlayer", String.class);
            buildDisplayMethod = apiClass.getMethod("buildDisplay", Player.class);
            refreshPlayerMethod = apiClass.getMethod("refreshPlayer", Player.class);

            plugin.getLogger().info("[ReAbility] nicknames API 연동 활성화.");
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
            return Component.text(fake);
        }

        Object currentApi = api;
        Method currentBuildMethod = buildDisplayMethod;
        if (currentApi != null && currentBuildMethod != null) {
            try {
                Object snapshot = currentBuildMethod.invoke(currentApi, player);
                if (snapshot != null) {
                    Method displayMethod = snapshot.getClass().getMethod("display");
                    Object display = displayMethod.invoke(snapshot);
                    if (display instanceof Component component) {
                        return component;
                    }
                }
            } catch (Throwable ignored) {
                // fallback below
            }
        }

        return Component.text(player.getName());
    }

    public static void refreshPlayerIfAvailable(Player player) {
        if (player == null) return;
        if (JokerNameRegistry.getFakeName(player.getUniqueId()) != null) return;

        Object currentApi = api;
        Method currentMethod = refreshPlayerMethod;
        if (currentApi == null || currentMethod == null) return;

        try {
            currentMethod.invoke(currentApi, player);
        } catch (Throwable ignored) {
            // ignore
        }
    }

    private static void clear() {
        api = null;
        findOnlinePlayerMethod = null;
        buildDisplayMethod = null;
        refreshPlayerMethod = null;
    }
}
