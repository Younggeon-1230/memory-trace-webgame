package com.metamong.memorytrace.common.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/** API 오류 코드 (docs/backend_design.md 8장). 이름이 곧 응답의 code이며 프론트의 ApiErrorCode와 같아야 한다. */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "입력 값이 올바르지 않습니다."),
    TARGET_NOT_IN_CASE(HttpStatus.BAD_REQUEST, "이 사건의 대상이 아닙니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "허용되지 않은 요청입니다."),
    NOT_SESSION_OWNER(HttpStatus.FORBIDDEN, "다른 탐정의 수사 기록입니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 정보를 찾을 수 없습니다."),
    LOGIN_ID_TAKEN(HttpStatus.CONFLICT, "이미 사용 중인 아이디입니다."),
    SESSION_ENDED(HttpStatus.CONFLICT, "이미 종료된 수사입니다."),
    SESSION_IN_PROGRESS(HttpStatus.CONFLICT, "아직 진행 중인 수사입니다."),
    CASE_UNAVAILABLE(HttpStatus.CONFLICT, "지금은 이 사건의 수사를 진행할 수 없습니다. 나중에 다시 확인해 주세요."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버에서 오류가 발생했습니다. 잠시 뒤 다시 시도해 주세요.");

    private final HttpStatus status;
    private final String defaultMessage;
}
