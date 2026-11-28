package com.metamong.memorytrace.common.error;

/** 입력 오류, requestId 오용 (400) */
public class InvalidRequestException extends ApiException {

    public InvalidRequestException() {
        super(ErrorCode.VALIDATION_ERROR);
    }

    public InvalidRequestException(String message) {
        super(ErrorCode.VALIDATION_ERROR, message);
    }
}
