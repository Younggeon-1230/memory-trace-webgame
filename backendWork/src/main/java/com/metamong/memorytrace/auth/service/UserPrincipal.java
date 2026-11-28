package com.metamong.memorytrace.auth.service;

import com.metamong.memorytrace.auth.dto.UserResponse;
import com.metamong.memorytrace.auth.entity.Role;
import com.metamong.memorytrace.auth.entity.User;
import java.io.Serial;
import java.util.Collection;
import java.util.List;
import lombok.Getter;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * 세션에 남는 로그인 사용자. 엔티티는 담지 않는다.
 * 비밀번호 해시는 인증할 때만 필요하며, 인증이 끝나면 AuthenticationManager가 지운다.
 */
public class UserPrincipal implements UserDetails, CredentialsContainer {

    @Serial
    private static final long serialVersionUID = 1L;

    @Getter
    private final Long userId;
    private final String loginId;
    @Getter
    private final String nickname;
    @Getter
    private final Role role;
    private String passwordHash;

    private UserPrincipal(Long userId, String loginId, String nickname, Role role, String passwordHash) {
        this.userId = userId;
        this.loginId = loginId;
        this.nickname = nickname;
        this.role = role;
        this.passwordHash = passwordHash;
    }

    public static UserPrincipal from(User user) {
        return new UserPrincipal(user.getId(), user.getLoginId(), user.getNickname(), user.getRole(),
                user.getPasswordHash());
    }

    public UserResponse toResponse() {
        return new UserResponse(userId, loginId, nickname, role);
    }

    @Override
    public String getUsername() {
        return loginId;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public void eraseCredentials() {
        passwordHash = null;
    }
}
