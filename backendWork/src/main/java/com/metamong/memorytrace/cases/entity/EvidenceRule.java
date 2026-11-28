package com.metamong.memorytrace.cases.entity;

import com.metamong.memorytrace.judge.ConditionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "evidence_rule")
public class EvidenceRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "rule_id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "condition_type", nullable = false, length = 20)
    private ConditionType conditionType;

    @Column(name = "condition_value", nullable = false, length = 40)
    private String conditionValue;

    @Column(name = "basis_text", nullable = false, length = 255)
    private String basisText;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "source_type", nullable = false, length = 20)
    private RuleSourceType sourceType;

    @Column(name = "source_ref", length = 100)
    private String sourceRef;
}
