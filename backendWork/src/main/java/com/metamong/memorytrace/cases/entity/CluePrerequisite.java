package com.metamong.memorytrace.cases.entity;

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
@Table(name = "clue_prerequisite", uniqueConstraints = {
        @UniqueConstraint(name = "uk_prereq_pair", columnNames = {"clue_id", "required_clue_id"})
})
public class CluePrerequisite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "prerequisite_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "clue_id", nullable = false)
    private Clue clue;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "required_clue_id", nullable = false)
    private Clue requiredClue;
}
