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
 * 정산 계좌 수정 요청. 보낸 값으로 전체를 교체한다.
 *
 * 기본 계좌 여부는 여기서 바꾸지 않는다 — 수정 화면에 기본 설정 UI 가 없다.
 */
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "정산 계좌 수정 요청")
public class PayoutAccountUpdateRequest {

	@Schema(description = "은행", example = "TOSS_BANK")
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
