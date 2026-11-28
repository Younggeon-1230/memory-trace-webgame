package com.metamong.memorytrace.common.error;

/** 단서·용의자가 세션의 사건에 속하지 않음 (400) */
public class TargetNotInCaseException extends ApiException {

    public TargetNotInCaseException() {
        super(ErrorCode.TARGET_NOT_IN_CASE);
    }

    public TargetNotInCaseException(String message) {
        super(ErrorCode.TARGET_NOT_IN_CASE, message);
    }
}
