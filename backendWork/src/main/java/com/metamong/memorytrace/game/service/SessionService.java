package com.metamong.memorytrace.game.service;

import com.metamong.memorytrace.auth.repository.UserRepository;
import com.metamong.memorytrace.cases.entity.CaseFile;
import com.metamong.memorytrace.cases.repository.CaseFileRepository;
import com.metamong.memorytrace.cases.repository.CaseSuspectRepository;
import com.metamong.memorytrace.common.error.NotSessionOwnerException;
import com.metamong.memorytrace.common.error.ResourceNotFoundException;
import com.metamong.memorytrace.game.dto.GameStateResponse;
import com.metamong.memorytrace.game.dto.SessionStartResult;
import com.metamong.memorytrace.game.entity.PlaySession;
import com.metamong.memorytrace.game.entity.SessionStatus;
import com.metamong.memorytrace.game.entity.SessionSuspect;
import com.metamong.memorytrace.game.repository.PlaySessionRepository;
import com.metamong.memorytrace.game.repository.SessionSuspectRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SessionService {

    private final CaseFileRepository caseFileRepository;
    private final CaseSuspectRepository caseSuspectRepository;
    private final PlaySessionRepository playSessionRepository;
    private final SessionSuspectRepository sessionSuspectRepository;
    private final UserRepository userRepository;
    private final GameStateAssembler assembler;
    private final Clock clock;

    /**
     * 진행 중 세션이 있으면 이어 하고, 없으면 만든다. 동시 요청이 먼저 만들었으면
     * DataIntegrityViolationException이 나며 SessionStartFacade가 처리한다. 여기에서는 잡지 않는다.
     */
    @Transactional
    public SessionStartResult startOrResume(Long userId, Long caseId) {
        // 공개 중이 아니면 진행 중 세션이 있어도 404 (7.5)
        CaseFile caseFile = caseFileRepository.findPublishedById(caseId)
                .orElseThrow(() -> new ResourceNotFoundException("사건을 찾을 수 없습니다."));
        Optional<PlaySession> existing = findActiveSession(userId, caseId);
        if (existing.isPresent()) {
            return new SessionStartResult(assembler.assemble(existing.get()), false);
        }

        LocalDateTime now = LocalDateTime.now(clock);
        PlaySession session = playSessionRepository.save(PlaySession.start(
                userRepository.getReferenceById(userId), caseFile, ScorePolicy.START_SCORE, now));
        sessionSuspectRepository.saveAll(
                caseSuspectRepository.findByCaseFileIdOrderByDisplayOrderAscIdAsc(caseId).stream()
                        .map(suspect -> SessionSuspect.active(session, suspect, now))
                        .toList());
        return new SessionStartResult(assembler.assemble(session), true);
    }

    /** 동시 시작에서 진 요청이 새 트랜잭션에서 상대가 만든 세션을 읽는다. */
    @Transactional(readOnly = true)
    public Optional<GameStateResponse> findInProgress(Long userId, Long caseId) {
        return findActiveSession(userId, caseId).map(assembler::assemble);
    }

    /** 존재(404) → 소유자(403) 순서. 종료된 세션과 비공개·검증 전 사건의 세션도 조회할 수 있다. */
    @Transactional(readOnly = true)
    public GameStateResponse getState(Long userId, Long sessionId) {
        PlaySession session = playSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("수사 기록을 찾을 수 없습니다."));
        if (!session.getUser().getId().equals(userId)) {
            throw new NotSessionOwnerException();
        }
        return assembler.assemble(session);
    }

    private Optional<PlaySession> findActiveSession(Long userId, Long caseId) {
        return playSessionRepository.findByUserIdAndCaseFileIdAndStatus(userId, caseId, SessionStatus.IN_PROGRESS);
    }
}
