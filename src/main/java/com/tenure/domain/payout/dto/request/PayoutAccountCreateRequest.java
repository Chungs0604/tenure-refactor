package com.tenure.domain.payout.dto.request;

import com.tenure.domain.payout.enums.BankCode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 정산 계좌 등록 요청.
 *
 * 기본 계좌 지정을 받지 않는다 — 추가 화면의 입력 필드가 은행·예금주·계좌 번호 3개뿐이고
 * 기본 설정 체크박스가 없다. 첫 계좌만 자동으로 기본이 되고, 이후 변경은 별도 동작이다.
 *
 * 길이 제약은 컬럼 길이와 맞춰 둔다 — 넘기면 DB 에서 잘리거나 끊기는데 그건 400 으로 돌려줄 일이다.
 */
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "정산 계좌 등록 요청")
public class PayoutAccountCreateRequest {

	@Schema(description = "은행. 선택지는 은행 목록 조회로 받는다", example = "TOSS_BANK")
	@NotNull
	private BankCode bankCode;

	@Schema(description = "예금주", example = "홍길동")
	@NotBlank
	@Size(max = 50)
	private String accountHolder;

	@Schema(description = "계좌 번호", example = "123456-789012")
	@NotBlank
	@Size(max = 50)
	private String accountNumber;
}
