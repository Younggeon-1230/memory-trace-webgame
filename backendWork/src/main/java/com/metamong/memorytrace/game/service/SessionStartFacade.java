package com.metamong.memorytrace.game.service;

import com.metamong.memorytrace.game.dto.SessionStartResult;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * 동시 시작 처리 (D25). 일부러 @Transactional을 붙이지 않는다. 생성 트랜잭션이 롤백까지 끝난 뒤에
 * 새 트랜잭션에서 다시 조회해야 하기 때문이다.
 */
@Component
@RequiredArgsConstructor
public class SessionStartFacade {

    private final SessionService sessionService;

    public SessionStartResult start(Long userId, Long caseId) {
        try {
            return sessionService.startOrResume(userId, caseId);
        } catch (DataIntegrityViolationException e) {
            // 제약 이름은 해석하지 않는다. 진행 중 세션이 생겼는지로만 판단하고, 없으면 원래 예외를 던진다
            return sessionService.findInProgress(userId, caseId)
                    .map(state -> new SessionStartResult(state, false))
                    .orElseThrow(() -> e);
        }
    }
}
