package com.metamong.memorytrace.common.error;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * 컨트롤러 단계의 오류를 {code, message}로 바꾼다 (docs/backend_design.md 8장).
 * 필터 단계의 401·403은 여기에 오지 않으며 common.security 가 같은 형식으로 쓴다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String UNSUPPORTED_REQUEST = "지원하지 않는 요청입니다.";

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApi(ApiException e) {
        return respond(e.getErrorCode().getStatus(), ErrorResponse.of(e.getErrorCode(), e.getMessage()));
    }

    /** {@code @Valid} 본문 검증 실패. 첫 필드 오류의 메시지를 보낸다. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleInvalidBody(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(DefaultMessageSourceResolvable::getDefaultMessage)
                .findFirst()
                .orElse(ErrorCode.VALIDATION_ERROR.getDefaultMessage());
        return respond(HttpStatus.BAD_REQUEST, ErrorResponse.of(ErrorCode.VALIDATION_ERROR, message));
    }

    /** 쿼리·경로 값 검증 실패, 깨진 JSON, 타입 불일치. 예외 문구에는 내부 정보가 있어 보내지 않는다. */
    @ExceptionHandler({
            ConstraintViolationException.class,
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<ErrorResponse> handleBadRequest() {
        return respond(HttpStatus.BAD_REQUEST, ErrorResponse.of(ErrorCode.VALIDATION_ERROR));
    }

    /**
     * 인증하는 도중의 서버 장애(DB 오류 등). 인증 예외의 하위 타입이지만 로그인 실패가 아니므로
     * 401로 바꾸지 않고 500으로 보내며 로그를 남긴다.
     */
    @ExceptionHandler(AuthenticationServiceException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationFailure(AuthenticationServiceException e) {
        return internalError(e);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthentication() {
        return respond(HttpStatus.UNAUTHORIZED,
                ErrorResponse.of(ErrorCode.UNAUTHORIZED, LoginFailedException.MESSAGE));
    }

    /** 다시 던져 Security 필터가 처리하게 한다(비로그인 401, 로그인 403). 아래의 일반 처리가 500으로 삼키지 않게 한다. */
    @ExceptionHandler(AccessDeniedException.class)
    public void handleAccessDenied(AccessDeniedException e) {
        throw e;
    }

    /**
     * 그 밖의 모든 예외. 스프링 표준 4xx(없는 경로, 405, 415, 필수 값 누락, 쿼리 값 검증 등)는
     * 원래 상태 코드를 유지하고, 나머지는 500으로 보낸다. 스택은 로그에만 남긴다.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleOthers(Exception e) {
        if (e instanceof org.springframework.web.ErrorResponse standard
                && standard.getStatusCode().is4xxClientError()) {
            return respond(standard.getStatusCode(), standard.getHeaders(), forStandard(standard.getStatusCode()));
        }
        return internalError(e);
    }

    private ResponseEntity<ErrorResponse> internalError(Exception e) {
        log.error("예상하지 못한 오류", e);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, ErrorResponse.of(ErrorCode.INTERNAL_ERROR));
    }

    private ErrorResponse forStandard(HttpStatusCode status) {
        return switch (status.value()) {
            case 404 -> ErrorResponse.of(ErrorCode.NOT_FOUND);
            case 405, 406, 415 -> ErrorResponse.of(ErrorCode.VALIDATION_ERROR, UNSUPPORTED_REQUEST);
            default -> ErrorResponse.of(ErrorCode.VALIDATION_ERROR);
        };
    }

    private ResponseEntity<ErrorResponse> respond(HttpStatusCode status, ErrorResponse body) {
        return respond(status, HttpHeaders.EMPTY, body);
    }

    /** Content-Type을 JSON으로 고정해, 요청의 Accept와 관계없이 같은 형식으로 응답한다. */
    private ResponseEntity<ErrorResponse> respond(HttpStatusCode status, HttpHeaders headers, ErrorResponse body) {
        return ResponseEntity.status(status)
                .headers(headers)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }
}
