package com.tenure.domain.post.enums;

/**
 * 태그된 아이템 목록(SCR-016)에서 한 행이 그려지는 상태.
 *
 * SaleStatus 를 그대로 쓰지 않는다 — 화면에 '삭제된 아이템' 상태가 따로 있고(회색 처리),
 * 판매 등록이 한 번도 없었던 아이템도 '미판매' 로 보여야 한다. 판매 등록의 상태와 아이템의 삭제
 * 여부를 한 값으로 합친 표시용 enum 이다.
 */
public enum TaggedItemStatus {

	/** 판매 중 — CTA 를 띄운다 */
	ON_SALE,

	/** 미판매. 판매 등록이 없는 아이템도 여기 들어간다 */
	NOT_FOR_SALE,

	/** 판매 완료 */
	SOLD,

	/** 삭제된 아이템 — 회색 처리하고 CTA 를 띄우지 않는다 */
	DELETED
}
