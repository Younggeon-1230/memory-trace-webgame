package com.metamong.memorytrace.game.dto;

/** 흔적 사전의 한 항목. 어느 단서의 규칙인지 알 수 없도록 ID를 담지 않는다 (D4). */
public record TraceEntryDto(String basisText) {
}
