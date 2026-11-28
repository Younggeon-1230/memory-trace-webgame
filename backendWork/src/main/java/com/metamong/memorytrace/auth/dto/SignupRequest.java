package com.metamong.memorytrace.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 가입 요청. 문구는 프론트의 mock 서버와 같다.
 * 검사가 여러 개 걸려도 결과가 같도록 한 필드의 검사는 같은 문구를 쓴다.
 */
public record SignupRequest(
        @NotBlank(message = SignupRequest.LOGIN_ID_MESSAGE)
        @Pattern(regexp = "^[a-z0-9_]{4,30}$", message = SignupRequest.LOGIN_ID_MESSAGE)
        String loginId,

        @NotBlank(message = SignupRequest.PASSWORD_MESSAGE)
        @Size(min = 8, max = 64, message = SignupRequest.PASSWORD_MESSAGE)
        @MaxUtf8Bytes(value = 72, maxChars = 64, message = SignupRequest.PASSWORD_BYTES_MESSAGE)
        String password,

        @NotBlank(message = SignupRequest.NICKNAME_MESSAGE)
        @Size(min = 2, max = 20, message = SignupRequest.NICKNAME_MESSAGE)
        String nickname) {

    static final String LOGIN_ID_MESSAGE = "아이디는 영문 소문자·숫자·_ 4~30자입니다.";
    static final String PASSWORD_MESSAGE = "비밀번호는 8~64자입니다.";
    static final String PASSWORD_BYTES_MESSAGE = "비밀번호가 너무 깁니다. 한글·특수 문자는 글자당 여러 바이트를 차지합니다.";
    static final String NICKNAME_MESSAGE = "닉네임은 2~20자입니다.";

    /** 닉네임은 앞뒤 공백을 제거한 값을 검사하고 저장한다. */
    public SignupRequest {
        nickname = nickname == null ? null : nickname.strip();
    }

    /** 로그에 비밀번호가 남지 않게 한다. */
    @Override
    public String toString() {
        return "SignupRequest[loginId=" + loginId + "]";
    }
}
