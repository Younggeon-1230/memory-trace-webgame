package com.metamong.memorytrace.auth.service;

import com.metamong.memorytrace.auth.dto.SignupRequest;
import com.metamong.memorytrace.auth.dto.UserResponse;
import com.metamong.memorytrace.auth.entity.User;
import com.metamong.memorytrace.auth.repository.UserRepository;
import com.metamong.memorytrace.common.error.LoginIdTakenException;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    /**
     * 가입. 일부러 @Transactional을 붙이지 않는다. 저장이 실패하면 리포지토리의 저장 트랜잭션이
     * 끝난 뒤에 아이디를 새로 조회해야 하기 때문이다.
     */
    public UserResponse signup(SignupRequest request) {
        if (userRepository.existsByLoginId(request.loginId())) {
            throw new LoginIdTakenException();
        }
        User user = User.signUp(request.loginId(), passwordEncoder.encode(request.password()),
                request.nickname(), LocalDateTime.now(clock));
        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // 동시 가입. 제약 이름은 해석하지 않고, 아이디가 생겼는지로만 판단한다
            if (userRepository.existsByLoginId(request.loginId())) {
                throw new LoginIdTakenException();
            }
            throw e;
        }
        return new UserResponse(user.getId(), user.getLoginId(), user.getNickname(), user.getRole());
    }
}
