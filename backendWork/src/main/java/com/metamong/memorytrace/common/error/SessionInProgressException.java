package com.metamong.memorytrace.common.error;

/** 진행 중 세션의 결과·타임라인 조회 (409) */
public class SessionInProgressException extends ApiException {

    public SessionInProgressException() {
        super(ErrorCode.SESSION_IN_PROGRESS);
    }
}
