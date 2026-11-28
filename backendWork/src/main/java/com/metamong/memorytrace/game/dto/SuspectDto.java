package com.metamong.memorytrace.game.dto;

import com.metamong.memorytrace.cases.entity.CaseSuspect;
import com.metamong.memorytrace.game.entity.SessionSuspect;
import com.metamong.memorytrace.game.entity.SuspectStatus;
import com.metamong.memorytrace.pokemon.entity.Pokemon;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

public record SuspectDto(
        Long caseSuspectId,
        Integer pokemonId,
        String nameKo,
        String spritePath,
        BigDecimal heightM,
        BigDecimal weightKg,
        List<String> types,
        String color,
        String shape,
        List<TraitDto> traits,
        String suspicionText,
        SuspectStatus status,
        Long basisClueId) {

    /** m·kg 변환은 표시용으로 여기에서만 한다. 판정은 DB의 dm·hg 정수로 한다. */
    public static SuspectDto of(SessionSuspect sessionSuspect, List<TraitDto> traits) {
        CaseSuspect caseSuspect = sessionSuspect.getCaseSuspect();
        Pokemon pokemon = caseSuspect.getPokemon();
        return new SuspectDto(
                caseSuspect.getId(),
                pokemon.getId(),
                pokemon.getNameKo(),
                pokemon.getSpritePath(),
                BigDecimal.valueOf(pokemon.getHeightDm(), 1),
                BigDecimal.valueOf(pokemon.getWeightHg(), 1),
                Stream.of(pokemon.getType1(), pokemon.getType2()).filter(Objects::nonNull).toList(),
                pokemon.getColor(),
                pokemon.getShape(),
                traits,
                Objects.requireNonNullElse(caseSuspect.getSuspicionText(), ""),
                sessionSuspect.getStatus(),
                sessionSuspect.getBasisClue() == null ? null : sessionSuspect.getBasisClue().getId());
    }
}
