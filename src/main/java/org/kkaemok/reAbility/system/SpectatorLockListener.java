package org.kkaemok.reAbility.system;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

public class SpectatorLockListener implements Listener {
    public static final String SPECTATOR_LOCK_TAG = "reability_spectator_lock";

    @EventHandler(ignoreCancelled = true)
    public void onSpectateTeleport(PlayerTeleportEvent event) {
        if (event.getCause() != PlayerTeleportEvent.TeleportCause.SPECTATE) return;
        if (!event.getPlayer().getScoreboardTags().contains(SPECTATOR_LOCK_TAG)) return;
        event.setCancelled(true);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        event.getPlayer().removeScoreboardTag(SPECTATOR_LOCK_TAG);
    }
}
