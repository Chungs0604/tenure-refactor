package com.tenure.domain.user.mapper;

import com.tenure.domain.user.dto.response.AccountResponse;
import com.tenure.domain.user.dto.response.MyProfileResponse;
import com.tenure.domain.user.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * 엔티티 → 응답 단방향 변환.
 *
 * <p>저장소 키를 URL 로 바꾸는 일은 여기서 하지 않는다 — 매핑 계층이 {@code FileStorage} 를 알면
 * 단위 테스트가 저장소 설정에 묶인다. Service 가 바꿔서 넘긴 값을 그대로 싣는다.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface UserMapper {

	AccountResponse toAccountResponse(User user);

	@Mapping(target = "profileImageUrl", source = "profileImageUrl")
	@Mapping(target = "itemCount", source = "itemCount")
	@Mapping(target = "wishCount", source = "wishCount")
	MyProfileResponse toMyProfileResponse(
			User user, String profileImageUrl, Integer itemCount, Integer wishCount
	);
}
