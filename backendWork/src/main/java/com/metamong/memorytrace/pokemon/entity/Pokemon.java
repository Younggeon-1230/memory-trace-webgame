package com.metamong.memorytrace.pokemon.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** PokéAPI 캐시. 전국도감 번호가 PK다. 판정은 heightDm, weightHg 정수값으로만 한다. */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "pokemon", uniqueConstraints = {
        @UniqueConstraint(name = "uk_pokemon_name_en", columnNames = {"name_en"})
})
public class Pokemon {

    @Id
    @Column(name = "pokemon_id")
    private Integer id;

    @Column(name = "name_en", nullable = false, length = 30)
    private String nameEn;

    @Column(name = "name_ko", nullable = false, length = 30)
    private String nameKo;

    @Column(name = "height_dm", nullable = false)
    private int heightDm;

    @Column(name = "weight_hg", nullable = false)
    private int weightHg;

    @Column(name = "type1", nullable = false, length = 20)
    private String type1;

    @Column(name = "type2", length = 20)
    private String type2;

    @Column(name = "color", nullable = false, length = 20)
    private String color;

    @Column(name = "shape", nullable = false, length = 20)
    private String shape;

    @Column(name = "sprite_path", nullable = false, length = 255)
    private String spritePath;

    @Column(name = "synced_at", nullable = false)
    private LocalDateTime syncedAt;
}
