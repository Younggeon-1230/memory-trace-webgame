package com.metamong.memorytrace.common.error;

/** 다른 회원의 세션 (403) */
public class NotSessionOwnerException extends ApiException {

    public NotSessionOwnerException() {
        super(ErrorCode.NOT_SESSION_OWNER);
    }
}
