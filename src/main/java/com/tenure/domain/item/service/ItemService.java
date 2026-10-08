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
import com.tenure.domain.order.enums.OrderStatus;
import com.tenure.domain.order.repository.TradeOrderRepository;
import com.tenure.domain.user.entity.User;
import com.tenure.domain.user.exception.UserErrorCode;
import com.tenure.domain.user.repository.UserRepository;
import com.tenure.global.exception.CustomException;
import com.tenure.global.logging.Loggable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.tenure.domain.category.enums.MeasurementField;
import com.tenure.domain.category.enums.MeasurementScheme;
import com.tenure.domain.item.dto.request.ItemForm;
import com.tenure.domain.item.entity.ItemCondition;
import com.tenure.domain.item.entity.ItemMeasurement;
import com.tenure.domain.item.enums.ConditionCode;
import com.tenure.domain.item.repository.ItemConditionRepository;
import com.tenure.domain.item.repository.ItemMeasurementRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

	private final ItemRepository itemRepository;
	private final ItemOwnershipRepository itemOwnershipRepository;
	private final ItemFormApplier itemFormApplier;
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
		// 세부 카테고리인지·칩에서 고른 사이즈인지 확인함
		Category category = itemFormApplier.resolveCategory(
				request.getCategoryId(), request.getSizeLabel());

		// 아이템 저장
		Item registerItem = Item.register(
				registrant,
				category,
				request.getName(),
				request.getBrandName(),
				request.getSizeLabel(),
				request.getWearTarget(),
				request.getCoverImageKey(),
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
	 * 아이템 상세 수정. 시트가 전체 폼이라 받은 값으로 통째로 갈아끼운다.
	 *
	 * 최초 보유 날짜는 바꿀 수 없다. 그 값이 최초 소유 구간의 시작일이고 구매자가
	 * 보고 산 'Tenure 기록 기준' 의 첫 줄이라, 등록 뒤에는 누구도 손대지 못하게 한다.
	 */
	@Transactional
	@Loggable("아이템 수정")
	public ItemIdResponse update(Long userId, Long itemId, ItemUpdateRequest request) {

		// 아이템이 현재 user의 것이 맞는지 검증
		Item item = findOwnedItem(userId, itemId);

		itemFormApplier.apply(item, ItemForm.builder()
				.categoryId(request.getCategoryId())
				.name(request.getName())
				.brandName(request.getBrandName())
				.sizeLabel(request.getSizeLabel())
				.wearTarget(request.getWearTarget())
				.build());

		return itemMapper.toItemIdResponse(item);
	}

	/**
	 * 구매 제안 허용 토글.
	 *
	 * 상세 수정 시트가 아니라 선택 모달(3910:43234)의 항목이라 엔드포인트를 나눴다 —
	 * 한 요청에 묶으면 토글만 누르는데도 폼 전체를 함께 보내야 한다.
	 */
	@Transactional
	@Loggable("구매 제안 허용 변경")
	public ItemIdResponse changeOfferAllowed(Long userId, Long itemId, boolean offerAllowed) {
		Item item = findOwnedItem(userId, itemId);
		item.changeOfferAllowed(offerAllowed);
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

		// 판매 중이면 지울 수 없음. 상태는 아이템이 들고 있어 판매 등록을 읽지 않는다 (DEC-026)
		if (item.isOnSale()) {
			log.info("판매 중이라 삭제할 수 없다 itemId={}", itemId);
			throw new CustomException(ItemErrorCode.ITEM_409_ON_SALE);
		}

		// 끝나지 않은 거래가 있어도 막음
		if (tradeOrderRepository.existsInProgress(itemId, OrderStatus.FINISHED)) {
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
}
