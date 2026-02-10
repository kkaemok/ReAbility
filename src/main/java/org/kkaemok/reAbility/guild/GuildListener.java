package org.kkaemok.reAbility.guild;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.List;

public class GuildListener implements Listener {
    private final GuildManager gm;
    public GuildListener(GuildManager gm) { this.gm = gm; }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        if (gm.pendingRequests.containsKey(e.getPlayer().getUniqueId())) {
            List<String> reqs = gm.pendingRequests.get(e.getPlayer().getUniqueId());
            if (!reqs.isEmpty()) {
                e.getPlayer().sendMessage("[!] 보류 중인 길드 가입 요청: " + String.join(", ", reqs));
                e.getPlayer().sendMessage("/길드 수락 <플레이어명> 명령어로 수락하세요.");
            }
        }
    }
}
