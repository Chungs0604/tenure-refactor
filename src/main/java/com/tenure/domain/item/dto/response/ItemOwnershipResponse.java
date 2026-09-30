package com.tenure.domain.item.dto.response;

import com.tenure.domain.item.enums.AcquiredBy;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/** 'Tenure 기록 기준' 의 소유 구간 카드 한 장. */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "소유 구간")
public class ItemOwnershipResponse {

	@Schema(description = "소유자 닉네임. 탈퇴자는 '탈퇴한 사용자'", example = "유진나라")
	private final String ownerNickname;

	@Schema(description = "소유자 프로필 사진 URL. 없으면 null")
	private final String ownerProfileImageUrl;

	@Schema(description = "구간 시작일", example = "2026-07-10")
	private final LocalDate startDate;

	@Schema(description = "구간 종료일. null 이면 '~ 현재'", example = "2026-07-10")
	private final LocalDate endDate;

	@Schema(description = "OOTD 인증 N회. 무효화된 기록은 빠진다", example = "12")
	private final Integer wearCount;

	@Schema(description = "취득 경위. INITIAL_REGISTRATION 에만 '최초 등록' 배지가 붙는다",
			example = "INITIAL_REGISTRATION")
	private final AcquiredBy acquiredBy;

	@Schema(description = "카드 안 그리드에 쓰는 OOTD 사진 URL. 최신 3장")
	private final List<String> previewImages;
}
