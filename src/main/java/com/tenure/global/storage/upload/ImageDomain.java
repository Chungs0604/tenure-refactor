package com.tenure.global.storage.upload;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 업로드 이미지의 용도. 저장소 키의 첫 구간이 된다 — post/2026/09/ab12.jpg.
 *
 * 용도를 받는 이유는 키를 사람이 읽을 수 있게 나누기 위해서다. 저장 후에는 컬럼마다
 * 키가 흩어지므로(profile_image_key · image_key · cover_image_key) 어느 화면이 올린
 * 파일인지 키만 보고 알 수 없으면 정리도 추적도 어렵다.
 */
@Getter
@RequiredArgsConstructor
public enum ImageDomain {

	PROFILE("profile"),
	POST("post"),
	ITEM("item"),
	CHAT("chat");

	private final String pathPrefix;
}
