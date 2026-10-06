package com.tenure.domain.chat.dto.response;

import com.tenure.domain.item.enums.SaleStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "채팅방 헤더")
public class ChatRoomHeaderResponse {

	@Schema(description = "채팅방 id", example = "1")
	private final Long roomId;

	@Schema(description = "상대")
	private final ChatHeaderCounterpartResponse counterpart;

	@Schema(description = "아이템 요약")
	private final ChatItemSummaryResponse item;

	@Schema(description = "가격. listing 이 있으면 그 가격, 없으면 관련 제안 금액, 둘 다 없으면 null", example = "360000")
	private final String price;

	@Schema(description = "판매 상태", example = "ON_SALE")
	private final SaleStatus saleStatus;

	@Schema(description = "최근 착용일. 인증이 없으면 null", example = "2026-06-21")
	private final LocalDate recentWornDate;

	@Schema(description = "헤더 버튼 2개(좌: 아이템 이동, 우: 거래 단계)")
	private final List<ChatCtaResponse> ctas;

	@Schema(description = "진행 중인 거래 id. 없으면 null", example = "1")
	private final Long orderId;
}
