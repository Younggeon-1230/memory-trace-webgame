package com.metamong.memorytrace.common.error;

/** 사건·세션 없음, 비공개 사건 시작 (404) */
public class ResourceNotFoundException extends ApiException {

    public ResourceNotFoundException() {
        super(ErrorCode.NOT_FOUND);
    }

    public ResourceNotFoundException(String message) {
        super(ErrorCode.NOT_FOUND, message);
    }
}
