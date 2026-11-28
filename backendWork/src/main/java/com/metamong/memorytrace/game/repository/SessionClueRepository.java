package com.metamong.memorytrace.game.repository;

import com.metamong.memorytrace.game.entity.SessionClue;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SessionClueRepository extends JpaRepository<SessionClue, Long> {

    /** 세션이 수집한 단서 기록. 단서 자체는 사건의 단서 목록에서 ID로 찾는다. */
    List<SessionClue> findBySessionId(Long sessionId);
}
