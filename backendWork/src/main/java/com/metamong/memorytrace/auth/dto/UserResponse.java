package com.metamong.memorytrace.auth.dto;

import com.metamong.memorytrace.auth.entity.Role;

/** 프론트의 User 타입. 비밀번호 해시는 담지 않는다. */
public record UserResponse(Long userId, String loginId, String nickname, Role role) {
}
