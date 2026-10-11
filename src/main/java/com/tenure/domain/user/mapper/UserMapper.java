package com.tenure.domain.user.mapper;

import com.tenure.domain.user.dto.response.AccountResponse;
import com.tenure.domain.user.dto.response.MyProfileResponse;
import com.tenure.domain.user.dto.response.UserProfileResponse;
import com.tenure.domain.user.dto.response.UserSummaryResponse;
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

	/** 탈퇴 계정의 저장값은 {@code 탈퇴한사용자_{id}} 라 화면 표시용 문구를 따로 둔다. */
	String WITHDRAWN_NICKNAME = "탈퇴한 사용자";

	AccountResponse toAccountResponse(User user);

	@Mapping(target = "profileImageUrl", source = "profileImageUrl")
	@Mapping(target = "itemCount", source = "itemCount")
	@Mapping(target = "wishCount", source = "wishCount")
	MyProfileResponse toMyProfileResponse(
			User user, String profileImageUrl, Integer itemCount, Integer wishCount
	);

	@Mapping(target = "nickname", expression = "java(com.tenure.domain.user.mapper.UserMapper.displayNickname(user))")
	@Mapping(target = "profileImageUrl", source = "profileImageUrl")
	@Mapping(target = "itemCount", source = "itemCount")
	@Mapping(target = "isFollowing", source = "following")
	@Mapping(target = "isBlocked", source = "blocked")
	UserProfileResponse toUserProfileResponse(
			User user, String profileImageUrl, Integer itemCount, Boolean following, Boolean blocked
	);

	@Mapping(target = "nickname", expression = "java(com.tenure.domain.user.mapper.UserMapper.displayNickname(user))")
	@Mapping(target = "profileImageUrl", source = "profileImageUrl")
	@Mapping(target = "isFollowing", source = "following")
	UserSummaryResponse toUserSummaryResponse(User user, String profileImageUrl, Boolean following);

	/**
	 * 탈퇴 계정은 저장된 닉네임 대신 고정 문구를 보여준다.
	 *
	 * static 이라 OrderMapper·PostMapper·ChatMapper 가 각자 복제하지 않고 완전히 한정한
	 * 이름으로 {@code expression} 에서 바로 부를 수 있다.
	 */
	static String displayNickname(User user) {
		return user.isWithdrawn() ? WITHDRAWN_NICKNAME : user.getNickname();
	}
}
