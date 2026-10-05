package com.tenure.domain.order.dto.response;

import com.tenure.domain.order.enums.CourierCode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "택배사 선택지 한 건")
public class CourierResponse {

	@Schema(description = "저장·전송하는 값", example = "CJ_LOGISTICS")
	private final CourierCode code;

	@Schema(description = "화면 표시명", example = "CJ대한통운")
	private final String name;
}
