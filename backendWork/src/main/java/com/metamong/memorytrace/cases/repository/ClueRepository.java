package com.metamong.memorytrace.cases.repository;

import com.metamong.memorytrace.cases.entity.Clue;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClueRepository extends JpaRepository<Clue, Long> {

    /** 사건의 모든 단서를 판정 규칙과 함께 표시 순서로 읽는다. */
    @Query("""
            select c from Clue c
            left join fetch c.rule
            where c.caseFile.id = :caseId
            order by c.displayOrder, c.id
            """)
    List<Clue> findByCaseIdWithRule(@Param("caseId") Long caseId);
}
