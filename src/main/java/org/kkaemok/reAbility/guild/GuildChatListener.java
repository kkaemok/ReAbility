package org.kkaemok.reAbility.guild;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public class GuildChatListener implements Listener {
    private final GuildManager gm;

    public GuildChatListener(GuildManager gm) {
        this.gm = gm;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();

        // 1. LuckPerms 데이터 가져오기 (네가 준 코드 로직 100% 유지)
        User user = LuckPermsProvider.get().getUserManager().getUser(player.getUniqueId());
        String prefix = (user != null && user.getCachedData().getMetaData().getPrefix() != null)
                ? user.getCachedData().getMetaData().getPrefix() : "";
        String suffix = (user != null && user.getCachedData().getMetaData().getSuffix() != null)
                ? user.getCachedData().getMetaData().getSuffix() : "";

        // 2. 기본 포맷 조립: 접두사 + 이름 + 접미사
        Component playerDisplayName = gm.parseColor(prefix)
                .append(Component.text(player.getName()))
                .append(gm.parseColor(suffix));

        // 3. 길드 채팅 모드일 때 처리
        if (gm.isGuildChatMode(player.getUniqueId())) {
            event.setCancelled(true); // 일반 채팅으로 나가는 것 차단

            // 포맷: [길드챗] 접두사닉네임접미사: 메시지
            Component guildMsg = Component.text("[길드챗] ", NamedTextColor.GREEN)
                    .append(playerDisplayName)
                    .append(Component.text(": ").color(NamedTextColor.WHITE))
                    .append(event.message());

            // 길드원 및 관리자에게만 전송
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.isOp() || isSameGuild(player, p)) {
                    p.sendMessage(guildMsg);
                }
            }
            Bukkit.getConsoleSender().sendMessage(guildMsg);
            return; // 길드챗 처리 끝났으므로 종료
        }

        // 4. 일반 채팅일 때 (포맷 강제 변경)
        // 포맷: 접두사닉네임접미사: 메시지 (네가 준 포맷 100% 적용)
        event.renderer((source, sourceDisplayName, message, viewer) ->
                playerDisplayName
                        .append(Component.text(": ").color(NamedTextColor.WHITE))
                        .append(message)
        );
    }

    private boolean isSameGuild(Player p1, Player p2) {
        GuildData g1 = gm.getGuildByMember(p1.getUniqueId());
        GuildData g2 = gm.getGuildByMember(p2.getUniqueId());
        return g1 != null && g2 != null && g1.name.equals(g2.name);
    }
}