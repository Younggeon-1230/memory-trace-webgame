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

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "session_suspect", uniqueConstraints = {
        @UniqueConstraint(name = "uk_session_suspect", columnNames = {"session_id", "case_suspect_id"})
})
public class SessionSuspect {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "session_suspect_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private PlaySession session;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "case_suspect_id", nullable = false)
    private CaseSuspect caseSuspect;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 30)
    private SuspectStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "basis_clue_id")
    private Clue basisClue;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
