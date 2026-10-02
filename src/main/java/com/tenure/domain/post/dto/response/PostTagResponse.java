package com.tenure.domain.post.dto.response;

import com.tenure.domain.item.enums.SaleStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 사진 위의 태그 말풍선.
 *
 * saleStatus 를 함께 내려준다 (DEC-102) — 말풍선이 ON_SALE 일 때만 '#판매중' 을
 * 붙여 2행이 되므로 클라이언트가 높이를 결정하려면 상태를 알아야 한다. 판매 등록이 없으면 null 이다.
 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "아이템 태그")
public class PostTagResponse {

	@Schema(description = "태그 id. 태그 수정 요청의 입력값이다")
	private final Long postItemTagId;

	@Schema(description = "아이템 id")
	private final Long itemId;

	@Schema(description = "아이템명", example = "LVC 1955 501")
	private final String itemName;

	@Schema(description = "브랜드명", example = "Levis")
	private final String brandName;

	@Schema(description = "아이템의 판매 상태. 말풍선은 ON_SALE 일 때만 '#판매중' 을 붙인다", example = "ON_SALE")
	private final SaleStatus saleStatus;

	@Schema(description = "가로 위치(비율)", example = "0.4200")
	private final BigDecimal posX;

	@Schema(description = "세로 위치(비율)", example = "0.6100")
	private final BigDecimal posY;
}
