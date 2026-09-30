package com.tenure.domain.item.service;

import com.tenure.domain.category.entity.Category;
import com.tenure.domain.category.enums.SizeScheme;
import com.tenure.domain.category.exception.CategoryErrorCode;
import com.tenure.domain.category.repository.CategoryRepository;
import com.tenure.domain.category.repository.SizeOptionRepository;
import com.tenure.domain.item.dto.request.ItemCreateRequest;
import com.tenure.domain.item.dto.request.ItemUpdateRequest;
import com.tenure.domain.item.dto.response.ItemIdResponse;
import com.tenure.domain.item.entity.Item;
import com.tenure.domain.item.entity.ItemOwnership;
import com.tenure.domain.item.exception.ItemErrorCode;
import com.tenure.domain.item.mapper.ItemMapper;
import com.tenure.domain.item.repository.ItemOwnershipRepository;
import com.tenure.domain.item.repository.ItemRepository;
import com.tenure.domain.listing.enums.SaleStatus;
import com.tenure.domain.listing.repository.ItemListingRepository;
import com.tenure.domain.order.enums.OrderStatus;
import com.tenure.domain.order.repository.TradeOrderRepository;
import com.tenure.domain.user.entity.User;
import com.tenure.domain.user.exception.UserErrorCode;
import com.tenure.domain.user.repository.UserRepository;
import com.tenure.global.exception.CustomException;
import com.tenure.global.logging.Loggable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 아이템 등록·수정·삭제.
 *
 * 등록은 아이템과 최초 소유 구간을 한 트랜잭션에서 만든다. 소유 구간이 없으면 게시물에
 * 태그할 수 없어서 — 태그 자격이 '현재 소유 구간이 열려 있는가' 로 판정된다 — 아이템만
 * 저장되면 쓸 수 없는 행이 남는다.
 *
 * 예외를 던지기 전에 판단 근거를 남긴다 — ErrorCode 자체는 ServiceLoggingAspect 가 찍는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ItemService {

	/** 끝난 거래. 이 둘만 아니면 진행 중으로 본다 — 중간에 상태가 늘어도 자동으로 포함된다 */
	private static final Set<OrderStatus> FINISHED_ORDER_STATUSES =
			EnumSet.of(OrderStatus.COMPLETED, OrderStatus.CANCELED);

	private final ItemRepository itemRepository;
	private final ItemOwnershipRepository itemOwnershipRepository;
	private final CategoryRepository categoryRepository;
	private final SizeOptionRepository sizeOptionRepository;
	private final ItemListingRepository itemListingRepository;
	private final TradeOrderRepository tradeOrderRepository;
	private final ItemMapper itemMapper;
	private final UserRepository userRepository;

	/**
	 * 아이템 등록.
	 *
	 * 최초 보유 날짜를 비우면 소유 구간은 등록일부터 연다. first_owned_date 는 null 로 남겨
	 * '언제부터 갖고 있었는지 모른다' 와 '등록 시점부터 기록한다' 를 나눠 표현한다.
	 */
	@Transactional
	@Loggable("아이템 등록")
	public ItemIdResponse create(Long userId, ItemCreateRequest request) {
		User registrant = findUser(userId);
		Category category = findLeafCategory(request.getCategoryId());

		// 칩에서 고른 값인지 확인함
		validateSizeLabel(category, request.getSizeLabel());

		// 아이템 저장
		Item registerItem = Item.register(
				registrant,
				category,
				request.getName(),
				request.getBrandName(),
				request.getSizeLabel(),
				request.getWearTarget(),
				request.getCoverImageKey(),
				request.getDescription(),
				request.getFirstOwnedDate()
		);

		Item item = itemRepository.save(registerItem);

		// 보유 시작일을 비웠으면 등록일부터 기록함
		LocalDate ownedFrom = request.getFirstOwnedDate() != null
				? request.getFirstOwnedDate()
				: LocalDate.now();

		// 소유 구간이 있어야 게시물에 태그할 수 있음
		itemOwnershipRepository.save(
				ItemOwnership.openInitial(item, registrant, ownedFrom)
		);

		return itemMapper.toItemIdResponse(item);
	}

	/**
	 * 아이템 상세 수정.
	 *
	 * 최초 보유 날짜는 바꿀 수 없다. 그 값이 최초 소유 구간의 시작일이고 구매자가
	 * 보고 산 'Tenure 기록 기준' 의 첫 줄이라, 등록 뒤에는 누구도 손대지 못하게 한다.
	 */
	@Transactional
	@Loggable("아이템 수정")
	public ItemIdResponse update(Long userId, Long itemId, ItemUpdateRequest request) {

		// 아이템이 현재 user의 것이 맞는지 검증
		Item item = findOwnedItem(userId, itemId);

		// 카테고리를 안 보냈으면 기존 값을 그대로 씀
		Category category = request.getCategoryId() == null
				? item.getCategory()
				: findLeafCategory(request.getCategoryId());

		// 사이즈나 카테고리를 건드렸을 때만 본다. 매번 검사하면 칩 세트를 고쳤을 때
		// 기존 사이즈를 가진 아이템이 이름조차 못 고치게 됨
		if (request.getSizeLabel() != null || request.getCategoryId() != null) {

			// 카테고리만 바꿔도 검사가 필요함 — 상의(L)를 신발로 옮기면 L 은 신발 칩에 없음
			String sizeLabel = request.getSizeLabel() == null
					? item.getSizeLabel()
					: request.getSizeLabel();

			validateSizeLabel(category, sizeLabel);
		}

		item.update(
				request.getCategoryId() == null ? null : category,
				request.getName(),
				request.getBrandName(),
				request.getSizeLabel(),
				request.getWearTarget(),
				request.getCoverImageKey(),
				request.getDescription()
		);

		// 구매 제안 변경 여부
		if (request.getOfferAllowed() != null) {
			item.changeOfferAllowed(request.getOfferAllowed());
		}

		return itemMapper.toItemIdResponse(item);
	}

	/**
	 * 아이템 삭제 (soft).
	 *
	 * 태그는 남아 '삭제된 아이템' 으로 표시된다 — 그래서 물리 삭제를 하지 않는다.
	 * 판매 중이거나 끝나지 않은 거래가 있으면 막는다.
	 */
	@Transactional
	@Loggable("아이템 삭제")
	public void delete(Long userId, Long itemId) {

		// 현재 본인의 아이템인지 확인
		Item item = findOwnedItem(userId, itemId);

		// 판매 중이면 지울 수 없음
		if (itemListingRepository.existsByItemIdAndSaleStatus(itemId, SaleStatus.ON_SALE)) {
			log.info("판매 중이라 삭제할 수 없다 itemId={}", itemId);
			throw new CustomException(ItemErrorCode.ITEM_409_ON_SALE);
		}

		// 끝나지 않은 거래가 있어도 막음
		if (tradeOrderRepository.existsInProgress(itemId, FINISHED_ORDER_STATUSES)) {
			log.info("진행 중인 거래가 있어 삭제할 수 없다 itemId={}", itemId);
			throw new CustomException(ItemErrorCode.ITEM_409_ON_SALE);
		}

		// 태그가 '삭제된 아이템' 으로 남아야 해서 행은 지우지 않음
		item.delete(LocalDateTime.now());
	}

	/** 소유자 본인만 고치고 지운다. 등록자여도 팔았으면 남의 옷이다. */
	private Item findOwnedItem(Long userId, Long itemId) {
		Item item = itemRepository.findActiveWithCategory(itemId)
				.orElseThrow(() -> {
					log.warn("아이템을 찾을 수 없습니다. itemId = {}", itemId);
					return new CustomException(ItemErrorCode.ITEM_404);
				});

		// 등록자여도 팔았으면 남의 옷임
		itemOwnershipRepository.findCurrent(itemId)
				.filter(ownership -> ownership.getOwner().getId().equals(userId))
				.orElseThrow(() -> {
					log.info("현재 소유자가 아니다 userId={} itemId={}", userId, itemId);
					return new CustomException(ItemErrorCode.ITEM_403);
				});

		return item;
	}

	private User findUser(Long userId) {
		return userRepository.findById(userId)
				.orElseThrow(() -> new CustomException(UserErrorCode.USER_404));
	}

	/** 대분류 id 를 보냈거나 없는 id 를 보내면 여기서 갈린다. */
	private Category findLeafCategory(Long categoryId) {
		return categoryRepository.findLeafWithParent(categoryId)
				.orElseThrow(() -> {
					log.info("세부 카테고리가 아니거나 존재하지 않는다 categoryId={}", categoryId);
					return new CustomException(
							categoryRepository.existsById(categoryId)
									? ItemErrorCode.ITEM_400_CATEGORY
									: CategoryErrorCode.CATEGORY_404);
				});
	}

	/**
	 * 사이즈는 대분류마다 고를 수 있는 값이 다르다. 칩에서 고른 값만 통과시켜 공개 페이지와
	 * 상품 상세에 오타가 남지 않게 한다.
	 */
	private void validateSizeLabel(Category leaf, String sizeLabel) {
		if (sizeLabel == null || sizeLabel.isBlank()) {
			return;
		}

		// 칩 세트는 세부 카테고리가 아니라 대분류가 가짐
		SizeScheme scheme = leaf.getParent().getSizeScheme();
		if (scheme == null || !sizeOptionRepository.existsBySchemeAndLabel(scheme, sizeLabel)) {
			log.info("카테고리에 없는 사이즈다 categoryId={} scheme={} sizeLabel={}",
					leaf.getId(), scheme, sizeLabel);
			throw new CustomException(ItemErrorCode.ITEM_400_SIZE);
		}
	}
}
