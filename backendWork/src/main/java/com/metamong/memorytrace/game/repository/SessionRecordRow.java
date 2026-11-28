package com.metamong.memorytrace.game.repository;

import com.metamong.memorytrace.game.entity.Grade;
import com.metamong.memorytrace.game.entity.SessionStatus;

/** 사건별 내 기록을 계산하는 데 필요한 세션 값만 읽는다. 연관을 지연 로딩하지 않는다. */
public record SessionRecordRow(Long sessionId, Long caseId, SessionStatus status, int score, Grade grade) {
}
