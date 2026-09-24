package com.tenure.global.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * 마이그레이션 스크립트 자체의 제약. DB 를 띄우지 않는다.
 *
 * <p>DDL 은 {@code docs/schema.yaml} 에서 생성하므로, 정본을 고치다 들어온 문자가
 * 조용히 SQL 로 흘러드는 것을 여기서 막는다.
 */
@DisplayName("마이그레이션 스크립트")
class MigrationScriptTest {

	@Test
	@DisplayName("BMP 밖 문자가 없다 — 코멘트가 utf8mb3 메타데이터에서 깨진다")
	void scripts_containNoSupplementaryCharacters() throws IOException {
		// MySQL 은 테이블·컬럼 코멘트를 character_set_system(utf8mb3 고정)으로 다룬다.
		// 이모지처럼 4바이트가 필요한 문자를 넣으면 CREATE TABLE 마다 경고 1300 이 뜨고
		// 저장된 값도 깨진다. 실제로 '🔖'(U+1F516) 가 한 번 흘러든 적이 있다.
		List<String> offenders = new ArrayList<>();

		for (Resource script : loadScripts()) {
			String sql = script.getContentAsString(StandardCharsets.UTF_8);
			sql.codePoints()
					.filter(cp -> cp > 0xFFFF)
					.distinct()
					.forEach(cp -> offenders.add(
							"%s: U+%04X %s".formatted(script.getFilename(), cp, new String(Character.toChars(cp)))));
		}

		assertThat(offenders).isEmpty();
	}

	@Test
	@DisplayName("스크립트가 UTF-8 로 읽히고 비어 있지 않다")
	void scripts_areReadableAndNonEmpty() throws IOException {
		Resource[] scripts = loadScripts();

		assertThat(scripts).isNotEmpty();
		for (Resource script : scripts) {
			assertThat(script.getContentAsString(StandardCharsets.UTF_8)).isNotBlank();
		}
	}

	private Resource[] loadScripts() throws IOException {
		return new PathMatchingResourcePatternResolver()
				.getResources("classpath:db/migration/*.sql");
	}
}
