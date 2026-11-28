package com.metamong.memorytrace.common.time;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 모든 시각은 이 Clock으로 만든다 (docs/backend_design.md 1.1).
 * 초 단위로 자른다. DATETIME에 소수 초를 넘기면 MySQL이 반올림하기 때문이다.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.tick(Clock.system(ZoneId.of("Asia/Seoul")), Duration.ofSeconds(1));
    }
}
