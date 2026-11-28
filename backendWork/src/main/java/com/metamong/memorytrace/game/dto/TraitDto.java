package com.metamong.memorytrace.game.dto;

import com.metamong.memorytrace.pokemon.entity.PokemonTrait;

public record TraitDto(String traitCode, String description) {

    public static TraitDto from(PokemonTrait trait) {
        return new TraitDto(trait.getTraitCode(), trait.getDescription());
    }
}
