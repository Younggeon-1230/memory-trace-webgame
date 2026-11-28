package com.metamong.memorytrace.cases.entity;

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
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** rule, core, rebuttalText 는 클라이언트 공개 범위가 제한된다(설계 4.15). */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "clue", uniqueConstraints = {
        @UniqueConstraint(name = "uk_clue_case_code", columnNames = {"case_id", "clue_code"})
})
public class Clue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "clue_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "case_id", nullable = false)
    private CaseFile caseFile;

    @Column(name = "clue_code", nullable = false, length = 10)
    private String clueCode;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "clue_kind", nullable = false, length = 10)
    private ClueKind kind;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Column(name = "summary", nullable = false, length = 100)
    private String summary;

    @Column(name = "detail_text", nullable = false, columnDefinition = "text")
    private String detailText;

    @Column(name = "rebuttal_text", length = 255)
    private String rebuttalText;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rule_id")
    private EvidenceRule rule;

    @Column(name = "is_core", nullable = false)
    private boolean core;

    @Column(name = "area_x", precision = 5, scale = 4)
    private BigDecimal areaX;

    @Column(name = "area_y", precision = 5, scale = 4)
    private BigDecimal areaY;

    @Column(name = "area_w", precision = 5, scale = 4)
    private BigDecimal areaW;

    @Column(name = "area_h", precision = 5, scale = 4)
    private BigDecimal areaH;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;
}
