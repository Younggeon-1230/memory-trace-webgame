package com.metamong.memorytrace.cases.dto;

import com.metamong.memorytrace.cases.entity.CaseFile;
import com.metamong.memorytrace.game.entity.Grade;
import com.metamong.memorytrace.game.entity.SessionStatus;
import com.metamong.memorytrace.game.repository.SessionRecordRow;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 사건 목록의 한 항목 (프론트의 CaseSummary).
 * 정답·진상·소개문·검증 상태는 담지 않는다 (docs/backend_design.md 4.5).
 */
public record CaseSummaryResponse(
        Long caseId,
        String caseCode,
        String title,
        String subtitle,
        String sceneImagePath,
        MyStatus myStatus) {

    /** @param myRows 이 사건에 대한 내 세션 */
    public static CaseSummaryResponse of(CaseFile caseFile, List<SessionRecordRow> myRows) {
        return new CaseSummaryResponse(
                caseFile.getId(),
                caseFile.getCaseCode(),
                caseFile.getTitle(),
                Objects.requireNonNullElse(caseFile.getSubtitle(), ""),
                caseFile.getSceneImagePath(),
                MyStatus.from(myRows));
    }

    /** 값이 없는 필드는 생략하지 않고 null로 보낸다. */
    public record MyStatus(Long inProgressSessionId, int playCount, Grade bestGrade, Integer bestScore) {

        /** 해결한 세션 중 점수가 가장 높은 것. 동점이면 먼저 만든(ID가 작은) 세션. */
        private static final Comparator<SessionRecordRow> BEST_FIRST =
                Comparator.comparingInt(SessionRecordRow::score).reversed()
                        .thenComparing(SessionRecordRow::sessionId);

        static MyStatus from(List<SessionRecordRow> rows) {
            Long inProgressSessionId = rows.stream()
                    .filter(row -> row.status() == SessionStatus.IN_PROGRESS)
                    .map(SessionRecordRow::sessionId)
                    .findFirst()
                    .orElse(null);
            // 끝난 수사(SOLVED, FAILED)만 센다
            int playCount = (int) rows.stream()
                    .filter(row -> row.status() != SessionStatus.IN_PROGRESS)
                    .count();
            Optional<SessionRecordRow> best = rows.stream()
                    .filter(row -> row.status() == SessionStatus.SOLVED)
                    .min(BEST_FIRST);
            return new MyStatus(
                    inProgressSessionId,
                    playCount,
                    best.map(SessionRecordRow::grade).orElse(null),
                    best.map(SessionRecordRow::score).orElse(null));
        }
    }
}
