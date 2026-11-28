package com.metamong.memorytrace.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.metamong.memorytrace.cases.entity.CaseSuspect;
import com.metamong.memorytrace.cases.entity.ClueKind;
import com.metamong.memorytrace.common.config.JacksonConfig;
import com.metamong.memorytrace.game.dto.CaseInfoDto;
import com.metamong.memorytrace.game.dto.ClueDto;
import com.metamong.memorytrace.game.dto.GameStateResponse;
import com.metamong.memorytrace.game.dto.SuspectDto;
import com.metamong.memorytrace.game.entity.SessionStatus;
import com.metamong.memorytrace.game.entity.SessionSuspect;
import com.metamong.memorytrace.game.entity.SuspectStatus;
import com.metamong.memorytrace.pokemon.entity.Pokemon;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

/** 게임 상태의 표시값 변환과 JSON 모양 (docs/backend_design.md 4.7). DB 없이 실행한다. */
class GameStateJsonTest {

    private final ObjectMapper objectMapper = mapper();

    private static ObjectMapper mapper() {
        Jackson2ObjectMapperBuilder builder = Jackson2ObjectMapperBuilder.json();
        new JacksonConfig().dateTimeFormatCustomizer().customize(builder);
        return builder.build();
    }

    private static SessionSuspect suspect(String type2, String suspicionText) {
        Pokemon pokemon = mock(Pokemon.class);
        when(pokemon.getId()).thenReturn(21);
        when(pokemon.getHeightDm()).thenReturn(3);
        when(pokemon.getWeightHg()).thenReturn(20);
        when(pokemon.getType1()).thenReturn("normal");
        when(pokemon.getType2()).thenReturn(type2);
        CaseSuspect caseSuspect = mock(CaseSuspect.class);
        when(caseSuspect.getPokemon()).thenReturn(pokemon);
        when(caseSuspect.getSuspicionText()).thenReturn(suspicionText);
        SessionSuspect sessionSuspect = mock(SessionSuspect.class);
        when(sessionSuspect.getCaseSuspect()).thenReturn(caseSuspect);
        when(sessionSuspect.getStatus()).thenReturn(SuspectStatus.ACTIVE);
        return sessionSuspect;
    }

    @Test
    void convertsDecimetersAndHectograms() {
        SuspectDto dto = SuspectDto.of(suspect("flying", "의심"), List.of());

        assertThat(dto.heightM()).isEqualByComparingTo(new BigDecimal("0.3"));
        assertThat(dto.weightKg()).isEqualByComparingTo(new BigDecimal("2"));
        assertThat(dto.types()).containsExactly("normal", "flying");
        assertThat(dto.basisClueId()).isNull();
    }

    @Test
    void singleTypeAndMissingSuspicionText() {
        SuspectDto dto = SuspectDto.of(suspect(null, null), List.of());

        assertThat(dto.types()).containsExactly("normal");
        assertThat(dto.suspicionText()).isEmpty();
    }

    @Test
    void caseFieldNameAndDateFormat() throws JsonProcessingException {
        ClueDto clue = new ClueDto(20L, "E1", ClueKind.SCENE, "이름", "요약", "내용",
                LocalDateTime.of(2026, 12, 1, 20, 1, 0));
        GameStateResponse state = new GameStateResponse(1L, SessionStatus.IN_PROGRESS, 100, 2, 0,
                new CaseInfoDto(3L, "CASE03", "제목", "소개", "/scenes/a.webp"),
                List.of(), List.of(clue), List.of(), List.of(SuspectDto.of(suspect(null, null), List.of())));

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(state));

        assertThat(json.has("case")).isTrue();
        assertThat(json.has("caseInfo")).isFalse();
        // 초가 0이어도 생략하지 않는다
        assertThat(json.get("clues").get(0).get("collectedAt").asText()).isEqualTo("2026-12-01T20:01:00");
        // 값이 없으면 필드를 생략하지 않고 null로 보낸다
        assertThat(json.get("suspects").get(0).has("basisClueId")).isTrue();
        assertThat(json.get("suspects").get(0).get("basisClueId").isNull()).isTrue();
    }
}
