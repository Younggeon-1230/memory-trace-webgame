package com.metamong.memorytrace.pokemon.entity;

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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "pokemon_trait", uniqueConstraints = {
        @UniqueConstraint(name = "uk_trait_pokemon_code", columnNames = {"pokemon_id", "trait_code"})
})
public class PokemonTrait {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "trait_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pokemon_id", nullable = false)
    private Pokemon pokemon;

    @Column(name = "trait_code", nullable = false, length = 40)
    private String traitCode;

    @Column(name = "description", nullable = false, length = 255)
    private String description;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "source_type", nullable = false, length = 20)
    private TraitSourceType sourceType;

    @Column(name = "source_text", columnDefinition = "text")
    private String sourceText;

    @Column(name = "source_version", length = 30)
    private String sourceVersion;

    @Column(name = "is_judgment", nullable = false)
    private boolean judgment;
}
