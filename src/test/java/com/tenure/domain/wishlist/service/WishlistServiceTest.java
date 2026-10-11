package com.tenure.domain.wishlist.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.tenure.domain.item.entity.Item;
import com.tenure.domain.item.enums.SaleStatus;
import com.tenure.domain.item.enums.WearTarget;
import com.tenure.domain.item.exception.ItemErrorCode;
import com.tenure.domain.item.repository.ItemRepository;
import com.tenure.domain.listing.entity.ItemListing;
import com.tenure.domain.listing.enums.FeeBearer;
import com.tenure.domain.listing.repository.ItemListingRepository;
import com.tenure.domain.user.entity.User;
import com.tenure.domain.user.enums.Gender;
import com.tenure.domain.user.repository.UserRepository;
import com.tenure.domain.wishlist.dto.response.WishCountResponse;
import com.tenure.domain.wishlist.dto.response.WishlistItemResponse;
import com.tenure.domain.wishlist.entity.Wishlist;
import com.tenure.domain.wishlist.enums.WishlistTab;
import com.tenure.domain.wishlist.exception.WishlistErrorCode;
import com.tenure.domain.wishlist.mapper.WishlistMapper;
import com.tenure.domain.wishlist.repository.WishlistRepository;
import com.tenure.global.exception.CustomException;
import com.tenure.global.exception.ErrorCode;
import com.tenure.global.pagination.Cursor;
import com.tenure.global.pagination.CursorSlice;
import com.tenure.global.storage.FileStorage;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.InjectMocks;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("WishlistService")
class WishlistServiceTest {

	private static final Long USER_ID = 1L;
	private static final Long OTHER_USER_ID = 2L;
	private static final Long ITEM_ID = 10L;
	private static final Long WISHLIST_ID = 100L;

	@Mock private WishlistRepository wishlistRepository;
	@Mock private ItemRepository itemRepository;
	@Mock private ItemListingRepository itemListingRepository;
	@Mock private UserRepository userRepository;
	@Mock private FileStorage fileStorage;

	/** mock 하지 않음 — mock 하면 엔티티에 필드를 넣고 응답에 안 실어도 테스트가 통과함 */
	@Spy private WishlistMapper wishlistMapper = Mappers.getMapper(WishlistMapper.class);

	@InjectMocks private WishlistService wishlistService;

	@Nested
	@DisplayName("관심 등록")
	class AddToWishlist {

		@Test
		@DisplayName("처음 등록하면 wishlist 행을 만들고 item.wish_count 를 올린다")
		void addToWishlist_notRegistered_increasesWishCount() {
			Item item = item(3);
			given(wishlistRepository.existsByUserIdAndItemId(USER_ID, ITEM_ID)).willReturn(false);
			given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(USER_ID)));
			given(itemRepository.findActiveForUpdate(ITEM_ID)).willReturn(Optional.of(item));

			WishCountResponse response = wishlistService.addToWishlist(USER_ID, ITEM_ID);

