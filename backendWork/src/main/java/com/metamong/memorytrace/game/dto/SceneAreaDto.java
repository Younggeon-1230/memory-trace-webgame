package com.metamong.memorytrace.game.dto;

import com.metamong.memorytrace.cases.entity.Clue;

/** 현장의 클릭 영역. 미수집 단서도 포함하므로 단서의 이름이나 내용은 담지 않는다. */
public record SceneAreaDto(Long clueId, double x, double y, double w, double h, boolean collected) {

    /** @param clue SCENE 단서. 좌표는 DB의 CHECK 제약으로 항상 있다. */
    public static SceneAreaDto of(Clue clue, boolean collected) {
        return new SceneAreaDto(clue.getId(),
                clue.getAreaX().doubleValue(), clue.getAreaY().doubleValue(),
                clue.getAreaW().doubleValue(), clue.getAreaH().doubleValue(),
                collected);
    }
}
