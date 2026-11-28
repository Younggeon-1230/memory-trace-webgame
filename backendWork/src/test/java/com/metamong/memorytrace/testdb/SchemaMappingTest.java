package com.metamong.memorytrace.testdb;

import static org.assertj.core.api.Assertions.assertThat;

import com.metamong.memorytrace.auth.entity.User;
import com.metamong.memorytrace.cases.entity.CaseFile;
import com.metamong.memorytrace.cases.entity.CaseSuspect;
import com.metamong.memorytrace.cases.entity.Clue;
import com.metamong.memorytrace.cases.entity.CluePrerequisite;
import com.metamong.memorytrace.cases.entity.EvidenceRule;
import com.metamong.memorytrace.game.entity.PlaySession;
import com.metamong.memorytrace.game.entity.SessionAction;
import com.metamong.memorytrace.game.entity.SessionClue;
import com.metamong.memorytrace.game.entity.SessionSuspect;
import com.metamong.memorytrace.pokemon.entity.Pokemon;
import com.metamong.memorytrace.pokemon.entity.PokemonTrait;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.dialect.MySQLDialect;
import org.hibernate.mapping.Column;
import org.hibernate.mapping.Table;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.orm.jpa.hibernate.SpringImplicitNamingStrategy;

/**
 * DB 없이 엔티티 매핑과 01_schema.sql을 비교한다.
 * ddl-auto=validate가 검사하지 않는 길이, NULL 허용, UNIQUE까지 본다.
 *
 * <p><b>보조 테스트다.</b> 네이밍 전략을 여기서 직접 지정하므로 애플리케이션 설정이 바뀌어도 따라가지 않으며,
 * CHECK, FK, 기본값은 보지 않는다. 매핑의 최종 판단은 실제 MySQL에서의 ddl-auto=validate와
 * {@code EntityMappingIntegrationTest}로 한다.
 */
class SchemaMappingTest {

    private static final List<Class<?>> ENTITIES = List.of(
            User.class, Pokemon.class, PokemonTrait.class, CaseFile.class, CaseSuspect.class, EvidenceRule.class,
            Clue.class, CluePrerequisite.class, PlaySession.class, SessionClue.class, SessionSuspect.class,
            SessionAction.class);

    private static final Pattern TABLE_NAME = Pattern.compile("^CREATE TABLE (\\w+)");
    private static final Pattern COLUMN = Pattern.compile(
            "^\\s+([a-z]\\w*)\\s+(BIGINT|INT|VARCHAR\\(\\d+\\)|TEXT|BOOLEAN|DECIMAL\\(\\d+,\\d+\\)|TINYINT|CHAR\\(\\d+\\)|DATETIME)(?=\\s|,|$)(.*)$");
    private static final Pattern UNIQUE_KEY = Pattern.compile("UNIQUE KEY (\\w+) \\(([^)]*)\\)");

    /** 표 이름 → 설명 줄 집합. 컬럼은 "col 이름 타입 null여부", UNIQUE는 "uk 이름 (컬럼들)" */
    private static Map<String, TreeSet<String>> fromSchema() {
        Map<String, TreeSet<String>> tables = new TreeMap<>();
        for (String sql : SqlScript.parse(TestDatabaseInitializer.read(TestDatabaseInitializer.SCHEMA))) {
            Matcher name = TABLE_NAME.matcher(sql);
            assertThat(name.find()).isTrue();
            TreeSet<String> lines = tables.computeIfAbsent(name.group(1), key -> new TreeSet<>());
            sql.lines().forEach(line -> {
                Matcher column = COLUMN.matcher(line);
                if (column.matches()) {
                    String type = switch (column.group(2)) {
                        case "INT" -> "integer";
                        case "BOOLEAN" -> "bit"; // MySQL의 TINYINT(1)을 Hibernate와 드라이버는 bit로 본다
                        default -> column.group(2).toLowerCase();
                    };
                    boolean notNull = column.group(3).contains("NOT NULL");
                    lines.add("col " + column.group(1) + " " + type + (notNull ? " not null" : " null"));
                }
            });
            Matcher unique = UNIQUE_KEY.matcher(sql);
            while (unique.find()) {
                lines.add("uk " + unique.group(1) + " (" + unique.group(2).replace(" ", "") + ")");
            }
        }
        return tables;
    }

    private static Map<String, TreeSet<String>> fromEntities() {
        StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                .applySetting(AvailableSettings.DIALECT, MySQLDialect.class.getName())
                .applySetting("hibernate.boot.allow_jdbc_metadata_access", false)
                .build();
        try {
            MetadataSources sources = new MetadataSources(registry);
            ENTITIES.forEach(sources::addAnnotatedClass);
            // 애플리케이션이 쓰는 스프링 부트 기본 네이밍 전략과 같게 맞춘다
            Metadata metadata = sources.getMetadataBuilder()
                    .applyPhysicalNamingStrategy(
                            new org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy())
                    .applyImplicitNamingStrategy(new SpringImplicitNamingStrategy())
                    .build();

            Map<String, TreeSet<String>> tables = new TreeMap<>();
            metadata.getEntityBindings().forEach(binding -> {
                Table table = binding.getTable();
                TreeSet<String> lines = tables.computeIfAbsent(table.getName(), key -> new TreeSet<>());
                for (Column column : table.getColumns()) {
                    String type = column.getSqlType(metadata).toLowerCase().replace("datetime(6)", "datetime");
                    boolean primaryKey = table.getPrimaryKey().getColumns().contains(column);
                    boolean notNull = primaryKey || !column.isNullable();
                    lines.add("col " + column.getName() + " " + type + (notNull ? " not null" : " null"));
                }
                table.getUniqueKeys().values().forEach(key -> lines.add("uk " + key.getName() + " ("
                        + key.getColumns().stream().map(Column::getName).collect(Collectors.joining(",")) + ")"));
            });
            return tables;
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }

    @Test
    @DisplayName("12개 엔티티의 컬럼명·타입·길이·NULL 허용·UNIQUE가 스키마와 같다")
    void entitiesMatchSchema() {
        Map<String, TreeSet<String>> schema = fromSchema();
        Map<String, TreeSet<String>> entities = fromEntities();

        assertThat(schema).hasSize(12);
        // 파서가 컬럼을 놓쳐도 통과하지 않도록 스키마의 전체 컬럼 수를 고정한다
        assertThat(schema.values().stream().flatMap(TreeSet::stream).filter(line -> line.startsWith("col ")))
                .hasSize(100);
        assertThat(entities.keySet()).isEqualTo(schema.keySet());
        schema.forEach((table, expected) ->
                assertThat(entities.get(table)).as("table %s", table).isEqualTo(expected));
    }
}
