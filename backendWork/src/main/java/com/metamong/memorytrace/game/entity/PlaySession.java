package com.metamong.memorytrace.game.entity;

import com.metamong.memorytrace.auth.entity.User;
import com.metamong.memorytrace.cases.entity.CaseFile;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "play_session", uniqueConstraints = {
        @UniqueConstraint(name = "uk_session_active", columnNames = {"user_id", "case_id", "active_flag"})
})
public class PlaySession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "session_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "case_id", nullable = false)
    private CaseFile caseFile;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 12)
    private SessionStatus status;

    @Column(name = "score", nullable = false)
    private int score;

    @Column(name = "wrong_exclusion_count", nullable = false)
    private int wrongExclusionCount;

    @Column(name = "accusation_count", nullable = false)
    private int accusationCount;

    @Column(name = "bonus_awarded", nullable = false)
    private boolean bonusAwarded;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "grade", length = 2)
    private Grade grade;

    @Column(name = "active_flag")
    private Byte activeFlag;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    /** 새 수사. DB 기본값에 맡기지 않고 모든 값을 채운다 (docs/backend_design.md 3.4 R7). */
    public static PlaySession start(User user, CaseFile caseFile, int startScore, LocalDateTime startedAt) {
        PlaySession session = new PlaySession();
        session.user = user;
        session.caseFile = caseFile;
        session.status = SessionStatus.IN_PROGRESS;
        session.score = startScore;
        session.wrongExclusionCount = 0;
        session.accusationCount = 0;
        session.bonusAwarded = false;
        session.activeFlag = (byte) 1;
        session.startedAt = startedAt;
        return session;
    }
}
