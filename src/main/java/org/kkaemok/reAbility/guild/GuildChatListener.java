package org.kkaemok.reAbility.guild;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.kkaemok.reAbility.integration.NicknamesBridge;

public class GuildChatListener implements Listener {
    private final GuildManager gm;

    public GuildChatListener(GuildManager gm) {
        this.gm = gm;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        Component displayName = NicknamesBridge.getChatDisplayName(player);

        if (gm.isGuildChatMode(player.getUniqueId())) {
            event.setCancelled(true);

            Component guildMsg = Component.text("[길드챗] ", NamedTextColor.GREEN)
                    .append(displayName)
                    .append(Component.text(": ", NamedTextColor.WHITE))
                    .append(event.message());

            Bukkit.getScheduler().runTask(gm.getPlugin(), () -> {
                for (Player online : Bukkit.getOnlinePlayers()) {
                    if (online.isOp() || isSameGuild(player, online)) {
                        online.sendMessage(guildMsg);
                    }
                }
                Bukkit.getConsoleSender().sendMessage(guildMsg);
            });
            return;
        }

        event.renderer((source, sourceDisplayName, message, viewer) ->
                displayName
                        .append(Component.text(": ", NamedTextColor.WHITE))
                        .append(message)
        );
    }

    private boolean isSameGuild(Player p1, Player p2) {
        GuildData g1 = gm.getGuildByMember(p1.getUniqueId());
        GuildData g2 = gm.getGuildByMember(p2.getUniqueId());
        return g1 != null && g2 != null && g1.name.equalsIgnoreCase(g2.name);
    }
}
