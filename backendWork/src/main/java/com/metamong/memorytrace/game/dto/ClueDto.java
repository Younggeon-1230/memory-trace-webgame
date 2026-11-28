package com.metamong.memorytrace.game.dto;

import com.metamong.memorytrace.cases.entity.Clue;
import com.metamong.memorytrace.cases.entity.ClueKind;
import java.time.LocalDateTime;

/** 수집한 단서만 이 형태로 보낸다. 판정 규칙, 핵심 여부, 반박 문구는 담지 않는다. */
public record ClueDto(Long clueId, String clueCode, ClueKind kind, String name, String summary,
                      String detailText, LocalDateTime collectedAt) {

    public static ClueDto of(Clue clue, LocalDateTime collectedAt) {
        return new ClueDto(clue.getId(), clue.getClueCode(), clue.getKind(), clue.getName(),
                clue.getSummary(), clue.getDetailText(), collectedAt);
    }
}
