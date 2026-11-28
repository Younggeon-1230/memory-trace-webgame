package com.metamong.memorytrace.cases.entity;

import com.metamong.memorytrace.pokemon.entity.Pokemon;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "case_suspect", uniqueConstraints = {
        @UniqueConstraint(name = "uk_suspect_case_pokemon", columnNames = {"case_id", "pokemon_id"})
})
public class CaseSuspect {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "case_suspect_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "case_id", nullable = false)
    private CaseFile caseFile;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pokemon_id", nullable = false)
    private Pokemon pokemon;

    @Column(name = "suspicion_text", length = 255)
    private String suspicionText;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;
}
