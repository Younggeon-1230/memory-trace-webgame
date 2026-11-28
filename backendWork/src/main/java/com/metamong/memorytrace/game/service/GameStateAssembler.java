package com.metamong.memorytrace.game.service;

import com.metamong.memorytrace.cases.entity.Clue;
import com.metamong.memorytrace.cases.entity.ClueKind;
import com.metamong.memorytrace.cases.entity.EvidenceRule;
import com.metamong.memorytrace.cases.repository.ClueRepository;
import com.metamong.memorytrace.game.dto.CaseInfoDto;
import com.metamong.memorytrace.game.dto.ClueDto;
import com.metamong.memorytrace.game.dto.GameStateResponse;
import com.metamong.memorytrace.game.dto.SceneAreaDto;
import com.metamong.memorytrace.game.dto.SuspectDto;
import com.metamong.memorytrace.game.dto.TraceEntryDto;
import com.metamong.memorytrace.game.dto.TraitDto;
import com.metamong.memorytrace.game.entity.PlaySession;
import com.metamong.memorytrace.game.entity.SessionClue;
import com.metamong.memorytrace.game.entity.SessionSuspect;
import com.metamong.memorytrace.game.repository.SessionClueRepository;
import com.metamong.memorytrace.game.repository.SessionSuspectRepository;
import com.metamong.memorytrace.pokemon.repository.PokemonTraitRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** GameState를 만드는 유일한 곳. 호출하는 서비스의 트랜잭션 안에서만 부른다. */
@Component
@RequiredArgsConstructor
public class GameStateAssembler {

    private final ClueRepository clueRepository;
    private final SessionClueRepository sessionClueRepository;
    private final SessionSuspectRepository sessionSuspectRepository;
    private final PokemonTraitRepository pokemonTraitRepository;

    @Transactional(propagation = Propagation.MANDATORY)
    public GameStateResponse assemble(PlaySession session) {
        List<Clue> caseClues = clueRepository.findByCaseIdWithRule(session.getCaseFile().getId());
        Map<Long, LocalDateTime> collectedAt = sessionClueRepository.findBySessionId(session.getId()).stream()
                .collect(Collectors.toMap(row -> row.getClue().getId(), SessionClue::getCollectedAt));
        // 사건의 용의자가 아니라 세션의 용의자 행을 기준으로 한다 (7.5)
        List<SessionSuspect> suspects = sessionSuspectRepository.findBySessionIdWithDetail(session.getId());

        return new GameStateResponse(
                session.getId(),
                session.getStatus(),
                session.getScore(),
                ScorePolicy.MAX_ACCUSATIONS - session.getAccusationCount(),
                session.getWrongExclusionCount(),
                CaseInfoDto.from(session.getCaseFile()),
                caseClues.stream()
                        .filter(clue -> clue.getKind() == ClueKind.SCENE)
                        .map(clue -> SceneAreaDto.of(clue, collectedAt.containsKey(clue.getId())))
                        .toList(),
                // 수집한 단서만, 수집한 순서가 아니라 표시 순서로
                caseClues.stream()
                        .filter(clue -> collectedAt.containsKey(clue.getId()))
                        .map(clue -> ClueDto.of(clue, collectedAt.get(clue.getId())))
                        .toList(),
                traceDictionary(caseClues),
                suspectDtos(suspects));
    }

    /** 이 사건의 단서에 연결된 규칙 전부(수집 여부와 무관). 중복을 없애고 규칙 ID 순서로. */
    private static List<TraceEntryDto> traceDictionary(List<Clue> caseClues) {
        Map<Long, String> basisByRuleId = new TreeMap<>();
        for (Clue clue : caseClues) {
            EvidenceRule rule = clue.getRule();
            if (rule != null) {
                basisByRuleId.put(rule.getId(), rule.getBasisText());
            }
        }
        return basisByRuleId.values().stream().map(TraceEntryDto::new).toList();
    }

    private List<SuspectDto> suspectDtos(List<SessionSuspect> suspects) {
        List<Integer> pokemonIds = suspects.stream()
                .map(suspect -> suspect.getCaseSuspect().getPokemon().getId())
                .toList();
        Map<Integer, List<TraitDto>> traits = pokemonTraitRepository
                .findByPokemonIdInAndJudgmentTrueOrderById(pokemonIds).stream()
                .collect(Collectors.groupingBy(trait -> trait.getPokemon().getId(),
                        Collectors.mapping(TraitDto::from, Collectors.toList())));
        return suspects.stream()
                .map(suspect -> SuspectDto.of(suspect,
                        traits.getOrDefault(suspect.getCaseSuspect().getPokemon().getId(), List.of())))
                .toList();
    }
}
