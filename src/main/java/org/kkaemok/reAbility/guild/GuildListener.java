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
                e.getPlayer().sendMessage("§e[!] 부재 중 가입 요청: §f" + String.join(", ", reqs));
                e.getPlayer().sendMessage("§7/길드 수락 <닉네임> 명령어를 사용하세요.");
            }
        }
    }
}