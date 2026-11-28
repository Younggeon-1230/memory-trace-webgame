package com.metamong.memorytrace.game.dto;

/** 수사 시작의 결과. created면 201, 이어하기면 200이다. 응답 본문은 state만 보낸다. */
public record SessionStartResult(GameStateResponse state, boolean created) {
}
