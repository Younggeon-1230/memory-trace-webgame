package com.metamong.memorytrace.pokemon.repository;

import com.metamong.memorytrace.pokemon.entity.Pokemon;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PokemonRepository extends JpaRepository<Pokemon, Integer> {
}
