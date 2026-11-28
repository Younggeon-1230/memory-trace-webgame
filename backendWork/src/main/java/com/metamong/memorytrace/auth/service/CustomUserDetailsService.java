package com.metamong.memorytrace.auth.service;

import com.metamong.memorytrace.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String loginId) {
        return userRepository.findByLoginId(loginId)
                // DB 정렬 규칙(utf8mb4_unicode_ci)은 대소문자와 뒤 공백을 구분하지 않아 한 번 더 비교한다
                .filter(user -> user.getLoginId().equals(loginId))
                .map(UserPrincipal::from)
                .orElseThrow(() -> new UsernameNotFoundException("사용자가 없습니다."));
    }
}
