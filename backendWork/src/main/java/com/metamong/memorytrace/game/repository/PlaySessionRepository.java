package com.metamong.memorytrace.game.repository;

import com.metamong.memorytrace.game.entity.PlaySession;
import com.metamong.memorytrace.game.entity.SessionStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlaySessionRepository extends JpaRepository<PlaySession, Long> {

    /** 한 회원의 모든 세션을 쿼리 한 번으로 읽는다. */
    @Query("""
            select new com.metamong.memorytrace.game.repository.SessionRecordRow(
                s.id, s.caseFile.id, s.status, s.score, s.grade)
            from PlaySession s
            where s.user.id = :userId
            order by s.id
            """)
    List<SessionRecordRow> findRecordRowsByUserId(@Param("userId") Long userId);

    /** 회원의 사건별 진행 중 세션. UNIQUE(user_id, case_id, active_flag)로 최대 1개다. */
    Optional<PlaySession> findByUserIdAndCaseFileIdAndStatus(Long userId, Long caseId, SessionStatus status);
}
