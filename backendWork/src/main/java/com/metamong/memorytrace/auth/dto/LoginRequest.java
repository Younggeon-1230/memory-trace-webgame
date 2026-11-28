package com.metamong.memorytrace.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = LoginRequest.LOGIN_ID_MESSAGE)
        @Size(max = 30, message = LoginRequest.LOGIN_ID_MESSAGE)
        String loginId,

        @NotBlank(message = LoginRequest.PASSWORD_MESSAGE)
        @Size(max = 64, message = LoginRequest.PASSWORD_MESSAGE)
        String password) {

    static final String LOGIN_ID_MESSAGE = "아이디를 30자 이내로 입력해 주세요.";
    static final String PASSWORD_MESSAGE = "비밀번호를 64자 이내로 입력해 주세요.";

    /** 로그에 비밀번호가 남지 않게 한다. */
    @Override
    public String toString() {
        return "LoginRequest[loginId=" + loginId + "]";
    }
}
