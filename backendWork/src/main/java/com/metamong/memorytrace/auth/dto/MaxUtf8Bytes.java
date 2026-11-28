package com.metamong.memorytrace.auth.dto;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** UTF-8로 바꾼 길이 제한. BCrypt는 72바이트까지만 처리한다 (D6). */
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = MaxUtf8BytesValidator.class)
public @interface MaxUtf8Bytes {

    int value();

    /** 글자 수가 이보다 길면 검사를 건너뛴다. 길이 제한(@Size)의 문구만 나가게 하기 위해서다. */
    int maxChars();

    String message();

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
