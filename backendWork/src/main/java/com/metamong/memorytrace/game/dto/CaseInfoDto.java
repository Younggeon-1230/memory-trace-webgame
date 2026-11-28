package com.metamong.memorytrace.game.dto;

import com.metamong.memorytrace.cases.entity.CaseFile;

public record CaseInfoDto(Long caseId, String caseCode, String title, String briefing, String sceneImagePath) {

    public static CaseInfoDto from(CaseFile caseFile) {
        return new CaseInfoDto(caseFile.getId(), caseFile.getCaseCode(), caseFile.getTitle(),
                caseFile.getBriefing(), caseFile.getSceneImagePath());
    }
}
