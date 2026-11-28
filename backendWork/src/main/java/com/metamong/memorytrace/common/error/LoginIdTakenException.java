package com.metamong.memorytrace.common.error;

/** 아이디 중복 (409) */
public class LoginIdTakenException extends ApiException {

    public LoginIdTakenException() {
        super(ErrorCode.LOGIN_ID_TAKEN);
    }
}
