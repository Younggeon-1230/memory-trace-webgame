package com.metamong.memorytrace.common.error;

import lombok.Getter;

/** 업무 오류의 공통 부모. 메시지는 응답에 그대로 나가므로 내부 정보를 담지 않는다. */
@Getter
public abstract class ApiException extends RuntimeException {

    private final ErrorCode errorCode;

    protected ApiException(ErrorCode errorCode) {
        this(errorCode, errorCode.getDefaultMessage());
    }

    protected ApiException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
