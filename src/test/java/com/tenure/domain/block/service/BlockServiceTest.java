package com.tenure.domain.block.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.tenure.domain.block.entity.UserBlock;
import com.tenure.domain.block.exception.BlockErrorCode;
import com.tenure.domain.block.repository.UserBlockRepository;
import com.tenure.domain.user.entity.User;
import com.tenure.domain.user.enums.Gender;
import com.tenure.domain.user.exception.UserErrorCode;
import com.tenure.domain.user.repository.UserRepository;
import com.tenure.global.exception.CustomException;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("BlockService")
class BlockServiceTest {

	private static final Long VIEWER_ID = 1L;
	private static final Long TARGET_ID = 2L;

	@Mock private UserBlockRepository userBlockRepository;
	@Mock private UserRepository userRepository;

	@InjectMocks private BlockService blockService;

	@Nested
	@DisplayName("차단")
	class BlockUser {

		@Test
		@DisplayName("처음 차단하면 user_block 행을 만든다")
		void block_notBlocked_createsRelation() {
			User target = user(TARGET_ID);
			User viewer = user(VIEWER_ID);
			given(userRepository.findById(TARGET_ID)).willReturn(Optional.of(target));
			given(userBlockRepository.existsBlock(VIEWER_ID, TARGET_ID)).willReturn(false);
			given(userRepository.findById(VIEWER_ID)).willReturn(Optional.of(viewer));

			blockService.block(VIEWER_ID, TARGET_ID);

			verify(userBlockRepository).save(any(UserBlock.class));
		}

		@Test
		@DisplayName("자기 자신을 차단하면 BLOCK_400_SELF 이고 아무것도 조회하지 않는다")
		void block_self_throwsSelfBlock() {
			assertThatThrownBy(() -> blockService.block(VIEWER_ID, VIEWER_ID))
					.isInstanceOf(CustomException.class)
					.extracting(e -> ((CustomException) e).getErrorCode())
					.isEqualTo(BlockErrorCode.BLOCK_400_SELF);

			verifyNoInteractions(userRepository, userBlockRepository);
		}

		@Test
		@DisplayName("이미 차단한 사용자면 BLOCK_409 이고 저장하지 않는다")
		void block_alreadyBlocked_throwsConflict() {
			User target = user(TARGET_ID);
			given(userRepository.findById(TARGET_ID)).willReturn(Optional.of(target));
			given(userBlockRepository.existsBlock(VIEWER_ID, TARGET_ID)).willReturn(true);

			assertThatThrownBy(() -> blockService.block(VIEWER_ID, TARGET_ID))
					.isInstanceOf(CustomException.class)
					.extracting(e -> ((CustomException) e).getErrorCode())
					.isEqualTo(BlockErrorCode.BLOCK_409);

			verify(userBlockRepository, never()).save(any());
		}

		@Test
		@DisplayName("동시 요청이 겹쳐 유니크 제약에 걸리면 BLOCK_409 로 번역한다")
		void block_concurrentConflict_translatesToBlock409() {
			User target = user(TARGET_ID);
			User viewer = user(VIEWER_ID);
			given(userRepository.findById(TARGET_ID)).willReturn(Optional.of(target));
			given(userBlockRepository.existsBlock(VIEWER_ID, TARGET_ID)).willReturn(false);
			given(userRepository.findById(VIEWER_ID)).willReturn(Optional.of(viewer));
			willThrow(new DataIntegrityViolationException("uk_user_block"))
					.given(userBlockRepository).save(any());

			assertThatThrownBy(() -> blockService.block(VIEWER_ID, TARGET_ID))
					.isInstanceOf(CustomException.class)
					.extracting(e -> ((CustomException) e).getErrorCode())
					.isEqualTo(BlockErrorCode.BLOCK_409);
		}

		@Test
		@DisplayName("없는 사용자를 차단하면 USER_404 이다")
		void block_targetNotFound_throwsUserNotFound() {
			given(userRepository.findById(TARGET_ID)).willReturn(Optional.empty());

			assertThatThrownBy(() -> blockService.block(VIEWER_ID, TARGET_ID))
					.isInstanceOf(CustomException.class)
					.extracting(e -> ((CustomException) e).getErrorCode())
					.isEqualTo(UserErrorCode.USER_404);

			verifyNoInteractions(userBlockRepository);
		}
	}

	@Nested
	@DisplayName("차단해제")
	class UnblockUser {

		@Test
		@DisplayName("차단 중이면 관계를 하드 삭제한다")
		void unblock_blocked_deletesRelation() {
			UserBlock relation = UserBlock.create(user(VIEWER_ID), user(TARGET_ID));
			given(userRepository.existsById(TARGET_ID)).willReturn(true);
			given(userBlockRepository.findByBlockerIdAndBlockedId(VIEWER_ID, TARGET_ID))
					.willReturn(Optional.of(relation));

			blockService.unblock(VIEWER_ID, TARGET_ID);

			verify(userBlockRepository).delete(relation);
		}

		@Test
		@DisplayName("차단하지 않은 관계의 차단해제는 예외 없이 성공 처리한다")
		void unblock_notBlocked_idempotentSuccess() {
			given(userRepository.existsById(TARGET_ID)).willReturn(true);
			given(userBlockRepository.findByBlockerIdAndBlockedId(VIEWER_ID, TARGET_ID))
					.willReturn(Optional.empty());

			blockService.unblock(VIEWER_ID, TARGET_ID);

			verify(userBlockRepository, never()).delete(any());
		}

		@Test
		@DisplayName("대상 사용자가 없으면 USER_404 이다")
		void unblock_targetNotFound_throwsUserNotFound() {
			given(userRepository.existsById(TARGET_ID)).willReturn(false);

			assertThatThrownBy(() -> blockService.unblock(VIEWER_ID, TARGET_ID))
					.isInstanceOf(CustomException.class)
					.extracting(e -> ((CustomException) e).getErrorCode())
					.isEqualTo(UserErrorCode.USER_404);

			verifyNoInteractions(userBlockRepository);
		}
	}

	private static User user(Long id) {
		User user = User.signUp("user" + id + "@tenure.app", "$2a$10$hashed", "홍길동",
				"821012345678", "nick" + id, null, Gender.MALE, 170, 64);
		ReflectionTestUtils.setField(user, "id", id);
		return user;
	}
}
