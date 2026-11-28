package com.metamong.memorytrace.cases.repository;

import com.metamong.memorytrace.cases.entity.CaseFile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CaseFileRepository extends JpaRepository<CaseFile, Long> {
}
