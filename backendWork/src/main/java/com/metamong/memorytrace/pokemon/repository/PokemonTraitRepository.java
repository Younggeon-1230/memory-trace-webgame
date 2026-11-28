package com.metamong.memorytrace.pokemon.repository;

import com.metamong.memorytrace.pokemon.entity.PokemonTrait;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PokemonTraitRepository extends JpaRepository<PokemonTrait, Long> {
}
