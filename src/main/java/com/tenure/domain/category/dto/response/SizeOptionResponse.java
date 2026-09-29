package com.tenure.domain.category.dto.response;

import com.tenure.domain.category.enums.SizeSystem;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/** 사이즈 칩 하나. 화면은 sizeSystem 이 바뀌는 지점에서 줄을 바꾼다. */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "사이즈 칩")
public class SizeOptionResponse {

	@Schema(description = "칩 라벨. 아이템 등록 요청의 sizeLabel 에 이 값을 그대로 보낸다", example = "L")
	private final String label;

	@Schema(description = "칩이 속한 체계", example = "ALPHA")
	private final SizeSystem sizeSystem;
}
