package com.tenure.domain.report.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "신고 접수 응답")
public class ReportResponse {

	@Schema(description = "생성된 신고 id", example = "1")
	private final Long id;
}
