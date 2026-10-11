package com.tenure.domain.follow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.tenure.domain.block.repository.UserBlockRepository;
import com.tenure.domain.follow.entity.Follow;
import com.tenure.domain.follow.exception.FollowErrorCode;
import com.tenure.domain.follow.repository.FollowRepository;
import com.tenure.domain.notification.enums.NotificationCategory;
import com.tenure.domain.notification.enums.NotificationSettingType;
import com.tenure.domain.notification.enums.NotificationTargetType;
import com.tenure.domain.notification.service.NotificationService;
import com.tenure.domain.user.entity.User;
import com.tenure.domain.user.dto.response.UserSummaryResponse;
import com.tenure.domain.user.enums.Gender;
import com.tenure.domain.user.exception.UserErrorCode;
import com.tenure.domain.user.mapper.UserMapper;
import com.tenure.domain.user.repository.UserRepository;
import com.tenure.global.exception.CustomException;
import com.tenure.global.pagination.CursorSlice;
import com.tenure.global.storage.FileStorage;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("FollowService")
class FollowServiceTest {

	private static final Long VIEWER_ID = 1L;
	private static final Long TARGET_ID = 2L;

	@Mock private FollowRepository followRepository;
	@Mock private UserRepository userRepository;
	@Mock private UserBlockRepository userBlockRepository;
	@Mock private FileStorage fileStorage;
	@Mock private NotificationService notificationService;

	/** mock 하지 않음 — mock 하면 엔티티에 필드를 넣고 응답에 안 실어도 테스트가 통과함 */
	@Spy private UserMapper userMapper = Mappers.getMapper(UserMapper.class);

	@InjectMocks private FollowService followService;

	@Nested
	@DisplayName("팔로우")
	class FollowUser {

		@Test
		@DisplayName("처음 팔로우하면 follow 행을 만들고 대상의 follower_count 를 올린다")
		void follow_notFollowing_increasesFollowerCount() {
			User target = user(TARGET_ID, 3);
			User viewer = user(VIEWER_ID, 0);
			given(userRepository.findByIdForUpdate(VIEWER_ID)).willReturn(Optional.of(viewer));
			given(userRepository.findByIdForUpdate(TARGET_ID)).willReturn(Optional.of(target));
			given(followRepository.existsFollow(VIEWER_ID, TARGET_ID)).willReturn(false);

			followService.follow(VIEWER_ID, TARGET_ID);

			assertThat(target.getFollowerCount()).isEqualTo(4);
			verify(followRepository).save(any(Follow.class));
			verify(notificationService).notify(
					eq(target), eq(NotificationSettingType.FOLLOW), eq(NotificationCategory.ACTIVITY),
					eq(viewer.getNickname()), any(), any(), eq(NotificationTargetType.USER), eq(VIEWER_ID));
		}

		@Test
		@DisplayName("자기 자신을 팔로우하면 FOLLOW_400_SELF 이고 아무것도 조회하지 않는다")
		void follow_self_throwsSelfFollow() {
			assertThatThrownBy(() -> followService.follow(VIEWER_ID, VIEWER_ID))
					.isInstanceOf(CustomException.class)
					.extracting(e -> ((CustomException) e).getErrorCode())
					.isEqualTo(FollowErrorCode.FOLLOW_400_SELF);

			verifyNoInteractions(userRepository, followRepository, userBlockRepository, notificationService);
		}

		@Test
		@DisplayName("차단 관계면 FOLLOW_403_BLOCKED 이고 아무것도 조회하지 않는다")
		void follow_blocked_throwsForbidden() {
			given(userBlockRepository.existsMutualBlock(VIEWER_ID, TARGET_ID)).willReturn(true);

			assertThatThrownBy(() -> followService.follow(VIEWER_ID, TARGET_ID))
					.isInstanceOf(CustomException.class)
					.extracting(e -> ((CustomException) e).getErrorCode())
					.isEqualTo(FollowErrorCode.FOLLOW_403_BLOCKED);

			verifyNoInteractions(userRepository, followRepository, notificationService);
		}

