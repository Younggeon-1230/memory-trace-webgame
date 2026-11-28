package com.metamong.memorytrace.game.entity;

import com.metamong.memorytrace.cases.entity.CaseSuspect;
import com.metamong.memorytrace.cases.entity.Clue;
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

/** 행동 기록. 추가만 하고 수정하지 않는다. */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "session_action", uniqueConstraints = {
        @UniqueConstraint(name = "uk_action_request", columnNames = {"request_id"}),
        @UniqueConstraint(name = "uk_action_dedup", columnNames = {"session_id", "dedup_key"})
})
public class SessionAction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "action_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private PlaySession session;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "action_type", nullable = false, length = 12)
    private ActionType actionType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "clue_id")
    private Clue clue;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "case_suspect_id")
    private CaseSuspect caseSuspect;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "result", nullable = false, length = 10)
    private ActionResult result;

    @Column(name = "score_delta", nullable = false)
    private int scoreDelta;

    @Column(name = "dedup_key", length = 60)
    private String dedupKey;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "request_id", length = 36)
    private String requestId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
