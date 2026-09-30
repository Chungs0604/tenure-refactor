package com.tenure.domain.item.enums;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * 추천 블록의 제목·부제.
 *
 * 화면에 그대로 나가는 문구라 피그마(2791:28973)에서 읽은 문자열을 그대로 단언한다 —
 * 문구가 바뀌면 이 테스트가 먼저 깨져야 한다.
 */
@DisplayName("추천 블록 문구")
class RecommendationBlockTest {

	@Nested
	@DisplayName("제목")
	class Title {

		@Test
		@DisplayName("기준 아이템이 안 들어가는 블록은 고정 문구다")
		void fixedTitles() {
			assertThat(RecommendationBlock.SIMILAR_MOOD.titleOf("Levis", "LVC 1955 501"))
					.isEqualTo("비슷한 무드 모아보기");
			assertThat(RecommendationBlock.GOOD_COORDI.titleOf("Levis", "LVC 1955 501"))
					.isEqualTo("함께 참고하기 좋은 코디");
		}

		@Test
		@DisplayName("비슷한 스타일은 기준 아이템 이름이 제목에 들어간다")
		void similarItem_carriesItemName() {
			assertThat(RecommendationBlock.SIMILAR_ITEM.titleOf("Levis", "LVC 1955 501"))
					.isEqualTo("Levis / LVC 1955 501과 비슷한 스타일");
			assertThat(RecommendationBlock.SIMILAR_ITEM.titleOf("Guidi", "788z Boots"))
					.isEqualTo("Guidi / 788z Boots와 비슷한 스타일");
		}

		@ParameterizedTest(name = "{0} → {1}")
		@DisplayName("조사는 마지막 글자의 받침을 따라간다 — 숫자는 소리 내어 읽는다")
		@CsvSource({
				// 숫자로 끝나는 경우. 501 은 '오백일' 이라 ㄹ 받침이 생긴다
				"LVC 1955 501, 과",
				"Air Max 90, 과",
				// 0 으로 끝나면 십·백·천·영 중 무엇으로 읽어도 받침이 있다
				"Chuck 70, 과",
				"501 XX 2, 와",
				// 영문은 읽는 법을 알 수 없어 받침이 없는 것으로 본다
				"788z Boots, 와",
				"Third Cut, 와",
				// 한글
				"청바지, 와",
				"코튼 팬츠, 와",
				"가죽 자켓, 과"
		})
		void particle_followsFinalConsonant(String itemName, String particle) {
			assertThat(RecommendationBlock.SIMILAR_ITEM.titleOf("브랜드", itemName))
					.isEqualTo("브랜드 / " + itemName + particle + " 비슷한 스타일");
		}
	}

	@Nested
	@DisplayName("부제")
	class Description {

		@Test
		@DisplayName("고정 문구 둘은 화면 문구를 그대로 쓴다")
		void fixedDescriptions() {
			assertThat(RecommendationBlock.SIMILAR_MOOD.descriptionOf("데님 팬츠"))
					.isEqualTo("지금 본 착장과 어울리는 스타일을 추천해요.");
			assertThat(RecommendationBlock.GOOD_COORDI.descriptionOf("데님 팬츠"))
					.isEqualTo("지금 스타일과 이어서 보기 좋은 코디예요.");
		}

		@Test
		@DisplayName("비슷한 스타일은 세부 카테고리가 부제에 들어간다")
		void similarItem_carriesCategoryName() {
			assertThat(RecommendationBlock.SIMILAR_ITEM.descriptionOf("데님 팬츠"))
					.isEqualTo("비슷한 데님 팬츠 스타일을 모아봤어요.");
			assertThat(RecommendationBlock.SIMILAR_ITEM.descriptionOf("부츠"))
					.isEqualTo("비슷한 부츠 스타일을 모아봤어요.");
		}
	}
}