		@Test
		@DisplayName("이미 팔로우한 사용자면 FOLLOW_409 이고 아무것도 바꾸지 않는다")
		void follow_alreadyFollowing_throwsConflict() {
			User target = user(TARGET_ID, 3);
			User viewer = user(VIEWER_ID, 0);
			given(userRepository.findByIdForUpdate(VIEWER_ID)).willReturn(Optional.of(viewer));
			given(userRepository.findByIdForUpdate(TARGET_ID)).willReturn(Optional.of(target));
			given(followRepository.existsFollow(VIEWER_ID, TARGET_ID)).willReturn(true);

			assertThatThrownBy(() -> followService.follow(VIEWER_ID, TARGET_ID))
					.isInstanceOf(CustomException.class)
					.extracting(e -> ((CustomException) e).getErrorCode())
					.isEqualTo(FollowErrorCode.FOLLOW_409);

			assertThat(target.getFollowerCount()).isEqualTo(3);
			verify(followRepository, never()).save(any());
		}

		@Test
		@DisplayName("동시 요청이 겹쳐 유니크 제약에 걸리면 FOLLOW_409 로 번역한다")
		void follow_concurrentConflict_translatesToFollow409() {
			User target = user(TARGET_ID, 0);
			User viewer = user(VIEWER_ID, 0);
			given(userRepository.findByIdForUpdate(VIEWER_ID)).willReturn(Optional.of(viewer));
			given(userRepository.findByIdForUpdate(TARGET_ID)).willReturn(Optional.of(target));
			given(followRepository.existsFollow(VIEWER_ID, TARGET_ID)).willReturn(false);
			willThrow(new DataIntegrityViolationException("uk_follow"))
					.given(followRepository).save(any());

			assertThatThrownBy(() -> followService.follow(VIEWER_ID, TARGET_ID))
					.isInstanceOf(CustomException.class)
					.extracting(e -> ((CustomException) e).getErrorCode())
					.isEqualTo(FollowErrorCode.FOLLOW_409);

			assertThat(target.getFollowerCount()).isZero();
			verifyNoInteractions(notificationService);
		}

		@Test
		@DisplayName("없는 사용자를 팔로우하면 USER_404 이다")
		void follow_targetNotFound_throwsUserNotFound() {
			given(userRepository.findByIdForUpdate(VIEWER_ID)).willReturn(Optional.of(user(VIEWER_ID, 0)));
			given(userRepository.findByIdForUpdate(TARGET_ID)).willReturn(Optional.empty());

			assertThatThrownBy(() -> followService.follow(VIEWER_ID, TARGET_ID))
					.isInstanceOf(CustomException.class)
					.extracting(e -> ((CustomException) e).getErrorCode())
					.isEqualTo(UserErrorCode.USER_404);

			verifyNoInteractions(followRepository);
		}
	}

	@Nested
	@DisplayName("언팔로우")
	class UnfollowUser {

		@Test
		@DisplayName("팔로우 중이면 관계를 지우고 대상의 follower_count 를 내린다")
		void unfollow_following_decreasesFollowerCount() {
			User target = user(TARGET_ID, 3);
			Follow relation = Follow.create(user(VIEWER_ID, 0), target);
			given(userRepository.findByIdForUpdate(TARGET_ID)).willReturn(Optional.of(target));
			given(followRepository.findByFollowerIdAndFollowingId(VIEWER_ID, TARGET_ID))
					.willReturn(Optional.of(relation));

			followService.unfollow(VIEWER_ID, TARGET_ID);

			assertThat(target.getFollowerCount()).isEqualTo(2);
			verify(followRepository).delete(relation);
		}

		@Test
		@DisplayName("팔로우하지 않은 관계의 언팔로우는 예외 없이 성공 처리하고 카운터를 건드리지 않는다")
		void unfollow_notFollowing_idempotentSuccess() {
			User target = user(TARGET_ID, 3);
			given(userRepository.findByIdForUpdate(TARGET_ID)).willReturn(Optional.of(target));
			given(followRepository.findByFollowerIdAndFollowingId(VIEWER_ID, TARGET_ID))
					.willReturn(Optional.empty());

			followService.unfollow(VIEWER_ID, TARGET_ID);

			assertThat(target.getFollowerCount()).isEqualTo(3);
			verify(followRepository, never()).delete(any());
		}

