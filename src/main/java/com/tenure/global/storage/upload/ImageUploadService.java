package com.tenure.global.storage.upload;

import com.tenure.global.exception.CustomException;
import com.tenure.global.exception.MediaErrorCode;
import com.tenure.global.logging.Loggable;
import com.tenure.global.storage.FileStorage;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;


/**
 * 이미지 업로드. 저장만 하고 DB 를 건드리지 않으므로 트랜잭션이 없다.
 *
 * 도메인이 아니라 global/storage 아래에 둔다 — 지킬 도메인 규칙도 엔티티도 없고
 * 저장소(FileStorage)라는 기술 관심사의 입구이기 때문이다. 게시물·아이템·프로필·채팅이
 * 모두 이 엔드포인트 하나를 쓰므로 어느 도메인에 넣어도 나머지가 남의 도메인을 호출하게 된다.
 * MediaErrorCode 가 global/exception 에 있는 것도 같은 이유다.
 *
 * 업로드는 게시물·아이템 생성과 분리돼 있다. 생성 트랜잭션 안에서 파일을 올리면 커넥션을
 * 파일 전송 시간만큼 잡고, 롤백돼도 파일은 남는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImageUploadService {

	/** 허용 형식 → 확장자. 화면이 올리는 것은 카메라 촬영물과 앨범 사진이다. */
	private static final Map<String, String> ALLOWED_TYPES = Map.of(
			"image/jpeg", "jpg",
			"image/png", "png",
			"image/webp", "webp"
	);

	/** 키의 날짜 구간. 한 디렉터리에 파일이 무한정 쌓이지 않게 월 단위로 나눈다. */
	private static final DateTimeFormatter KEY_DATE = DateTimeFormatter.ofPattern("yyyy/MM");

	private final FileStorage fileStorage;

	/**
	 * 이미지를 저장하고 키를 돌려준다.
	 *
	 * 파일명은 사용자가 준 것을 쓰지 않는다 — 한글·공백·경로 문자가 그대로 키가 되고,
	 * 같은 이름을 다시 올리면 남의 파일을 덮어쓴다. UUID 로 새로 만든다.
	 */
	@Loggable("이미지 업로드")
	public ImageUploadResponse upload(ImageDomain domain, MultipartFile file) {

		String extension = resolveExtension(file);

		// ex: post/2026/09/{uuid}.jpg
		String key = "%s/%s/%s.%s".formatted(
				domain.getPathPrefix(),
				LocalDate.now().format(KEY_DATE),
				UUID.randomUUID().toString().replace("-", ""),
				extension
		);

		try (InputStream content = file.getInputStream()) {
			fileStorage.store(key, content, file.getSize(), file.getContentType());
		} catch (IOException | RuntimeException e) {
			log.error("이미지 저장에 실패했습니다. key={}", key, e);
			throw new CustomException(MediaErrorCode.MEDIA_500_STORAGE);
		}


		return ImageUploadResponse.builder()
				.key(key)
				.url(fileStorage.toUrl(key))
				.build();
	}

	/**
	 * 형식 검증. 확장자가 아니라 Content-Type 으로 판정한다.
	 *
	 * 빈 파일도 여기서 걸러낸다 — 0바이트 파일은 저장은 되지만 화면에서 깨진 이미지가 된다.
	 */
	private String resolveExtension(MultipartFile file) {
		if (file.isEmpty()) {
			log.warn("빈 파일을 업로드하려 했습니다. filename={}", file.getOriginalFilename());
			throw new CustomException(MediaErrorCode.MEDIA_400_TYPE);
		}

		String extension = ALLOWED_TYPES.get(file.getContentType());
		if (extension == null) {
			log.warn("허용하지 않는 형식입니다. contentType={}", file.getContentType());
			throw new CustomException(MediaErrorCode.MEDIA_400_TYPE);
		}

		return extension;
	}
}
