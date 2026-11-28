package com.metamong.memorytrace.common.error;

/** 로그인 실패 (401). 아이디가 틀렸는지 비밀번호가 틀렸는지 구분해 알리지 않는다. */
public class LoginFailedException extends ApiException {

    public static final String MESSAGE = "아이디 또는 비밀번호가 맞지 않습니다.";

    public LoginFailedException() {
        super(ErrorCode.UNAUTHORIZED, MESSAGE);
    }
}
