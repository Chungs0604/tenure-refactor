package com.tenure.domain.user.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.tenure.domain.user.dto.request.UserCreateRequest;
import com.tenure.domain.user.enums.AgreementType;
import com.tenure.domain.user.enums.Gender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * 요청 DTO 의 Jackson 바인딩.
 *
 * <p>Lombok {@code @Jacksonized} 는 Jackson 2 어노테이션을 내보내 Boot 4(Jackson 3)에서
 * 동작하지 않는다. no-arg 생성자로 바인딩되는지를 여기서 고정한다 — 어노테이션 조합이
 * 바뀌면 런타임 400 으로만 드러나서 늦게 발견된다.
 */
@DisplayName("회원가입 요청 바인딩")
class UserCreateRequestBindingTest {

	private final ObjectMapper mapper = JsonMapper.builder().build();

	@Test
	@DisplayName("중첩 객체와 목록까지 역직렬화된다")
	void deserialize_nestedAgreementsAndAddress_bindsAllFields() {
		String json = """
				{
				  "email": "gildong@naver.com",
				  "password": "password123",
				  "verificationToken": "tok",
				  "realName": "홍길동",
				  "phone": "821012345678",
				  "nickname": "gildong",
				  "gender": "MALE",
				  "heightCm": 170,
				  "weightKg": 64,
				  "agreements": [{ "agreementType": "SERVICE_TERMS", "agreed": true }],
				  "address": {
				    "recipientName": "홍길동",
				    "phone": "010-1234-5678",
				    "zipcode": "07027",
				    "address": "서울특별시 동작구 상도로 369"
				  }
				}""";

		UserCreateRequest request = mapper.readValue(json, UserCreateRequest.class);

		assertThat(request.getEmail()).isEqualTo("gildong@naver.com");
		assertThat(request.getGender()).isEqualTo(Gender.MALE);
		assertThat(request.getHeightCm()).isEqualTo(170);
		assertThat(request.getAgreements()).singleElement()
				.satisfies(a -> {
					assertThat(a.getAgreementType()).isEqualTo(AgreementType.SERVICE_TERMS);
					assertThat(a.getAgreed()).isTrue();
				});
		assertThat(request.getAddress().getRecipientName()).isEqualTo("홍길동");
		assertThat(request.getAddress().getAddressDetail()).isNull();
	}

	@Test
	@DisplayName("빌더로도 만들 수 있다 — 테스트 픽스처에서 쓴다")
	void build_withBuilder_setsFields() {
		UserCreateRequest request = UserCreateRequest.builder()
				.email("a@b.com")
				.nickname("nick")
				.build();

		assertThat(request.getEmail()).isEqualTo("a@b.com");
		assertThat(request.getNickname()).isEqualTo("nick");
	}
}
