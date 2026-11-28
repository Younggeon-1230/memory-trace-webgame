package com.metamong.memorytrace.cases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.metamong.memorytrace.cases.dto.CaseSummaryResponse;
import com.metamong.memorytrace.cases.dto.CaseSummaryResponse.MyStatus;
import com.metamong.memorytrace.cases.entity.CaseFile;
import com.metamong.memorytrace.game.entity.Grade;
import com.metamong.memorytrace.game.entity.SessionStatus;
import com.metamong.memorytrace.game.repository.SessionRecordRow;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 사건 목록의 내 기록 계산 (docs/backend_design.md 4.5). DB 없이 실행한다. */
class CaseSummaryResponseTest {

    private static MyStatus statusOf(SessionRecordRow... rows) {
        return CaseSummaryResponse.of(mock(CaseFile.class), List.of(rows)).myStatus();
    }

    private static SessionRecordRow solved(long id, int score, Grade grade) {
        return new SessionRecordRow(id, 3L, SessionStatus.SOLVED, score, grade);
    }

    private static SessionRecordRow failed(long id) {
        return new SessionRecordRow(id, 3L, SessionStatus.FAILED, 0, null);
    }

    private static SessionRecordRow inProgress(long id, int score) {
        return new SessionRecordRow(id, 3L, SessionStatus.IN_PROGRESS, score, null);
    }

    @Test
    void noRecord() {
        assertThat(statusOf()).isEqualTo(new MyStatus(null, 0, null, null));
    }

    @Test
    void failedOnlyHasNoBest() {
        assertThat(statusOf(failed(1), failed(2))).isEqualTo(new MyStatus(null, 2, null, null));
    }

    @Test
    void inProgressIsNotCountedAndItsScoreIsNotBest() {
        assertThat(statusOf(inProgress(7, 100))).isEqualTo(new MyStatus(7L, 0, null, null));
    }

    @Test
    void bestIsHighestSolvedScore() {
        assertThat(statusOf(solved(1, 70, Grade.B), solved(2, 110, Grade.S), failed(3),
                solved(4, 90, Grade.A), inProgress(5, 100)))
                .isEqualTo(new MyStatus(5L, 4, Grade.S, 110));
    }

    @Test
    void tieIsWonByEarlierSessionRegardlessOfInputOrder() {
        // 같은 100점이라도 등급이 다른 값을 넣어 어느 세션을 골랐는지 구분한다
        assertThat(statusOf(solved(9, 100, Grade.B), solved(4, 100, Grade.A)).bestGrade()).isEqualTo(Grade.A);
    }

    @Test
    void nullSubtitleBecomesEmptyString() {
        CaseFile caseFile = mock(CaseFile.class);
        when(caseFile.getSubtitle()).thenReturn(null);

        assertThat(CaseSummaryResponse.of(caseFile, List.of()).subtitle()).isEmpty();
    }
}
