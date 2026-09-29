package com.tenure.global.storage.upload;

import com.tenure.global.response.BaseResponse;
import com.tenure.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

/** 파라미터 제약을 이 인터페이스에만 선언한다 — 근거는 UserApi 의 주석에 적어 두었다. */
@Tag(name = "미디어", description = "이미지 업로드 API")
public interface ImageUploadApi {

	@Operation(summary = "이미지 업로드",
			description = "파일을 저장하고 저장소 키를 돌려준다. 게시물·아이템·프로필 생성 API 에는 "
					+ "이 키를 넘긴다 — 전체 URL 을 저장하면 저장소나 도메인이 바뀔 때 전수 "
					+ "마이그레이션이 필요하다 (DEC-006).")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "업로드 성공"),
			@ApiResponse(responseCode = "400", description = "MEDIA_400_TYPE — 지원하지 않는 형식"),
			@ApiResponse(responseCode = "413", description = "MEDIA_413 — 용량 초과"),
			@ApiResponse(responseCode = "500", description = "MEDIA_500_STORAGE — 저장 실패")
	})
	ResponseEntity<BaseResponse<ImageUploadResponse>> upload(
			@Parameter(hidden = true) AuthUser authUser,
			@Parameter(description = "저장 경로 구분", example = "POST") ImageDomain domain,
			@Parameter(description = "이미지 파일 (jpeg · png · webp)",
					content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE))
			MultipartFile file);
}
