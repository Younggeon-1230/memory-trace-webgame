package com.metamong.memorytrace.cases.repository;

import com.metamong.memorytrace.cases.entity.CaseSuspect;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CaseSuspectRepository extends JpaRepository<CaseSuspect, Long> {

    List<CaseSuspect> findByCaseFileIdOrderByDisplayOrderAscIdAsc(Long caseId);
}
