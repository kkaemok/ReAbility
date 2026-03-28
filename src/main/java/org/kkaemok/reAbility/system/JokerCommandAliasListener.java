package org.kkaemok.reAbility.system;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.kkaemok.reAbility.utils.JokerNameRegistry;

import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JokerCommandAliasListener implements Listener {
    @EventHandler(ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String message = event.getMessage();
        if (message.isEmpty()) return;

        Map<UUID, String> snapshot = JokerNameRegistry.snapshot();
        if (snapshot.isEmpty()) return;

        String updated = message;
        for (Map.Entry<UUID, String> entry : snapshot.entrySet()) {
            Player real = Bukkit.getPlayer(entry.getKey());
            if (real == null || !real.isOnline()) continue;

            String fake = entry.getValue();
            if (fake == null || fake.isEmpty()) continue;

            String pattern = "(?i)(?<=^|\\s)" + Pattern.quote(fake) + "(?=$|\\s)";
            updated = updated.replaceAll(pattern, Matcher.quoteReplacement(real.getName()));
        }

        if (!updated.equals(message)) {
            event.setMessage(updated);
        }
    }
}
