package com.metamong.memorytrace.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.metamong.memorytrace.auth.dto.SignupRequest;
import com.metamong.memorytrace.auth.entity.Role;
import com.metamong.memorytrace.auth.entity.User;
import com.metamong.memorytrace.auth.repository.UserRepository;
import com.metamong.memorytrace.auth.service.AuthService;
import com.metamong.memorytrace.common.error.LoginIdTakenException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthServiceTest {

    private final UserRepository users = mock(UserRepository.class);
    private final PasswordEncoder encoder = new BCryptPasswordEncoder();
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-28T03:04:05Z"), ZoneId.of("Asia/Seoul"));
    private final AuthService service = new AuthService(users, encoder, clock);

    private final SignupRequest request = new SignupRequest("tester", "pass1234!", "탐정");

    @Test
    void savesHashedUserWithAllColumnsFilled() {
        service.signup(request);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(users).saveAndFlush(saved.capture());
        User user = saved.getValue();
        assertThat(user.getPasswordHash()).startsWith("$2a$10$").isNotEqualTo("pass1234!");
        assertThat(encoder.matches("pass1234!", user.getPasswordHash())).isTrue();
        assertThat(user.getRole()).isEqualTo(Role.USER);
        assertThat(user.getCreatedAt()).isEqualTo(LocalDateTime.of(2026, 11, 28, 12, 4, 5));
    }

    @Test
    void existingLoginIdIsRejectedWithoutSaving() {
        when(users.existsByLoginId("tester")).thenReturn(true);

        assertThatThrownBy(() -> service.signup(request)).isInstanceOf(LoginIdTakenException.class);
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void concurrentSignupBecomesLoginIdTaken() {
        // 처음에는 없었지만 저장이 실패한 뒤에는 있다
        when(users.existsByLoginId("tester")).thenReturn(false, true);
        when(users.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> service.signup(request)).isInstanceOf(LoginIdTakenException.class);
    }

    @Test
    void otherIntegrityFailureIsRethrownAsIs() {
        DataIntegrityViolationException failure = new DataIntegrityViolationException("other");
        when(users.existsByLoginId("tester")).thenReturn(false);
        when(users.saveAndFlush(any())).thenThrow(failure);

        assertThatThrownBy(() -> service.signup(request)).isSameAs(failure);
    }
}
