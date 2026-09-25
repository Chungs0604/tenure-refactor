package com.tenure.domain.item.enums;

/**
 * 태그를 A→B 로 바꾸면 A 의 인증은 TAG_CHANGED 로, 태그를 지우면 TAG_DELETED 로 무효화한다. 화면에 노출되는 값은 아니다
 */
public enum WearRecordVoidReason {

	TAG_CHANGED,
	TAG_DELETED;

}
