package com.metamong.memorytrace.game.repository;

import com.metamong.memorytrace.game.entity.SessionSuspect;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SessionSuspectRepository extends JpaRepository<SessionSuspect, Long> {

    /** 세션의 용의자를 포켓몬·배제 근거와 함께 사건의 표시 순서로 읽는다. */
    @Query("""
            select s from SessionSuspect s
            join fetch s.caseSuspect cs
            join fetch cs.pokemon
            left join fetch s.basisClue
            where s.session.id = :sessionId
            order by cs.displayOrder, cs.id
            """)
    List<SessionSuspect> findBySessionIdWithDetail(@Param("sessionId") Long sessionId);
}
