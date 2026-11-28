package com.metamong.memorytrace.common.config;

import com.metamong.memorytrace.common.security.JsonAccessDeniedHandler;
import com.metamong.memorytrace.common.security.JsonAuthenticationEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.savedrequest.NullRequestCache;

/** 세션 인증 API의 접근 규칙 (docs/backend_design.md 5.2). CORS는 열지 않는다. */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JsonAuthenticationEntryPoint entryPoint,
                                                   JsonAccessDeniedHandler accessDeniedHandler) throws Exception {
        return http
                // CSRF 토큰은 모든 프로필에서 쓰지 않는다 (5.3). 운영의 방어는 SameSite=Lax와
                // Origin 검사이며, Origin 검사 필터는 단계 4-2에서 추가한다. 그 전에는 배포하지 않는다.
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                // 로그아웃은 컨트롤러로 만든다 (비로그인 401을 지키기 위해)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(cache -> cache.requestCache(new NullRequestCache()))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(HttpMethod.POST, "/api/auth/signup", "/api/auth/login").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        // 정적 파일, SPA 경로, /error
                        .anyRequest().permitAll())
                .build();
    }
}
