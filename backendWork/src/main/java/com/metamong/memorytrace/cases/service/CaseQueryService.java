package com.metamong.memorytrace.cases.service;

import com.metamong.memorytrace.cases.dto.CaseSummaryResponse;
import com.metamong.memorytrace.cases.repository.CaseFileRepository;
import com.metamong.memorytrace.game.repository.PlaySessionRepository;
import com.metamong.memorytrace.game.repository.SessionRecordRow;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CaseQueryService {

    private final CaseFileRepository caseFileRepository;
    private final PlaySessionRepository playSessionRepository;

    /** 공개 중인 사건과 각 사건에 대한 내 기록. 사건 수와 관계없이 두 번 조회한다. */
    @Transactional(readOnly = true)
    public List<CaseSummaryResponse> findCases(Long userId) {
        Map<Long, List<SessionRecordRow>> myRowsByCase = playSessionRepository.findRecordRowsByUserId(userId)
                .stream()
                .collect(Collectors.groupingBy(SessionRecordRow::caseId));
        return caseFileRepository.findPublished().stream()
                .map(caseFile -> CaseSummaryResponse.of(
                        caseFile, myRowsByCase.getOrDefault(caseFile.getId(), List.of())))
                .toList();
    }
}
