package com.metamong.memorytrace.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

/** 로그인 처리에 쓰는 빈 (docs/backend_design.md 5.1). 접근 규칙은 SecurityConfig에 있다. */
@Configuration
public class AuthenticationBeanConfig {

    /** BCrypt 기본 강도 10 */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** CustomUserDetailsService와 위의 PasswordEncoder로 인증한다. */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    /** 로그인 컨트롤러가 SecurityContext를 세션에 직접 저장할 때 쓴다. */
    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }
}
