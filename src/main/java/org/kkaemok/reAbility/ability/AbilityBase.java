package org.kkaemok.reAbility.ability;

import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

public abstract class AbilityBase implements Listener {

    // 능력의 고유 이름 (영어 식별자, 예: "WEREWOLF")
    public abstract String getName();

    // 유저에게 보여질 이름 (한글, 예: "늑대인간")
    public abstract String getDisplayName();

    // 등급
    public abstract AbilityGrade getGrade();

    // 능력 설명 (Lore)
    public abstract String[] getDescription();

    // 능력이 활성화될 때 (접속, 뽑기 등) 실행
    public void onActivate(Player player) {
        // 기본적으로 메시지를 보내거나 초기 버프를 줌
    }

    // Called once when the ability is first acquired.
    public void onAcquire(Player player) {
        // no-op by default
    }

    // 능력이 비활성화될 때 (접속 종료, 교체 등) 실행
    public void onDeactivate(Player player) {
        // 버프 제거 등
    }

    // 웅크리기 스킬 (기본은 아무것도 안함, 필요한 능력만 오버라이드)
    public void onSneakSkill(Player player) {

    }
}