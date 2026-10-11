package com.tenure.domain.wishlist.service;

import com.tenure.domain.item.entity.Item;
import com.tenure.domain.item.exception.ItemErrorCode;
import com.tenure.domain.item.repository.ItemRepository;
import com.tenure.domain.listing.entity.ItemListing;
import com.tenure.domain.listing.repository.ItemListingRepository;
import com.tenure.domain.user.entity.User;
import com.tenure.domain.user.exception.UserErrorCode;
import com.tenure.domain.user.mapper.UserMapper;
import com.tenure.domain.user.repository.UserRepository;
import com.tenure.domain.wishlist.dto.response.WishCountResponse;
import com.tenure.domain.wishlist.dto.response.WishlistItemResponse;
import com.tenure.domain.wishlist.entity.Wishlist;
import com.tenure.domain.wishlist.enums.WishlistTab;
import com.tenure.domain.wishlist.exception.WishlistErrorCode;
import com.tenure.domain.wishlist.mapper.WishlistMapper;
import com.tenure.domain.wishlist.repository.WishlistRepository;
import com.tenure.global.exception.CustomException;
import com.tenure.global.logging.Loggable;
import com.tenure.global.pagination.Cursor;
import com.tenure.global.pagination.CursorSlice;
import com.tenure.global.storage.FileStorage;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 위시리스트 목록·관심 등록/해제·알림 on/off.
 *
 * wish_count 정본은 wishlist 이지만 표시용 카운터는 item 에 비정규화돼 있음 —
 * 판매를 내렸다 다시 걸어도 수가 유지돼야 해서다. 그래서 증감은 item 행을 잠그고 함.
 *
 * 예외를 던지기 전에 판단 근거를 남김 — ErrorCode 자체는 ServiceLoggingAspect 가 찍음.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WishlistService {

	private final WishlistRepository wishlistRepository;
	private final ItemRepository itemRepository;
	private final ItemListingRepository itemListingRepository;
	private final UserRepository userRepository;
	private final WishlistMapper wishlistMapper;
	private final FileStorage fileStorage;

	@Loggable("위시리스트 목록 조회")
	public CursorSlice<WishlistItemResponse> list(Long userId, WishlistTab tab, Cursor cursor, int size) {
		List<Wishlist> rows = wishlistRepository.findAllForList(userId, tab, cursor, size);
		if (rows.isEmpty()) {
			return CursorSlice.empty();
		}

		// 페이지에 있는 아이템들의 최신 판매 등록을 한 번에 읽음 — 행마다 조회하면 N+1
		List<Long> itemIds = rows.stream().map(w -> w.getItem().getId()).distinct().toList();
		Map<Long, ItemListing> latestListingByItemId = resolveLatestListingByItemId(itemIds);

		return CursorSlice.of(rows, size, w -> Cursor.of(w.getCreatedAt(), w.getId()))
				.map(w -> toItemResponse(w, latestListingByItemId.get(w.getItem().getId())));
	}

	/**
	 * 관심 등록. 중복 등록은 409 — existsByUserIdAndItemId 로 먼저 거르고, 그 사이 동시
	 * 요청이 끼어들면 uk_wishlist 위반을 같은 코드로 번역함(PayoutService.create() 와 같은 패턴).
	 */
	@Transactional
	@Loggable("관심 등록")
	public WishCountResponse addToWishlist(Long userId, Long itemId) {
		if (wishlistRepository.existsByUserIdAndItemId(userId, itemId)) {
			log.info("이미 관심 등록한 아이템에 다시 등록을 시도함. userId={} itemId={}", userId, itemId);
			throw new CustomException(WishlistErrorCode.WISHLIST_409);
		}

		User user = findUser(userId);
		// 삭제된 아이템에는 새로 담을 수 없음. 잠근 채로 읽어야 wish_count 증가가 동시 등록과
		// 안 섞임
		Item item = itemRepository.findActiveForUpdate(itemId)
				.orElseThrow(() -> {
					log.info("없거나 삭제된 아이템에 관심 등록을 시도함. itemId={}", itemId);
					return new CustomException(ItemErrorCode.ITEM_404);
				});

		try {
			wishlistRepository.save(Wishlist.register(user, item));
			wishlistRepository.flush();
		} catch (DataIntegrityViolationException e) {
			log.info("동시 요청으로 위시리스트 유니크 제약에 걸림. userId={} itemId={}", userId, itemId);
			throw new CustomException(WishlistErrorCode.WISHLIST_409);
		}

		item.increaseWishCount();
		return wishlistMapper.toWishCountResponse(item.getWishCount());
	}

	/** 관심 해제. */
	@Transactional
	@Loggable("관심 해제")
	public WishCountResponse removeFromWishlist(Long userId, Long itemId) {
		Wishlist entry = wishlistRepository.findByUserIdAndItemId(userId, itemId)
				.orElseThrow(() -> {
					log.info("관심 등록하지 않은 아이템의 해제를 시도함. userId={} itemId={}", userId, itemId);
					return new CustomException(WishlistErrorCode.WISHLIST_404);
				});

		// 그 사이 아이템이 삭제됐어도 wish_count 는 맞춰야 하므로 삭제 여부를 가리지 않고 잠금
		Item item = itemRepository.findForUpdate(itemId)
				.orElseThrow(() -> {
					log.warn("위시리스트가 가리키는 아이템이 없음. itemId={}", itemId);
					return new CustomException(ItemErrorCode.ITEM_404);
				});

		// 동시에 들어온 중복 해제 요청이 먼저 커밋되면 이 삭제는 영향받은 행이 0건이라
		// flush 에서 바로 실패함 — addToWishlist 의 유니크 제약 위반 번역과 같은 자리
		try {
			wishlistRepository.delete(entry);
			wishlistRepository.flush();
		} catch (OptimisticLockingFailureException e) {
			log.info("동시 요청으로 이미 해제된 항목을 다시 해제하려 함. userId={} itemId={}", userId, itemId);
			throw new CustomException(WishlistErrorCode.WISHLIST_404);
		}

		item.decreaseWishCount();

		return wishlistMapper.toWishCountResponse(item.getWishCount());
	}

	/** 위시 알림 on/off. 본인 항목만 바꿀 수 있음 */
	@Transactional
	@Loggable("위시 알림 설정")
	public void updateAlarm(Long userId, Long wishlistId, boolean alarmEnabled) {
		Wishlist entry = wishlistRepository.findById(wishlistId)
				.orElseThrow(() -> {
					log.info("없는 위시리스트 항목임. wishlistId={}", wishlistId);
					return new CustomException(WishlistErrorCode.WISHLIST_404);
				});

		// LAZY 프록시의 id 는 DB 를 치지 않고 읽음
		if (!entry.getUser().getId().equals(userId)) {
			log.warn("남의 위시리스트 항목의 알림을 바꾸려 함. wishlistId={} userId={}", wishlistId, userId);
			throw new CustomException(WishlistErrorCode.WISHLIST_403);
		}

		entry.changeAlarmEnabled(alarmEnabled);
	}

	/**
	 * 아이템별 최신 판매 등록(닫힌 것 포함). 정렬이 (item asc, listedAt desc, id desc) 라
	 * 각 itemId 에서 처음 만나는 행이 최신 — putIfAbsent 로 그 첫 행만 남김.
	 */
	private Map<Long, ItemListing> resolveLatestListingByItemId(List<Long> itemIds) {
		Map<Long, ItemListing> result = new LinkedHashMap<>();
		for (ItemListing listing : itemListingRepository.findAllByItemIdsOrderByLatest(itemIds)) {
			result.putIfAbsent(listing.getItem().getId(), listing);
		}
		return result;
	}

	/**
	 * 목록 행 하나를 응답으로 바꿈.
	 *
	 * price 는 열린 판매 등록이 있을 때만 실음 — 닫혔거나 아예 없으면 화면이 '구매 제안'
	 * 링크를 그림. sellerNickname 은 판매 등록 이력 자체가 없으면 null(아이템 상세 조회와 같은 규칙).
	 * listedAgo 는 반대로 항상 값이 있음 — 등록 이력이 없으면 아이템 등록 시각으로 돌려 셈.
	 */
	private WishlistItemResponse toItemResponse(Wishlist wishlist, ItemListing latestListing) {
		Item item = wishlist.getItem();
		boolean hasOpenListing = latestListing != null && latestListing.isOpen();

		String price = hasOpenListing ? String.valueOf(latestListing.getPrice()) : null;
		String sellerNickname = latestListing == null ? null : nicknameOf(latestListing.getSeller());
		LocalDateTime listedBasis = latestListing == null ? item.getCreatedAt() : latestListing.getListedAt();

		return wishlistMapper.toResponse(
				wishlist,
				fileStorage.toUrl(item.getCoverImageKey()),
				price,
				sellerNickname,
				relativeDaysAgo(listedBasis));
	}

	private String nicknameOf(User seller) {
		// 탈퇴한 판매자는 닉네임을 가림(ListingQueryService.toSeller() 와 같은 규칙)
		return seller.isWithdrawn() ? UserMapper.WITHDRAWN_NICKNAME : seller.getNickname();
	}

	private String relativeDaysAgo(LocalDateTime from) {
		long days = Duration.between(from, LocalDateTime.now()).toDays();
		return days <= 0 ? "오늘" : days + "일 전";
	}

	private User findUser(Long userId) {
		return userRepository.findById(userId)
				.orElseThrow(() -> {
					log.warn("존재하지 않는 사용자. userId={}", userId);
					return new CustomException(UserErrorCode.USER_404);
				});
	}
}
