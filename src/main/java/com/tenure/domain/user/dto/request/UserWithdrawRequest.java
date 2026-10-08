package com.tenure.domain.user.dto.request;

import com.tenure.domain.user.enums.WithdrawalReason;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 회원탈퇴 요청. 사유는 드롭다운 4종 중 하나이며 자유 입력이 아님 */
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "회원탈퇴 요청")
public class UserWithdrawRequest {

	@Schema(description = "탈퇴 사유")
	@NotNull
	private WithdrawalReason reason;
}
