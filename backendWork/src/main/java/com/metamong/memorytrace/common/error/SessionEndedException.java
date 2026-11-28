package com.metamong.memorytrace.common.error;

/** 종료된 세션에 행동 요청 (409) */
public class SessionEndedException extends ApiException {

    public SessionEndedException() {
        super(ErrorCode.SESSION_ENDED);
    }
}
