package com.tenure.domain.report.dto.request;

import com.tenure.domain.report.enums.ReportReason;
import com.tenure.domain.report.enums.ReportTargetType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "신고 접수 요청")
public class ReportRequest {

	@Schema(description = "신고 대상 종류. USER 는 이 API 에서 받지 않는다", example = "POST")
	@NotNull
	private ReportTargetType targetType;

	@Schema(description = "신고 대상 id", example = "1")
	@NotNull
	private Long targetId;

	@Schema(description = "신고 사유. targetType 에 허용된 값만 받는다", example = "SPAM")
	@NotNull
	private ReportReason reason;

	@Schema(description = "상세 사유", example = "같은 상품을 반복해서 올리고 있습니다.")
	@Size(max = 500)
	private String detail;
}
