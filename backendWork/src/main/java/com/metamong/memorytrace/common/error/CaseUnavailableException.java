package com.metamong.memorytrace.common.error;

/** 검증 상태가 VALID가 아닌 사건의 행동 (409) */
public class CaseUnavailableException extends ApiException {

    public CaseUnavailableException() {
        super(ErrorCode.CASE_UNAVAILABLE);
    }
}