		@Test
		@DisplayName("대상 사용자가 없으면 USER_404 이다")
		void unfollow_targetNotFound_throwsUserNotFound() {
			given(userRepository.findByIdForUpdate(TARGET_ID)).willReturn(Optional.empty());

			assertThatThrownBy(() -> followService.unfollow(VIEWER_ID, TARGET_ID))
					.isInstanceOf(CustomException.class)
					.extracting(e -> ((CustomException) e).getErrorCode())
					.isEqualTo(UserErrorCode.USER_404);
		}
	}

	@Nested
	@DisplayName("팔로워 목록")
	class ListFollowers {

		@Test
		@DisplayName("대상 사용자가 없으면 USER_404 이다")
		void listFollowers_userNotFound_throwsUserNotFound() {
			given(userRepository.existsById(TARGET_ID)).willReturn(false);

			assertThatThrownBy(() -> followService.listFollowers(VIEWER_ID, TARGET_ID, null, 20))
					.isInstanceOf(CustomException.class)
					.extracting(e -> ((CustomException) e).getErrorCode())
					.isEqualTo(UserErrorCode.USER_404);

			verifyNoInteractions(followRepository);
		}

		@Test
		@DisplayName("차단 관계면 404 대신 빈 목록을 돌려주고 목록 조회를 하지 않는다")
		void listFollowers_blocked_returnsEmptySliceWithout404() {
			given(userRepository.existsById(TARGET_ID)).willReturn(true);
			given(userBlockRepository.existsMutualBlock(VIEWER_ID, TARGET_ID)).willReturn(true);

			CursorSlice<UserSummaryResponse> slice =
					followService.listFollowers(VIEWER_ID, TARGET_ID, null, 20);

			assertThat(slice.isEmpty()).isTrue();
			verifyNoInteractions(followRepository);
		}

		@Test
		@DisplayName("팔로워가 없으면 빈 목록이고 배치 조회를 하지 않는다")
		void listFollowers_empty_returnsEmptySlice() {
			given(userRepository.existsById(TARGET_ID)).willReturn(true);
			given(followRepository.findFollowers(TARGET_ID, null, 20)).willReturn(List.of());

			CursorSlice<UserSummaryResponse> slice =
					followService.listFollowers(VIEWER_ID, TARGET_ID, null, 20);

			assertThat(slice.isEmpty()).isTrue();
		}

		@Test
		@DisplayName("내가 팔로우 중인 팔로워는 isFollowing 이 true 로 나간다")
		void listFollowers_viewerFollowsBack_marksIsFollowingTrue() {
			User follower = user(3L, 0);
			Follow relation = Follow.create(follower, user(TARGET_ID, 0));
			ReflectionTestUtils.setField(relation, "id", 100L);
			ReflectionTestUtils.setField(relation, "createdAt", LocalDateTime.now());

			given(userRepository.existsById(TARGET_ID)).willReturn(true);
			given(followRepository.findFollowers(TARGET_ID, null, 20)).willReturn(List.of(relation));
			given(followRepository.findFollowingIdsAmong(VIEWER_ID, List.of(3L))).willReturn(List.of(3L));

			CursorSlice<UserSummaryResponse> slice =
					followService.listFollowers(VIEWER_ID, TARGET_ID, null, 20);

			UserSummaryResponse response = slice.getContent().get(0);
			assertThat(response.getId()).isEqualTo(3L);
			assertThat(response.getIsFollowing()).isTrue();
		}
	}

	private static User user(Long id, int followerCount) {
		User user = User.signUp("user" + id + "@tenure.app", "$2a$10$hashed", "홍길동",
				"821012345678", "nick" + id, null, Gender.MALE, 170, 64);
		ReflectionTestUtils.setField(user, "id", id);
		ReflectionTestUtils.setField(user, "followerCount", followerCount);
		return user;
	}
}
