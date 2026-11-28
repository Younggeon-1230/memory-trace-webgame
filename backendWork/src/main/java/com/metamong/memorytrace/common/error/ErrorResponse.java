package com.metamong.memorytrace.common.error;

/** 모든 오류 응답의 형식. 컨트롤러 단계와 필터 단계가 함께 쓴다. */
public record ErrorResponse(String code, String message) {

    public static ErrorResponse of(ErrorCode errorCode) {
        return new ErrorResponse(errorCode.name(), errorCode.getDefaultMessage());
    }

    public static ErrorResponse of(ErrorCode errorCode, String message) {
        return new ErrorResponse(errorCode.name(), message);
    }
}
