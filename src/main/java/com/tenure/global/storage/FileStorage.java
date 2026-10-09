package com.tenure.global.storage;

import java.io.InputStream;

/**
 * 이미지 저장소. 구현체는 app.storage.type 으로 갈린다 (local | s3).
 *
 * <p>저장하는 값은 <b>키</b>다. 예: {@code post/2026/03/ab12.jpg}
 * 저장소가 바뀌어도 키는 그대로이며, 화면에 내려줄 때 {@link #toUrl(String)} 로 URL 을 만든다.
 */
public interface FileStorage {

	/** 파일을 저장하고 키를 돌려준다. */
	String store(String key, InputStream content, long size, String contentType);

	/** 키를 화면에서 쓸 수 있는 URL 로 바꾼다. */
	String toUrl(String key);

	/** 키가 없으면 URL 도 없다. 여러 서비스가 각자 짜던 null 분기를 여기로 모은다. */
	default String toUrlOrNull(String key) {
		return key == null ? null : toUrl(key);
	}

	/** 키에 해당하는 파일을 지운다. */
	void delete(String key);
}
