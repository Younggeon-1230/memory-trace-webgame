package com.metamong.memorytrace.testdb;

import static org.assertj.core.api.Assertions.assertThat;

import com.metamong.memorytrace.auth.repository.UserRepository;
import com.metamong.memorytrace.cases.entity.CaseFile;
import com.metamong.memorytrace.cases.entity.Clue;
import com.metamong.memorytrace.cases.entity.ClueKind;
import com.metamong.memorytrace.cases.entity.ValidationStatus;
import com.metamong.memorytrace.cases.repository.CaseFileRepository;
import com.metamong.memorytrace.cases.repository.CaseSuspectRepository;
import com.metamong.memorytrace.cases.repository.CluePrerequisiteRepository;
import com.metamong.memorytrace.cases.repository.ClueRepository;
import com.metamong.memorytrace.cases.repository.EvidenceRuleRepository;
import com.metamong.memorytrace.game.entity.SessionStatus;
import com.metamong.memorytrace.game.repository.PlaySessionRepository;
import com.metamong.memorytrace.game.repository.SessionActionRepository;
import com.metamong.memorytrace.game.repository.SessionClueRepository;
import com.metamong.memorytrace.game.repository.SessionSuspectRepository;
import com.metamong.memorytrace.judge.ConditionType;
import com.metamong.memorytrace.pokemon.repository.PokemonRepository;
import com.metamong.memorytrace.pokemon.repository.PokemonTraitRepository;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/** 컨텍스트가 뜬다는 것은 실제 MySQL에서 ddl-auto=validate를 통과했다는 뜻이다. 그 위에서 12개 엔티티를 읽는다. */
@Transactional(readOnly = true)
class EntityMappingIntegrationTest extends IntegrationTestSupport {

    @Autowired DataSource dataSource;
    @Autowired UserRepository users;
    @Autowired PokemonRepository pokemon;
    @Autowired PokemonTraitRepository traits;
    @Autowired CaseFileRepository cases;
    @Autowired CaseSuspectRepository suspects;
    @Autowired EvidenceRuleRepository rules;
    @Autowired ClueRepository clues;
    @Autowired CluePrerequisiteRepository prerequisites;
    @Autowired PlaySessionRepository sessions;
    @Autowired SessionClueRepository sessionClues;
    @Autowired SessionSuspectRepository sessionSuspects;
    @Autowired SessionActionRepository actions;

    @Test
    @DisplayName("애플리케이션도 테스트 DB에 연결되어 있다")
    void usesTestDatabase() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            TestDatabaseGuard.verify(connection);
        }
    }

    @Test
    @DisplayName("12개 엔티티를 모두 읽는다")
    void readsEveryEntity() {
        assertThat(users.findAll()).hasSize(2);
        assertThat(pokemon.findAll()).hasSize(12);
        assertThat(traits.findAll()).hasSize(3);
        assertThat(cases.findAll()).hasSize(3);
        assertThat(suspects.findAll()).hasSize(12);
        assertThat(rules.findAll()).hasSize(7);
        assertThat(clues.findAll()).hasSize(25);
        assertThat(prerequisites.findAll()).hasSize(14);
        assertThat(sessions.findAll()).hasSize(1);
        assertThat(sessionClues.findAll()).hasSize(6);
        assertThat(sessionSuspects.findAll()).hasSize(4);
        assertThat(actions.findAll()).hasSize(12);
    }

    @Test
    @DisplayName("한글, Enum, DECIMAL, TINYINT, NULL 값이 그대로 읽힌다")
    void readsColumnValues() {
        CaseFile case3 = cases.findById(3L).orElseThrow();
        assertThat(case3.getSubtitle()).isEqualTo("남겨진 발자국의 모순을 풀어라");
        assertThat(case3.isEnabled()).isTrue();
        assertThat(case3.getValidationStatus()).isEqualTo(ValidationStatus.PENDING);
        assertThat(case3.getValidatedAt()).isNull();
        assertThat(case3.getAnswerPokemon().getNameKo()).isEqualTo("이브이");

        Clue footprint = clues.findById(20L).orElseThrow();
        assertThat(footprint.getKind()).isEqualTo(ClueKind.SCENE);
        assertThat(footprint.getAreaX()).isEqualByComparingTo(new BigDecimal("0.5140"));
        assertThat(footprint.getRule().getConditionType()).isEqualTo(ConditionType.SHAPE_IS);

        assertThat(sessions.findById(1L).orElseThrow().getStatus()).isEqualTo(SessionStatus.SOLVED);
        assertThat(sessions.findById(1L).orElseThrow().getActiveFlag()).isNull();
        assertThat(actions.findAll())
                .filteredOn(action -> action.getRequestId() != null)
                .hasSize(11)
                .allSatisfy(action -> assertThat(action.getRequestId()).hasSize(36));
    }
}
