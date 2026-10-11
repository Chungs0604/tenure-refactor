package com.tenure.domain.block.service;

import com.tenure.domain.block.entity.UserBlock;
import com.tenure.domain.block.exception.BlockErrorCode;
import com.tenure.domain.block.repository.UserBlockRepository;
import com.tenure.domain.user.entity.User;
import com.tenure.domain.user.exception.UserErrorCode;
import com.tenure.domain.user.repository.UserRepository;
import com.tenure.global.exception.CustomException;
import com.tenure.global.logging.Loggable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사용자 차단·차단해제.
 *
 * 예외를 던지기 전에 판단 근거를 남김 — ErrorCode 자체는 ServiceLoggingAspect 가 찍음.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BlockService {

	private final UserBlockRepository userBlockRepository;
	private final UserRepository userRepository;

	/** 차단. 동시 중복 요청은 유니크 제약으로 걸리므로 선검사 후 번역함(FollowService.follow() 와 같은 패턴). */
	@Transactional
	@Loggable("차단")
	public void block(Long viewerId, Long targetUserId) {
		if (viewerId.equals(targetUserId)) {
			log.info("자기 자신을 차단하려 함. userId={}", viewerId);
			throw new CustomException(BlockErrorCode.BLOCK_400_SELF);
		}

		User target = findUser(targetUserId);

		if (userBlockRepository.existsBlock(viewerId, targetUserId)) {
			log.info("이미 차단한 사용자를 다시 차단하려 함. viewerId={} targetUserId={}", viewerId, targetUserId);
			throw new CustomException(BlockErrorCode.BLOCK_409);
		}

		User blocker = findUser(viewerId);

		try {
			userBlockRepository.save(UserBlock.create(blocker, target));
			userBlockRepository.flush();
		} catch (DataIntegrityViolationException e) {
			log.info("동시 요청으로 차단 유니크 제약에 걸림. viewerId={} targetUserId={}", viewerId, targetUserId);
			throw new CustomException(BlockErrorCode.BLOCK_409);
		}
	}

	/** 차단해제. 하드 삭제. 차단하지 않은 관계면 조용히 성공 처리함(멱등) — 대상 사용자 자체가 없을 때만 404. */
	@Transactional
	@Loggable("차단해제")
	public void unblock(Long viewerId, Long targetUserId) {
		if (!userRepository.existsById(targetUserId)) {
			log.warn("존재하지 않는 사용자입니다. targetUserId={}", targetUserId);
			throw new CustomException(UserErrorCode.USER_404);
		}

		userBlockRepository.findByBlockerIdAndBlockedId(viewerId, targetUserId)
				.ifPresentOrElse(
						userBlockRepository::delete,
						() -> log.info("차단하지 않은 사용자의 차단해제를 시도함. viewerId={} targetUserId={}",
								viewerId, targetUserId));
	}

	private User findUser(Long userId) {
		return userRepository.findById(userId)
				.orElseThrow(() -> {
					log.warn("존재하지 않는 사용자입니다. userId={}", userId);
					return new CustomException(UserErrorCode.USER_404);
				});
	}
}
