package com.tenure.domain.item.dto.request;

import com.tenure.domain.item.enums.WearTarget;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 아이템 기본 정보 폼. 상세 수정 시트와 판매 전환 폼이 같은 항목을 받는다.
 *
 * 두 화면이 각자의 요청 DTO 를 갖되 아이템에 반영하는 경로는 하나다 — 사본이 둘이면
 * 경계에서 갈린다. 실제로 사이즈 검증이 두 서비스에 복제돼 null 처리가 서로 반대였다.
 *
 * 대표 사진은 여기 없다. 상세 수정 시트에는 입력란이 없고 판매 전환에만 있어,
 * 받는 쪽이 Item.changeCoverImage 를 직접 부른다.
 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ItemForm {

	private final Long categoryId;
	private final String name;
	private final String brandName;
	private final String sizeLabel;
	private final WearTarget wearTarget;
}
