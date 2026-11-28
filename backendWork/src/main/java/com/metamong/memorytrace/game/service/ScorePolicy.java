package com.metamong.memorytrace.game.service;

/** 점수·지목 규칙의 상수와 계산 (docs/backend_design.md 6.10). 감점·보너스·등급은 3-4에서 추가한다. */
public final class ScorePolicy {

    public static final int START_SCORE = 100;
    public static final int MAX_ACCUSATIONS = 2;

    private ScorePolicy() {
    }
}
