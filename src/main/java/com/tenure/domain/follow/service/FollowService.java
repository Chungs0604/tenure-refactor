package com.tenure.domain.follow.service;

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
import com.tenure.domain.user.exception.UserErrorCode;
import com.tenure.domain.user.mapper.UserMapper;
import com.tenure.domain.user.repository.UserRepository;
import com.tenure.global.exception.CustomException;
import com.tenure.global.logging.Loggable;
import com.tenure.global.pagination.Cursor;
import com.tenure.global.pagination.CursorSlice;
import com.tenure.global.storage.FileStorage;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 팔로워/팔로잉 목록, 팔로우·언팔로우.
 *
 * 예외를 던지기 전에 판단 근거를 남김 — ErrorCode 자체는 ServiceLoggingAspect 가 찍음.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FollowService {

	private final FollowRepository followRepository;
	private final UserRepository userRepository;
	private final UserBlockRepository userBlockRepository;
	private final UserMapper userMapper;
	private final FileStorage fileStorage;
	private final NotificationService notificationService;

	/**
	 * 팔로워 목록. 차단 관계면 빈 목록을 돌려줌 — 404 로 돌려주면 '차단했다' 는 사실이 드러남
	 * (PostQueryService.getUserPosts() 와 같은 이유).
	 */
	@Loggable("팔로워 목록 조회")
	public CursorSlice<UserSummaryResponse> listFollowers(Long viewerId, Long userId, Cursor cursor, int size) {
		ensureUserExists(userId);

		if (userBlockRepository.existsMutualBlock(viewerId, userId)) {
			return CursorSlice.empty();
		}

		List<Follow> rows = followRepository.findFollowers(userId, cursor, size);
		if (rows.isEmpty()) {
			return CursorSlice.empty();
		}

		List<Long> otherUserIds = rows.stream().map(f -> f.getFollower().getId()).distinct().toList();
		Set<Long> followingIds = followingIdsAmong(viewerId, otherUserIds);

		return CursorSlice.of(rows, size, f -> Cursor.of(f.getCreatedAt(), f.getId()))
				.map(f -> toSummary(f.getFollower(), followingIds.contains(f.getFollower().getId())));
	}

	/** 팔로잉 목록. listFollowers() 와 같은 이유로 차단 관계면 빈 목록을 돌려줌. */
	@Loggable("팔로잉 목록 조회")
	public CursorSlice<UserSummaryResponse> listFollowings(Long viewerId, Long userId, Cursor cursor, int size) {
		ensureUserExists(userId);

		if (userBlockRepository.existsMutualBlock(viewerId, userId)) {
			return CursorSlice.empty();
		}

		List<Follow> rows = followRepository.findFollowings(userId, cursor, size);
		if (rows.isEmpty()) {
			return CursorSlice.empty();
		}

		List<Long> otherUserIds = rows.stream().map(f -> f.getFollowing().getId()).distinct().toList();
		Set<Long> followingIds = followingIdsAmong(viewerId, otherUserIds);

		return CursorSlice.of(rows, size, f -> Cursor.of(f.getCreatedAt(), f.getId()))
				.map(f -> toSummary(f.getFollowing(), followingIds.contains(f.getFollowing().getId())));
	}

	/**
	 * 팔로우. follow 행 생성과 같은 트랜잭션에서 대상의 follower_count 를 +1 하고 대상에게 알림을 발행함.
	 *
	 * follower·target 두 User 행을 아이디 오름차순으로 잠근 채로 진행함. follower_count 보호를
	 * 위해 target 만 잠그는 것으로는 부족함 — follow insert 가 FK 검증으로 follower·target 양쪽
	 * 행에 공유 락을 걸기 때문에, 맞팔(A→B, B→A)이 겹치면 두 트랜잭션이 target 만 잠근 채로
	 * 서로의 FK 공유 락을 기다리다 MySQL 데드락이 난다(실측 확인). 잠그는 순서를 아이디
	 * 오름차순으로 고정하면 순환 대기 자체가 생기지 않는다.
	 *
	 * 차단 관계면 거절함 — 차단은 '서로 마주치지 않게' 하는 조치인데(BlockPredicates 참고),
	 * 팔로우를 막지 않으면 차단당한 쪽이 알림에 자기 닉네임을 띄워 상대 앞에 나타날 수 있다.
	 */
	@Transactional
	@Loggable("팔로우")
	public void follow(Long viewerId, Long targetUserId) {
		if (viewerId.equals(targetUserId)) {
			log.info("자기 자신을 팔로우하려 함. userId={}", viewerId);
			throw new CustomException(FollowErrorCode.FOLLOW_400_SELF);
		}

		if (userBlockRepository.existsMutualBlock(viewerId, targetUserId)) {
			log.info("차단 관계에서 팔로우를 시도함. viewerId={} targetUserId={}", viewerId, targetUserId);
			throw new CustomException(FollowErrorCode.FOLLOW_403_BLOCKED);
		}

		User smaller = lockUser(Math.min(viewerId, targetUserId));
		User larger = lockUser(Math.max(viewerId, targetUserId));
		User follower = viewerId.compareTo(targetUserId) < 0 ? smaller : larger;
		User target = viewerId.compareTo(targetUserId) < 0 ? larger : smaller;

		if (followRepository.existsFollow(viewerId, targetUserId)) {
			log.info("이미 팔로우한 사용자를 다시 팔로우하려 함. viewerId={} targetUserId={}", viewerId, targetUserId);
			throw new CustomException(FollowErrorCode.FOLLOW_409);
		}

		try {
			followRepository.save(Follow.create(follower, target));
			followRepository.flush();
		} catch (DataIntegrityViolationException e) {
			log.info("동시 요청으로 팔로우 유니크 제약에 걸림. viewerId={} targetUserId={}", viewerId, targetUserId);
			throw new CustomException(FollowErrorCode.FOLLOW_409);
		}

		target.increaseFollowerCount();

		notificationService.notify(
				target, NotificationSettingType.FOLLOW, NotificationCategory.ACTIVITY,
				UserMapper.displayNickname(follower), "나를 팔로우하기 시작했어요.",
				follower.getProfileImageKey(), NotificationTargetType.USER, follower.getId());
	}

	/**
	 * 언팔로우. 없는 관계면 404 대신 조용히 성공 처리함(멱등) — 대상 사용자 자체가 없을 때만 404.
	 *
	 * follow() 와 같은 이유로 대상 User 행을 잠근 채로 follower_count 를 내림.
	 */
	@Transactional
	@Loggable("언팔로우")
	public void unfollow(Long viewerId, Long targetUserId) {
		User target = lockUser(targetUserId);

		followRepository.findByFollowerIdAndFollowingId(viewerId, targetUserId)
				.ifPresentOrElse(
						relation -> {
							followRepository.delete(relation);
							target.decreaseFollowerCount();
						},
						() -> log.info("팔로우하지 않은 사용자의 언팔로우를 시도함. viewerId={} targetUserId={}",
								viewerId, targetUserId));
	}

	private Set<Long> followingIdsAmong(Long viewerId, List<Long> candidateIds) {
		return candidateIds.isEmpty()
				? Set.of()
				: new HashSet<>(followRepository.findFollowingIdsAmong(viewerId, candidateIds));
	}

	private UserSummaryResponse toSummary(User user, boolean following) {
		return userMapper.toUserSummaryResponse(user, toImageUrl(user.getProfileImageKey()), following);
	}

	private String toImageUrl(String imageKey) {
		return imageKey == null ? null : fileStorage.toUrl(imageKey);
	}

	private void ensureUserExists(Long userId) {
		if (!userRepository.existsById(userId)) {
			log.warn("존재하지 않는 사용자입니다. userId={}", userId);
			throw new CustomException(UserErrorCode.USER_404);
		}
	}

	private User lockUser(Long userId) {
		return userRepository.findByIdForUpdate(userId)
				.orElseThrow(() -> {
					log.warn("존재하지 않는 사용자입니다. userId={}", userId);
					return new CustomException(UserErrorCode.USER_404);
				});
	}
}