			assertThat(response.getWishCount()).isEqualTo(4);
			assertThat(item.getWishCount()).isEqualTo(4);
			verify(wishlistRepository).save(any(Wishlist.class));
		}

		@Test
		@DisplayName("이미 등록한 아이템이면 WISHLIST_409 이고 아무것도 바꾸지 않는다")
		void addToWishlist_alreadyRegistered_throwsConflict() {
			given(wishlistRepository.existsByUserIdAndItemId(USER_ID, ITEM_ID)).willReturn(true);

			assertThatThrownBy(() -> wishlistService.addToWishlist(USER_ID, ITEM_ID))
					.isInstanceOf(CustomException.class)
					.extracting(e -> ((CustomException) e).getErrorCode())
					.isEqualTo(WishlistErrorCode.WISHLIST_409);

			verifyNoInteractions(itemRepository);
			verify(wishlistRepository, never()).save(any());
		}

		@Test
		@DisplayName("동시 요청이 겹쳐 유니크 제약에 걸리면 WISHLIST_409 로 번역한다")
		void addToWishlist_concurrentConflict_translatesToWishlist409() {
			Item item = item(0);
			given(wishlistRepository.existsByUserIdAndItemId(USER_ID, ITEM_ID)).willReturn(false);
			given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(USER_ID)));
			given(itemRepository.findActiveForUpdate(ITEM_ID)).willReturn(Optional.of(item));
			willThrow(new DataIntegrityViolationException("uk_wishlist"))
					.given(wishlistRepository).save(any());

			assertThatThrownBy(() -> wishlistService.addToWishlist(USER_ID, ITEM_ID))
					.isInstanceOf(CustomException.class)
					.extracting(e -> ((CustomException) e).getErrorCode())
					.isEqualTo(WishlistErrorCode.WISHLIST_409);

			// 저장 실패로 끝났으므로 카운터는 안 올림
			assertThat(item.getWishCount()).isZero();
		}

		@Test
		@DisplayName("없거나 삭제된 아이템이면 ITEM_404 이다")
		void addToWishlist_deletedItem_throwsItemNotFound() {
			given(wishlistRepository.existsByUserIdAndItemId(USER_ID, ITEM_ID)).willReturn(false);
			given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(USER_ID)));
			given(itemRepository.findActiveForUpdate(ITEM_ID)).willReturn(Optional.empty());

			assertThatThrownBy(() -> wishlistService.addToWishlist(USER_ID, ITEM_ID))
					.isInstanceOf(CustomException.class)
					.extracting(e -> ((CustomException) e).getErrorCode())
					.isEqualTo(ItemErrorCode.ITEM_404);
		}
	}

	@Nested
	@DisplayName("관심 해제")
	class RemoveFromWishlist {

		@Test
		@DisplayName("등록돼 있으면 삭제하고 item.wish_count 를 내린다")
		void removeFromWishlist_registered_decreasesWishCount() {
			Item item = item(3);
			Wishlist entry = wishlistEntry(user(USER_ID), item, true);
			given(wishlistRepository.findByUserIdAndItemId(USER_ID, ITEM_ID)).willReturn(Optional.of(entry));
			given(itemRepository.findForUpdate(ITEM_ID)).willReturn(Optional.of(item));

			WishCountResponse response = wishlistService.removeFromWishlist(USER_ID, ITEM_ID);

			assertThat(response.getWishCount()).isEqualTo(2);
			assertThat(item.getWishCount()).isEqualTo(2);
			verify(wishlistRepository).delete(entry);
		}

		@Test
		@DisplayName("등록하지 않은 아이템이면 WISHLIST_404 이다")
		void removeFromWishlist_notRegistered_throwsNotFound() {
			given(wishlistRepository.findByUserIdAndItemId(USER_ID, ITEM_ID)).willReturn(Optional.empty());

			assertThatThrownBy(() -> wishlistService.removeFromWishlist(USER_ID, ITEM_ID))
					.isInstanceOf(CustomException.class)
					.extracting(e -> ((CustomException) e).getErrorCode())
					.isEqualTo(WishlistErrorCode.WISHLIST_404);

			verifyNoInteractions(itemRepository);
		}

		@Test
		@DisplayName("동시 해제 요청이 먼저 커밋돼 행이 이미 사라졌으면 WISHLIST_404 로 번역하고 카운터는 건드리지 않는다")
		void removeFromWishlist_concurrentConflict_translatesToWishlist404() {
			Item item = item(3);
			Wishlist entry = wishlistEntry(user(USER_ID), item, true);
			given(wishlistRepository.findByUserIdAndItemId(USER_ID, ITEM_ID)).willReturn(Optional.of(entry));
			given(itemRepository.findForUpdate(ITEM_ID)).willReturn(Optional.of(item));
			willThrow(new ObjectOptimisticLockingFailureException(Wishlist.class, entry.getId()))
					.given(wishlistRepository).flush();

			assertThatThrownBy(() -> wishlistService.removeFromWishlist(USER_ID, ITEM_ID))
					.isInstanceOf(CustomException.class)
					.extracting(e -> ((CustomException) e).getErrorCode())
					.isEqualTo(WishlistErrorCode.WISHLIST_404);

			// 삭제 자체는 먼저 반영된 결과라 카운터를 깎지 않는다
			assertThat(item.getWishCount()).isEqualTo(3);
		}
	}

	@Nested
	@DisplayName("위시 알림 on/off")
	class UpdateAlarm {

		@Test
		@DisplayName("본인 항목이면 알림 상태를 바꾼다")
		void updateAlarm_owner_changesAlarmEnabled() {
			Wishlist entry = wishlistEntry(user(USER_ID), item(0), true);
			ReflectionTestUtils.setField(entry, "id", WISHLIST_ID);
			given(wishlistRepository.findById(WISHLIST_ID)).willReturn(Optional.of(entry));

			wishlistService.updateAlarm(USER_ID, WISHLIST_ID, false);

			assertThat(entry.getAlarmEnabled()).isFalse();
		}

		@Test
		@DisplayName("남의 항목이면 WISHLIST_403 이고 바꾸지 않는다")
		void updateAlarm_notOwner_throwsForbidden() {
			Wishlist entry = wishlistEntry(user(OTHER_USER_ID), item(0), true);
			ReflectionTestUtils.setField(entry, "id", WISHLIST_ID);
			given(wishlistRepository.findById(WISHLIST_ID)).willReturn(Optional.of(entry));

			assertThatThrownBy(() -> wishlistService.updateAlarm(USER_ID, WISHLIST_ID, false))
					.isInstanceOf(CustomException.class)
					.extracting(e -> ((CustomException) e).getErrorCode())
					.isEqualTo(WishlistErrorCode.WISHLIST_403);

			assertThat(entry.getAlarmEnabled()).isTrue();
		}

		@Test
		@DisplayName("없는 항목이면 WISHLIST_404 이다")
		void updateAlarm_notFound_throwsNotFound() {
			given(wishlistRepository.findById(WISHLIST_ID)).willReturn(Optional.empty());

			assertThatThrownBy(() -> wishlistService.updateAlarm(USER_ID, WISHLIST_ID, false))
					.isInstanceOf(CustomException.class)
					.extracting(e -> ((CustomException) e).getErrorCode())
					.isEqualTo(WishlistErrorCode.WISHLIST_404);
		}
	}

	@Nested
	@DisplayName("위시리스트 목록")
	class ListWishlists {

		@Test
		@DisplayName("빈 목록이면 최신 판매 등록을 조회하지 않는다")
		void list_empty_skipsListingLookup() {
			given(wishlistRepository.findAllForList(USER_ID, WishlistTab.ALL, null, 20))
					.willReturn(List.of());

			CursorSlice<WishlistItemResponse> slice = wishlistService.list(USER_ID, WishlistTab.ALL, null, 20);

			assertThat(slice.isEmpty()).isTrue();
			verifyNoInteractions(itemListingRepository);
		}

		@Test
		@DisplayName("열린 판매 등록이 있으면 가격과 판매자를 함께 싣는다")
		void list_withOpenListing_includesPriceAndSeller() {
			User seller = user(OTHER_USER_ID);
			Item item = item(5);
			ReflectionTestUtils.setField(item, "id", ITEM_ID);
			Wishlist entry = wishlistEntry(user(USER_ID), item, true);
			ReflectionTestUtils.setField(entry, "id", WISHLIST_ID);
			ItemListing listing = ItemListing.open(
					item, seller, 100_000, 3_000, FeeBearer.SELLER, null, LocalDateTime.now());

			given(wishlistRepository.findAllForList(USER_ID, WishlistTab.ALL, null, 20))
					.willReturn(List.of(entry));
			given(itemListingRepository.findAllByItemIdsOrderByLatest(List.of(ITEM_ID)))
					.willReturn(List.of(listing));
			given(fileStorage.toUrl(item.getCoverImageKey())).willReturn("https://cdn.tenure.app/item.jpg");

			CursorSlice<WishlistItemResponse> slice = wishlistService.list(USER_ID, WishlistTab.ALL, null, 20);

			WishlistItemResponse response = slice.getContent().get(0);
			assertThat(response.getPrice()).isEqualTo("100000");
			assertThat(response.getSellerNickname()).isEqualTo(seller.getNickname());
			assertThat(response.getWishlistId()).isEqualTo(WISHLIST_ID);
			assertThat(response.getItemId()).isEqualTo(ITEM_ID);
		}

		@Test
		@DisplayName("판매 등록 이력이 없으면 가격·판매자는 null 이고 listedAgo 는 아이템 등록일 기준이다")
		void list_withoutListing_hasNullPriceAndSeller() {
			Item item = item(5);
			ReflectionTestUtils.setField(item, "id", ITEM_ID);
			Wishlist entry = wishlistEntry(user(USER_ID), item, true);
			ReflectionTestUtils.setField(entry, "id", WISHLIST_ID);

			given(wishlistRepository.findAllForList(USER_ID, WishlistTab.ALL, null, 20))
					.willReturn(List.of(entry));
			given(itemListingRepository.findAllByItemIdsOrderByLatest(List.of(ITEM_ID)))
					.willReturn(List.of());
			given(fileStorage.toUrl(item.getCoverImageKey())).willReturn("https://cdn.tenure.app/item.jpg");

			WishlistItemResponse response =
					wishlistService.list(USER_ID, WishlistTab.ALL, null, 20).getContent().get(0);

			assertThat(response.getPrice()).isNull();
			assertThat(response.getSellerNickname()).isNull();
			assertThat(response.getListedAgo()).isNotNull();
		}
	}

	private static User user(Long id) {
		User user = User.signUp("user" + id + "@tenure.app", "$2a$10$hashed", "홍길동",
				"821012345678", "nick" + id, null, Gender.MALE, 170, 64);
		ReflectionTestUtils.setField(user, "id", id);
		return user;
	}

	private static Item item(int wishCount) {
		Item item = Item.register(user(USER_ID), null, "LVC 1955 501", "Levis", "L",
				WearTarget.MALE, "item/2026/09/a.jpg", LocalDate.of(2025, 2, 8));
		ReflectionTestUtils.setField(item, "id", ITEM_ID);
		ReflectionTestUtils.setField(item, "wishCount", wishCount);
		ReflectionTestUtils.setField(item, "saleStatus", SaleStatus.NOT_FOR_SALE);
		ReflectionTestUtils.setField(item, "offerAllowed", true);
		ReflectionTestUtils.setField(item, "createdAt", LocalDateTime.now().minusDays(1));
		return item;
	}

	private static Wishlist wishlistEntry(User user, Item item, boolean alarmEnabled) {
		Wishlist entry = Wishlist.register(user, item);
		if (!alarmEnabled) {
			entry.changeAlarmEnabled(false);
		}
		ReflectionTestUtils.setField(entry, "createdAt", LocalDateTime.now());
		return entry;
	}
}
