package com.tenure.domain.user.dto.request;

import com.tenure.domain.address.enums.DeliveryRequestType;
import com.tenure.domain.user.enums.AgreementType;
import com.tenure.domain.user.enums.Gender;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 회원가입 요청 (API-012).
 *
 * <p>약관 3종 동의와 주소 1건을 함께 받아 화면 흐름(가입 → 약관 → 주소 → 프로필)을 한 번에 마감한다.
 *
 * <p>프로필 입력값 중 사진만 선택이다 — 수정 요청과 같은 규칙이라야 두 화면이 어긋나지 않는다.
 *
 * <p>길이 제약은 컬럼 길이와 맞춘다. 안 막으면 DB 에서 끊겨 500 이 되는데 그건 400 으로 돌려줄 일이다.
 */
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "회원가입 요청")
public class UserCreateRequest {

	@Schema(description = "이메일", example = "gildong@naver.com")
	@NotBlank
	@Email
	@Size(max = 255)
	private String email;

	@Schema(description = "비밀번호 (8자 이상)")
	@NotBlank
	@Size(min = 8)
	private String password;

	@Schema(description = "인증번호 확인(API-005)으로 받은 1회용 토큰")
	@NotBlank
	private String verificationToken;

	@Schema(description = "실명", example = "홍길동")
	@NotBlank
	@Size(max = 50)
	private String realName;

	@Schema(description = "휴대폰번호", example = "821012345678")
	@NotBlank
	@Size(max = 20)
	private String phone;

	@Schema(description = "닉네임", example = "gildong")
	@NotBlank
	@Size(max = 30)
	private String nickname;

	@Schema(description = "프로필 사진 저장소 키. 미지정 시 기본 이미지")
	@Size(max = 500)
	private String profileImageKey;

	@Schema(description = "성별")
	@NotNull
	private Gender gender;

	/** 엔티티는 SMALLINT(Short) 라 범위를 여기서 막는다 — 안 막으면 좁히기 변환에서 값이 뒤집힌다. */
	@Schema(description = "키(cm)", example = "170")
	@NotNull
	@Min(0)
	@Max(200)
	private Integer heightCm;

	@Schema(description = "몸무게(kg)", example = "64")
	@NotNull
	@Min(0)
	@Max(150)
	private Integer weightKg;

	@Schema(description = "약관 동의 3종")
	@NotEmpty
	@Valid
	private List<AgreementRequest> agreements;

	@Schema(description = "기본 배송지")
	@NotNull
	@Valid
	private AddressRequest address;

	@Getter
	@Builder
	@NoArgsConstructor(access = AccessLevel.PROTECTED)
	@AllArgsConstructor(access = AccessLevel.PRIVATE)
	@Schema(description = "약관 동의")
	public static class AgreementRequest {

		@Schema(description = "약관 종류")
		@NotNull
		private AgreementType agreementType;

		@Schema(description = "동의 여부. true 여야 한다")
		@NotNull
		private Boolean agreed;

		/** 필수 약관이라 false 로는 가입시키지 않는다. */
		@AssertTrue(message = "필수 약관에 동의해야 합니다.")
		public boolean isAgreedToRequiredTerms() {
			return Boolean.TRUE.equals(agreed);
		}
	}

	@Getter
	@Builder
	@NoArgsConstructor(access = AccessLevel.PROTECTED)
	@AllArgsConstructor(access = AccessLevel.PRIVATE)
	@Schema(description = "배송지")
	public static class AddressRequest {

		@Schema(description = "수령인")
		@NotBlank
		@Size(max = 50)
		private String recipientName;

		@Schema(description = "연락처")
		@NotBlank
		@Size(max = 20)
		private String phone;

		@Schema(description = "우편번호", example = "07027")
		@NotBlank
		@Size(max = 10)
		private String zipcode;

		@Schema(description = "도로명주소")
		@NotBlank
		@Size(max = 255)
		private String address;

		@Schema(description = "상세 주소")
		@Size(max = 255)
		private String addressDetail;

		@Schema(description = "배송 요청사항 유형. 고르지 않으면 null")
		private DeliveryRequestType deliveryRequestType;

		@Schema(description = "직접 입력한 요청사항. DIRECT 일 때만 보낸다")
		@Size(max = 50)
		private String deliveryRequest;
	}
}
