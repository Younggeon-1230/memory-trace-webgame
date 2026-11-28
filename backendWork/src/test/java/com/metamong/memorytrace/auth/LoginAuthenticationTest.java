package com.metamong.memorytrace.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.metamong.memorytrace.auth.entity.User;
import com.metamong.memorytrace.auth.repository.UserRepository;
import com.metamong.memorytrace.auth.service.CustomUserDetailsService;
import com.metamong.memorytrace.auth.service.UserPrincipal;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** 사용자 조회와 인증 (docs/backend_design.md 5.1). DB 없이 실행한다. */
class LoginAuthenticationTest {

    private final UserRepository users = mock(UserRepository.class);
    private final PasswordEncoder encoder = new BCryptPasswordEncoder();
    private final CustomUserDetailsService userDetailsService = new CustomUserDetailsService(users);

    private User storedUser() {
        return User.signUp("tester", encoder.encode("pass1234!"), "탐정", LocalDateTime.of(2026, 11, 28, 12, 0));
    }

    private AuthenticationManager manager() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(encoder);
        return new ProviderManager(provider);
    }

    private Authentication login(String loginId, String password) {
        return manager().authenticate(UsernamePasswordAuthenticationToken.unauthenticated(loginId, password));
    }

    @Test
    void passwordHashIsErasedAfterAuthentication() {
        User user = storedUser();
        when(users.findByLoginId("tester")).thenReturn(Optional.of(user));

        Authentication authentication = login("tester", "pass1234!");

        assertThat(((UserPrincipal) authentication.getPrincipal()).getPassword()).isNull();
        assertThat(authentication.getCredentials()).isNull();
    }

    @Test
    void loginIdMustMatchExactly() {
        // DB 정렬 규칙처럼 대소문자가 달라도 행을 돌려주는 상황
        User user = storedUser();
        when(users.findByLoginId("TESTER")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userDetailsService.loadUserByUsername("TESTER"))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    @Test
    void passwordLongerThan72BytesFailsAsBadCredentials() {
        User user = storedUser();
        when(users.findByLoginId("tester")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> login("tester", "가".repeat(64))).isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void repositoryFailureIsNotBadCredentials() {
        when(users.findByLoginId("tester")).thenThrow(new DataAccessResourceFailureException("db down"));

        assertThatThrownBy(() -> login("tester", "pass1234!"))
                .isInstanceOf(InternalAuthenticationServiceException.class);
    }
}
