package com.metamong.memorytrace.cases.repository;

import com.metamong.memorytrace.cases.entity.CaseFile;
import com.metamong.memorytrace.cases.entity.ValidationStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CaseFileRepository extends JpaRepository<CaseFile, Long> {

    /**
     * 공개 중인 사건 (D26): 사람이 켜 두었고(is_enabled) 검증을 통과한(VALID) 사건을 표시 순서로.
     * 공개 조건은 이 리포지토리에만 정의한다. 목록, 새 수사 시작, 기록 요약이 함께 쓴다.
     */
    default List<CaseFile> findPublished() {
        return findByEnabledTrueAndValidationStatusOrderByDisplayOrderAscIdAsc(ValidationStatus.VALID);
    }

    /** 공개 중인 사건 하나. 조건은 findPublished()와 같다. */
    default Optional<CaseFile> findPublishedById(Long caseId) {
        return findByIdAndEnabledTrueAndValidationStatus(caseId, ValidationStatus.VALID);
    }

    /** findPublished()를 통해서만 부른다. */
    List<CaseFile> findByEnabledTrueAndValidationStatusOrderByDisplayOrderAscIdAsc(ValidationStatus status);

    /** findPublishedById()를 통해서만 부른다. */
    Optional<CaseFile> findByIdAndEnabledTrueAndValidationStatus(Long caseId, ValidationStatus status);
}
