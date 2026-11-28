package com.metamong.memorytrace.cases.entity;

import com.metamong.memorytrace.pokemon.entity.Pokemon;
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

/**
 * 정답과 진상이 들어 있으므로 응답으로 직접 내보내지 않는다.
 * enabled(사람의 공개 의사)를 바꾸는 메서드는 두지 않는다. 검증 결과는 validation_status, validated_at만
 * 갱신하는 쿼리로 쓴다(엔티티를 저장하면 모든 컬럼이 UPDATE되어 enabled를 덮어쓸 수 있다).
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "case_file", uniqueConstraints = {
        @UniqueConstraint(name = "uk_case_code", columnNames = {"case_code"})
})
public class CaseFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "case_id")
    private Long id;

    @Column(name = "case_code", nullable = false, length = 10)
    private String caseCode;

    @Column(name = "title", nullable = false, length = 50)
    private String title;

    @Column(name = "subtitle", length = 100)
    private String subtitle;

    @Column(name = "briefing", nullable = false, columnDefinition = "text")
    private String briefing;

    @Column(name = "scene_image_path", nullable = false, length = 255)
    private String sceneImagePath;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "answer_pokemon_id", nullable = false)
    private Pokemon answerPokemon;

    @Column(name = "truth_text", nullable = false, columnDefinition = "text")
    private String truthText;

    @Column(name = "motive_text", nullable = false, columnDefinition = "text")
    private String motiveText;

    @Column(name = "twist_text", nullable = false, columnDefinition = "text")
    private String twistText;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "is_enabled", nullable = false)
    private boolean enabled;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "validation_status", nullable = false, length = 10)
    private ValidationStatus validationStatus;

    @Column(name = "validated_at")
    private LocalDateTime validatedAt;
}
