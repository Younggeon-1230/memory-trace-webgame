package com.metamong.memorytrace.game.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.metamong.memorytrace.game.entity.SessionStatus;
import java.util.List;

/**
 * 게임 상태 (프론트의 GameState). 정답·진상·판정 규칙·반박 문구·선행 조건은 담지 않는다
 * (docs/backend_design.md 4.7, 4.15). 필드를 늘리기 전에 공개해도 되는지 확인할 것.
 */
public record GameStateResponse(
        Long sessionId,
        SessionStatus status,
        int score,
        int accusationsLeft,
        int wrongExclusionCount,
        @JsonProperty("case") CaseInfoDto caseInfo,
        List<SceneAreaDto> sceneAreas,
        List<ClueDto> clues,
        List<TraceEntryDto> traceDictionary,
        List<SuspectDto> suspects) {
}
