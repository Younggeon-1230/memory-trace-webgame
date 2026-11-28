package com.metamong.memorytrace.pokemon.repository;

import com.metamong.memorytrace.pokemon.entity.PokemonTrait;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PokemonTraitRepository extends JpaRepository<PokemonTrait, Long> {

    /** 화면에 보여 주는 특징(is_judgment = TRUE)만 읽는다. */
    List<PokemonTrait> findByPokemonIdInAndJudgmentTrueOrderById(Collection<Integer> pokemonIds);
}
