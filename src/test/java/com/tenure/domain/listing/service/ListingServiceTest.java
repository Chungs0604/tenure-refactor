package com.tenure.domain.listing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.tenure.domain.category.entity.Category;
import com.tenure.domain.category.enums.MeasurementScheme;
import com.tenure.domain.category.enums.SizeScheme;
import com.tenure.domain.category.repository.CategoryRepository;
import com.tenure.domain.category.repository.SizeOptionRepository;
import com.tenure.domain.item.entity.Item;
import com.tenure.domain.item.entity.ItemMeasurement;
import com.tenure.domain.item.entity.ItemOwnership;
import com.tenure.domain.item.enums.ConditionCode;
import com.tenure.domain.item.enums.SaleStatus;
import com.tenure.domain.item.enums.WearTarget;
import com.tenure.domain.item.exception.ItemErrorCode;
import com.tenure.domain.item.repository.ItemConditionRepository;
import com.tenure.domain.item.repository.ItemMeasurementRepository;
import com.tenure.domain.item.repository.ItemOwnershipRepository;
import com.tenure.domain.item.repository.ItemRepository;
import com.tenure.domain.listing.dto.request.ListingCreateRequest;
import com.tenure.domain.listing.dto.request.ListingItemRequest;
import com.tenure.domain.listing.dto.response.FeaturedPostResponse;
import com.tenure.domain.category.enums.MeasurementField;
import com.tenure.domain.listing.entity.ItemListing;
import com.tenure.domain.listing.entity.ListingFeaturedPost;
import com.tenure.domain.listing.enums.FeeBearer;
import com.tenure.domain.listing.exception.ListingErrorCode;
import com.tenure.domain.listing.mapper.ListingMapper;
import com.tenure.domain.listing.mapper.ListingMapperImpl;
import com.tenure.domain.listing.repository.ItemListingRepository;
import com.tenure.domain.listing.repository.ListingFeaturedPostRepository;
import com.tenure.domain.notification.enums.NotificationCategory;
import com.tenure.domain.notification.enums.NotificationSettingType;
import com.tenure.domain.notification.enums.NotificationTargetType;
import com.tenure.domain.notification.service.NotificationService;
import com.tenure.domain.offer.enums.OfferCancelReason;
import com.tenure.domain.offer.service.OfferService;
import com.tenure.domain.order.repository.TradeOrderRepository;
import com.tenure.domain.payout.exception.PayoutErrorCode;
import com.tenure.domain.payout.repository.PayoutAccountRepository;
import com.tenure.domain.post.entity.Post;
import com.tenure.domain.post.enums.AspectRatio;
import com.tenure.domain.post.exception.PostErrorCode;
import com.tenure.domain.post.repository.PostRepository;
import com.tenure.domain.user.entity.User;
import com.tenure.domain.user.enums.Gender;
import com.tenure.domain.user.enums.UserGrade;
import com.tenure.domain.wishlist.repository.WishlistRepository;
import com.tenure.global.exception.CustomException;
import com.tenure.global.exception.ErrorCode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import com.tenure.domain.item.dto.request.ItemForm;
import com.tenure.domain.item.service.ItemFormApplier;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ListingService")
class ListingServiceTest {

	private static final Long USER_ID = 1L;
	private static final Long OTHER_USER_ID = 2L;
	private static final Long ITEM_ID = 100L;
	private static final Long LISTING_ID = 200L;
	private static final Long POST_ID = 300L;
	private static final Long CATEGORY_ID = 116L;
	private static final Long ROOT_CATEGORY_ID = 2L;

	@Mock private ItemListingRepository itemListingRepository;
	@Mock private ListingFeaturedPostRepository listingFeaturedPostRepository;
	@Mock private ItemRepository itemRepository;
	@Mock private ItemFormApplier itemFormApplier;
	@Mock private ItemOwnershipRepository itemOwnershipRepository;
	@Mock private PayoutAccountRepository payoutAccountRepository;
	@Mock private PostRepository postRepository;
	@Mock private FeaturedPostAssembler featuredPostAssembler;
	@Mock private TradeOrderRepository tradeOrderRepository;
	@Mock private OfferService offerService;
	@Mock private WishlistRepository wishlistRepository;
	@Mock private NotificationService notificationService;

	// 매핑은 검증 대상이라 mock 이 아니라 실제 생성 구현을 넣는다
	@Spy private ListingMapper listingMapper = new ListingMapperImpl();

	@InjectMocks private ListingService listingService;

	@Nested
	@DisplayName("판매 전환")
	class Create {

		@Test
		@DisplayName("판매 등록을 만들고 아이템을 판매중으로 바꾼다")
		void create_opensListingAndMarksItemOnSale() {
			Item item = item();
			givenSellable(item, UserGrade.RECORD);

			listingService.create(USER_ID, ITEM_ID, request(320000, 5000, FeeBearer.BUYER));

			ArgumentCaptor<ItemListing> captor = ArgumentCaptor.forClass(ItemListing.class);
			verify(itemListingRepository).save(captor.capture());

			assertThat(captor.getValue().getPrice()).isEqualTo(320000);
			assertThat(captor.getValue().getShippingFee()).isEqualTo(5000);
			assertThat(captor.getValue().getFeeBearer()).isEqualTo(FeeBearer.BUYER);
			assertThat(captor.getValue().getListedAt()).isNotNull();
			assertThat(captor.getValue().getClosedAt()).isNull();
			assertThat(item.getSaleStatus()).isEqualTo(SaleStatus.ON_SALE);
		}

		@Test
		@DisplayName("아이템 정보도 함께 갱신한다 — 한 화면·한 CTA 라 아이템 도메인에 넘긴다")
		void create_delegatesItemFormToItemService() {
			Item item = item();
			givenSellable(item, UserGrade.RECORD);

			listingService.create(USER_ID, ITEM_ID, request(320000, 5000, FeeBearer.SELLER));

			ArgumentCaptor<ItemForm> captor = ArgumentCaptor.forClass(ItemForm.class);
			verify(itemFormApplier).apply(eq(item), captor.capture());

			assertThat(captor.getValue().getBrandName()).isEqualTo("Levis");
			assertThat(captor.getValue().getName()).isEqualTo("LVC 1955 501");
			assertThat(captor.getValue().getSizeLabel()).isEqualTo("L");

			// 대표 사진은 판매 전환에만 있는 입력이라 여기서 직접 바꾼다
			assertThat(item.getCoverImageKey()).isEqualTo("item/2026/09/new.jpg");
			verify(itemFormApplier).replaceMeasurements(eq(item), any(), any());
			verify(itemFormApplier).replaceConditions(eq(item), any());
		}

		@Test
		@DisplayName("정산 계좌가 없으면 막는다 — 판매 대금을 받을 곳이 없다")
		void create_noPayoutAccount_rejected() {
			Item item = item();
			givenSellable(item, UserGrade.RECORD);
			given(payoutAccountRepository.existsByUserId(USER_ID)).willReturn(false);

			assertThatThrownBy(() -> listingService.create(
					USER_ID, ITEM_ID, request(320000, 5000, FeeBearer.SELLER)))
					.isInstanceOf(CustomException.class)
					.extracting(ListingServiceTest::errorCodeOf)
					.isEqualTo(PayoutErrorCode.PAYOUT_400_REQUIRED);

			verify(itemListingRepository, never()).save(any());
		}

		@Test
		@DisplayName("이미 열린 판매 등록이 있으면 막는다 — 어느 가격을 그릴지 정할 수 없다")
		void create_alreadyOpen_rejected() {
			Item item = item();
			givenSellable(item, UserGrade.RECORD);
			given(itemListingRepository.findOpenByItemId(ITEM_ID))
					.willReturn(Optional.of(listing(item, seller(UserGrade.RECORD))));

			assertThatThrownBy(() -> listingService.create(
					USER_ID, ITEM_ID, request(320000, 5000, FeeBearer.SELLER)))
					.isInstanceOf(CustomException.class)
					.extracting(ListingServiceTest::errorCodeOf)
					.isEqualTo(ListingErrorCode.LISTING_409_ALREADY_OPEN);
		}

		@Test
		@DisplayName("진행 중인 거래가 있으면 막는다 — 열린 등록이 없어도 이미 수락된 거래가 있을 수 있다")
		void create_inTrade_rejected() {
			Item item = item();
			givenSellable(item, UserGrade.RECORD);
			given(tradeOrderRepository.existsInProgress(anyLong(), any())).willReturn(true);

			assertThatThrownBy(() -> listingService.create(
					USER_ID, ITEM_ID, request(320000, 5000, FeeBearer.SELLER)))
					.isInstanceOf(CustomException.class)
					.extracting(ListingServiceTest::errorCodeOf)
					.isEqualTo(ListingErrorCode.LISTING_409_IN_TRADE);

			verify(itemListingRepository, never()).save(any());
		}

		@Test
		@DisplayName("기본 사용자가 수수료 부담을 보내면 막는다 — 화면에서 칩이 비활성이다")
		void create_basicUserSendsFeeBearer_rejected() {
			Item item = item();
			givenSellable(item, UserGrade.BASIC);

			assertThatThrownBy(() -> listingService.create(
					USER_ID, ITEM_ID, request(320000, null, FeeBearer.BUYER)))
					.isInstanceOf(CustomException.class)
					.extracting(ListingServiceTest::errorCodeOf)
					.isEqualTo(ListingErrorCode.LISTING_403_FEE_BEARER);
		}

		@Test
		@DisplayName("기본 사용자가 배송비를 보내도 막는다")
		void create_basicUserSendsShippingFee_rejected() {
			Item item = item();
			givenSellable(item, UserGrade.BASIC);

			assertThatThrownBy(() -> listingService.create(
					USER_ID, ITEM_ID, request(320000, 5000, null)))
					.isInstanceOf(CustomException.class)
					.extracting(ListingServiceTest::errorCodeOf)
					.isEqualTo(ListingErrorCode.LISTING_403_FEE_BEARER);
		}

		@Test
		@DisplayName("기본 사용자는 판매자 부담·배송비 0 으로 고정된다")
		void create_basicUser_fixedTerms() {
			Item item = item();
			givenSellable(item, UserGrade.BASIC);

			listingService.create(USER_ID, ITEM_ID, request(320000, null, null));

			ArgumentCaptor<ItemListing> captor = ArgumentCaptor.forClass(ItemListing.class);
			verify(itemListingRepository).save(captor.capture());

			assertThat(captor.getValue().getFeeBearer()).isEqualTo(FeeBearer.SELLER);
			assertThat(captor.getValue().getShippingFee()).isZero();
		}

		@Test
		@DisplayName("현재 소유자가 아니면 막는다 — 등록자여도 팔았으면 남의 옷이다")
		void create_notOwner_forbidden() {
			Item item = item();
			givenSellable(item, UserGrade.RECORD);
			given(itemOwnershipRepository.findCurrent(ITEM_ID))
					.willReturn(Optional.of(ownershipOf(item, user(OTHER_USER_ID, UserGrade.RECORD))));

			assertThatThrownBy(() -> listingService.create(
					USER_ID, ITEM_ID, request(320000, 5000, FeeBearer.SELLER)))
					.isInstanceOf(CustomException.class)
					.extracting(ListingServiceTest::errorCodeOf)
					.isEqualTo(ItemErrorCode.ITEM_403);
		}

		@Test
		@DisplayName("관심 등록하고 알림을 켠 사용자에게 판매 전환을 알린다")
		void create_notifiesAlarmEnabledWishlisters() {
			Item item = item();
			givenSellable(item, UserGrade.RECORD);
			User wishUser = user(900L, UserGrade.RECORD);
			given(wishlistRepository.findAlarmEnabledUsersByItemId(ITEM_ID)).willReturn(List.of(wishUser));

			listingService.create(USER_ID, ITEM_ID, request(320000, 5000, FeeBearer.BUYER));

			verify(notificationService).notifyAll(
					eq(List.of(wishUser)), eq(NotificationSettingType.WISHLIST_ITEM), eq(NotificationCategory.WISHLIST),
					eq(item.getName()), eq("판매 중으로 전환됐어요."), eq(item.getCoverImageKey()),
					eq(NotificationTargetType.ITEM), eq(ITEM_ID));
		}
	}

	@Nested
	@DisplayName("대표 OOTD")
	class FeaturedPosts {

		@Test
		@DisplayName("보낸 순서가 노출 순서가 된다 — 선택 화면의 배지 순서다")
		void create_featuredPostOrderFollowsRequest() {
			Item item = item();
			givenSellable(item, UserGrade.RECORD);

			Post first = post(POST_ID, USER_ID);
			Post second = post(POST_ID + 1, USER_ID);
			given(postRepository.findActiveByIds(any())).willReturn(List.of(second, first));

			ListingCreateRequest request = requestBuilder(320000, 5000, FeeBearer.SELLER)
					.featuredPostIds(List.of(POST_ID, POST_ID + 1))
					.build();

			listingService.create(USER_ID, ITEM_ID, request);

			ArgumentCaptor<List<ListingFeaturedPost>> captor = ArgumentCaptor.forClass(List.class);
			verify(listingFeaturedPostRepository).saveAll(captor.capture());

			assertThat(captor.getValue())
					.extracting(row -> row.getPost().getId(), ListingFeaturedPost::getSortOrder)
					.containsExactly(
							org.assertj.core.api.Assertions.tuple(POST_ID, 0),
							org.assertj.core.api.Assertions.tuple(POST_ID + 1, 1));
		}

		@Test
		@DisplayName("5개를 넘으면 막는다")
		void create_tooManyFeaturedPosts_rejected() {
			Item item = item();
			givenSellable(item, UserGrade.RECORD);

			ListingCreateRequest request = requestBuilder(320000, 5000, FeeBearer.SELLER)
					.featuredPostIds(List.of(1L, 2L, 3L, 4L, 5L, 6L))
					.build();

			assertThatThrownBy(() -> listingService.create(USER_ID, ITEM_ID, request))
					.isInstanceOf(CustomException.class)
					.extracting(ListingServiceTest::errorCodeOf)
					.isEqualTo(ListingErrorCode.LISTING_400_FEATURED_RANGE);
		}

		@Test
		@DisplayName("중복을 걷어낸 뒤 개수를 센다 — 같은 게시물을 두 번 보내도 1개다")
		void create_duplicateFeaturedPosts_countedOnce() {
			Item item = item();
			givenSellable(item, UserGrade.RECORD);
			given(postRepository.findActiveByIds(any()))
					.willReturn(List.of(post(POST_ID, USER_ID)));

			ListingCreateRequest request = requestBuilder(320000, 5000, FeeBearer.SELLER)
					.featuredPostIds(List.of(POST_ID, POST_ID))
					.build();

			listingService.create(USER_ID, ITEM_ID, request);

			ArgumentCaptor<List<ListingFeaturedPost>> captor = ArgumentCaptor.forClass(List.class);
			verify(listingFeaturedPostRepository).saveAll(captor.capture());
			assertThat(captor.getValue()).hasSize(1);
		}

		@Test
		@DisplayName("남의 게시물은 걸 수 없다 — 남의 OOTD 로 내 상품을 증명할 수 없다")
		void create_othersPost_forbidden() {
			Item item = item();
			givenSellable(item, UserGrade.RECORD);
			given(postRepository.findActiveByIds(any()))
					.willReturn(List.of(post(POST_ID, OTHER_USER_ID)));

			assertThatThrownBy(() -> listingService.create(
					USER_ID, ITEM_ID, request(320000, 5000, FeeBearer.SELLER)))
					.isInstanceOf(CustomException.class)
					.extracting(ListingServiceTest::errorCodeOf)
					.isEqualTo(PostErrorCode.POST_403);
		}

		@Test
		@DisplayName("삭제된 게시물은 걸 수 없다")
		void create_deletedPost_notFound() {
			Item item = item();
			givenSellable(item, UserGrade.RECORD);
			given(postRepository.findActiveByIds(any())).willReturn(List.of());

			assertThatThrownBy(() -> listingService.create(
					USER_ID, ITEM_ID, request(320000, 5000, FeeBearer.SELLER)))
					.isInstanceOf(CustomException.class)
					.extracting(ListingServiceTest::errorCodeOf)
					.isEqualTo(PostErrorCode.POST_404);
		}
	}

	@Nested
	@DisplayName("판매 상태 변경")
	class ChangeSaleStatus {

		@Test
		@DisplayName("미판매로 내리면 열린 등록을 닫고 아이템 상태를 바꾼다")
		void change_toNotForSale_closesListing() {
			Item item = item();
			item.changeSaleStatus(SaleStatus.ON_SALE);
			ItemListing listing = listing(item, seller(UserGrade.RECORD));

			givenOwnedItem(item);
			given(itemListingRepository.findOpenByItemId(ITEM_ID)).willReturn(Optional.of(listing));

			listingService.changeSaleStatus(USER_ID, ITEM_ID, SaleStatus.NOT_FOR_SALE);

			assertThat(item.getSaleStatus()).isEqualTo(SaleStatus.NOT_FOR_SALE);
			assertThat(listing.getClosedAt()).isNotNull();
		}

		@Test
		@DisplayName("판매 등록이 없는 아이템도 판매 완료로 바꿀 수 있다 — 상태는 아이템이 갖는다")
		void change_withoutListing_stillWorks() {
			Item item = item();
			givenOwnedItem(item);
			given(itemListingRepository.findOpenByItemId(ITEM_ID)).willReturn(Optional.empty());

			listingService.changeSaleStatus(USER_ID, ITEM_ID, SaleStatus.SOLD);

			assertThat(item.getSaleStatus()).isEqualTo(SaleStatus.SOLD);
		}

		@Test
		@DisplayName("판매중으로 되돌릴 수 없다 — 메뉴에 그 선택지가 없다")
		void change_toOnSale_rejected() {
			assertThatThrownBy(() ->
					listingService.changeSaleStatus(USER_ID, ITEM_ID, SaleStatus.ON_SALE))
					.isInstanceOf(CustomException.class)
					.extracting(ListingServiceTest::errorCodeOf)
					.isEqualTo(ListingErrorCode.LISTING_400_STATUS);
		}

		@Test
		@DisplayName("판매 완료로 바꾸면 관심 등록한 사람에게 알린다")
		void change_toSold_notifiesWishlisters() {
			Item item = item();
			givenOwnedItem(item);
			given(itemListingRepository.findOpenByItemId(ITEM_ID)).willReturn(Optional.empty());
			User wishUser = user(900L, UserGrade.RECORD);
			given(wishlistRepository.findAlarmEnabledUsersByItemId(ITEM_ID)).willReturn(List.of(wishUser));

			listingService.changeSaleStatus(USER_ID, ITEM_ID, SaleStatus.SOLD);

			verify(notificationService).notifyAll(
					eq(List.of(wishUser)), eq(NotificationSettingType.WISHLIST_ITEM), eq(NotificationCategory.WISHLIST),
					eq(item.getName()), eq("판매 완료됐어요."), eq(item.getCoverImageKey()),
					eq(NotificationTargetType.ITEM), eq(ITEM_ID));
		}

		@Test
		@DisplayName("미판매로 바꾸면 알리지 않는다")
		void change_toNotForSale_doesNotNotify() {
			Item item = item();
			item.changeSaleStatus(SaleStatus.ON_SALE);
			ItemListing listing = listing(item, seller(UserGrade.RECORD));
			givenOwnedItem(item);
			given(itemListingRepository.findOpenByItemId(ITEM_ID)).willReturn(Optional.of(listing));

			listingService.changeSaleStatus(USER_ID, ITEM_ID, SaleStatus.NOT_FOR_SALE);

			verify(notificationService, never()).notifyAll(any(), any(), any(), any(), any(), any(), any(), any());
		}

		@Test
		@DisplayName("진행 중인 거래가 있으면 막는다 — 거래가 상태를 직접 옮긴다")
		void change_inTrade_rejected() {
			Item item = item();
			givenOwnedItem(item);
			given(tradeOrderRepository.existsInProgress(anyLong(), any())).willReturn(true);

			assertThatThrownBy(() ->
					listingService.changeSaleStatus(USER_ID, ITEM_ID, SaleStatus.NOT_FOR_SALE))
					.isInstanceOf(CustomException.class)
					.extracting(ListingServiceTest::errorCodeOf)
					.isEqualTo(ListingErrorCode.LISTING_409_IN_TRADE);
		}
	}

	@Nested
	@DisplayName("탈퇴에 따른 판매중 아이템 정리")
	class CloseOnSaleItemsForWithdrawal {

		@Test
		@DisplayName("판매중 아이템을 미판매로 내리고 열린 등록을 닫고 대기 제안을 정리한다")
		void closes_onSaleItem_andItsListingAndPendingOffers() {
			Item item = item();
			item.changeSaleStatus(SaleStatus.ON_SALE);
			ItemListing listing = listing(item, seller(UserGrade.RECORD));

			given(itemRepository.findOnSaleItemIdsByCurrentOwner(USER_ID)).willReturn(List.of(ITEM_ID));
			given(itemRepository.findActiveForUpdate(ITEM_ID)).willReturn(Optional.of(item));
			given(itemListingRepository.findOpenByItemId(ITEM_ID)).willReturn(Optional.of(listing));

			listingService.closeOnSaleItemsForWithdrawal(USER_ID);

			assertThat(item.getSaleStatus()).isEqualTo(SaleStatus.NOT_FOR_SALE);
			assertThat(listing.getClosedAt()).isNotNull();
			verify(offerService).cancelPendingForDelist(ITEM_ID, OfferCancelReason.LISTING_DELISTED);
		}

		@Test
		@DisplayName("판매 등록이 없는 아이템도 미판매로 바꾼다 — 상태는 아이템이 갖는다")
		void closes_itemWithoutListing_stillWorks() {
			Item item = item();
			item.changeSaleStatus(SaleStatus.ON_SALE);

			given(itemRepository.findOnSaleItemIdsByCurrentOwner(USER_ID)).willReturn(List.of(ITEM_ID));
			given(itemRepository.findActiveForUpdate(ITEM_ID)).willReturn(Optional.of(item));
			given(itemListingRepository.findOpenByItemId(ITEM_ID)).willReturn(Optional.empty());

			listingService.closeOnSaleItemsForWithdrawal(USER_ID);

			assertThat(item.getSaleStatus()).isEqualTo(SaleStatus.NOT_FOR_SALE);
			verify(offerService).cancelPendingForDelist(ITEM_ID, OfferCancelReason.LISTING_DELISTED);
		}

		@Test
		@DisplayName("판매중 아이템이 여러 건이면 전부 정리한다")
		void closes_multipleOnSaleItems_allOfThem() {
			Long itemId2 = 101L;
			Item item1 = item();
			item1.changeSaleStatus(SaleStatus.ON_SALE);
			Item item2 = item();
			ReflectionTestUtils.setField(item2, "id", itemId2);
			item2.changeSaleStatus(SaleStatus.ON_SALE);

			given(itemRepository.findOnSaleItemIdsByCurrentOwner(USER_ID)).willReturn(List.of(ITEM_ID, itemId2));
			given(itemRepository.findActiveForUpdate(ITEM_ID)).willReturn(Optional.of(item1));
			given(itemRepository.findActiveForUpdate(itemId2)).willReturn(Optional.of(item2));
			given(itemListingRepository.findOpenByItemId(any())).willReturn(Optional.empty());

			listingService.closeOnSaleItemsForWithdrawal(USER_ID);

			assertThat(item1.getSaleStatus()).isEqualTo(SaleStatus.NOT_FOR_SALE);
			assertThat(item2.getSaleStatus()).isEqualTo(SaleStatus.NOT_FOR_SALE);
			verify(offerService).cancelPendingForDelist(ITEM_ID, OfferCancelReason.LISTING_DELISTED);
			verify(offerService).cancelPendingForDelist(itemId2, OfferCancelReason.LISTING_DELISTED);
		}

		@Test
		@DisplayName("재잠금 사이 다른 제안이 먼저 수락돼 판매완료로 바뀐 아이템은 건드리지 않는다")
		void skips_itemAlreadySoldBetweenFetchAndLock() {
			Item item = item();
			item.changeSaleStatus(SaleStatus.SOLD);

			given(itemRepository.findOnSaleItemIdsByCurrentOwner(USER_ID)).willReturn(List.of(ITEM_ID));
			given(itemRepository.findActiveForUpdate(ITEM_ID)).willReturn(Optional.of(item));

			listingService.closeOnSaleItemsForWithdrawal(USER_ID);

			assertThat(item.getSaleStatus()).isEqualTo(SaleStatus.SOLD);
			verifyNoInteractions(itemListingRepository, offerService);
		}

		@Test
		@DisplayName("판매중 아이템이 없으면 아무것도 하지 않는다")
		void doesNothing_whenNoOnSaleItems() {
			given(itemRepository.findOnSaleItemIdsByCurrentOwner(USER_ID)).willReturn(List.of());

			listingService.closeOnSaleItemsForWithdrawal(USER_ID);

			verify(itemRepository, never()).findActiveForUpdate(any());
			verifyNoInteractions(itemListingRepository, offerService);
		}
	}

	@Nested
	@DisplayName("판매 정보 수정")
	class Update {

		@Test
		@DisplayName("조건을 덮어쓴다 — 행을 새로 만들지 않는다")
		void update_overwritesTerms() {
			Item item = item();
			ItemListing listing = listing(item, seller(UserGrade.RECORD));
			givenUpdatableListing(listing);

			listingService.update(USER_ID, LISTING_ID, request(400000, 3000, FeeBearer.SPLIT));

			assertThat(listing.getPrice()).isEqualTo(400000);
			assertThat(listing.getShippingFee()).isEqualTo(3000);
			assertThat(listing.getFeeBearer()).isEqualTo(FeeBearer.SPLIT);
			verify(itemListingRepository, never()).save(any(ItemListing.class));
		}

		@Test
		@DisplayName("판매가 끝난 등록은 수정할 수 없다")
		void update_closedListing_rejected() {
			ItemListing listing = listing(item(), seller(UserGrade.RECORD));
			listing.close(LocalDateTime.now());
			given(itemListingRepository.findById(LISTING_ID)).willReturn(Optional.of(listing));

			assertThatThrownBy(() -> listingService.update(
					USER_ID, LISTING_ID, request(400000, 3000, FeeBearer.SELLER)))
					.isInstanceOf(CustomException.class)
					.extracting(ListingServiceTest::errorCodeOf)
					.isEqualTo(ListingErrorCode.LISTING_409_CLOSED);
		}

		@Test
		@DisplayName("남의 판매 등록은 건드릴 수 없다")
		void update_othersListing_forbidden() {
			ItemListing listing = listing(item(), user(OTHER_USER_ID, UserGrade.RECORD));
			given(itemListingRepository.findById(LISTING_ID)).willReturn(Optional.of(listing));

			assertThatThrownBy(() -> listingService.update(
					USER_ID, LISTING_ID, request(400000, 3000, FeeBearer.SELLER)))
					.isInstanceOf(CustomException.class)
					.extracting(ListingServiceTest::errorCodeOf)
					.isEqualTo(ListingErrorCode.LISTING_403);
		}

		@Test
		@DisplayName("없는 판매 등록이면 404 다")
		void update_unknownListing_notFound() {
			given(itemListingRepository.findById(LISTING_ID)).willReturn(Optional.empty());

			assertThatThrownBy(() -> listingService.update(
					USER_ID, LISTING_ID, request(400000, 3000, FeeBearer.SELLER)))
					.isInstanceOf(CustomException.class)
					.extracting(ListingServiceTest::errorCodeOf)
					.isEqualTo(ListingErrorCode.LISTING_404);
		}

		@Test
		@DisplayName("가격이 바뀌면 관심 등록한 사람에게 새 가격을 알린다")
		void update_priceChanged_notifiesWishlistersWithNewPrice() {
			Item item = item();
			ItemListing listing = listing(item, seller(UserGrade.RECORD));
			givenUpdatableListing(listing);
			User wishUser = user(900L, UserGrade.RECORD);
			given(wishlistRepository.findAlarmEnabledUsersByItemId(ITEM_ID)).willReturn(List.of(wishUser));

			listingService.update(USER_ID, LISTING_ID, request(400000, 3000, FeeBearer.SPLIT));

			verify(notificationService).notifyAll(
					eq(List.of(wishUser)), eq(NotificationSettingType.WISHLIST_ITEM), eq(NotificationCategory.WISHLIST),
					eq(item.getName()), eq("가격이 400,000원으로 변경됐어요."), eq(item.getCoverImageKey()),
					eq(NotificationTargetType.ITEM), eq(ITEM_ID));
		}

		@Test
		@DisplayName("가격이 안 바뀌면 알리지 않는다 — 배송비·설명만 바뀐 경우")
		void update_priceUnchanged_doesNotNotify() {
			Item item = item();
			ItemListing listing = listing(item, seller(UserGrade.RECORD));
			givenUpdatableListing(listing);

			listingService.update(USER_ID, LISTING_ID, request(320000, 3000, FeeBearer.SPLIT));

			verify(notificationService, never()).notifyAll(any(), any(), any(), any(), any(), any(), any(), any());
		}
	}

	@Nested
	@DisplayName("대표 OOTD 교체")
	class ReplaceFeaturedPosts {

		@Test
		@DisplayName("성공하면 교체된 대표 OOTD 목록을 돌려준다")
		void replace_success_returnsFeaturedPosts() {
			Item item = item();
			ItemListing listing = listing(item, seller(UserGrade.RECORD));
			givenUpdatableListing(listing);

			FeaturedPostResponse card = FeaturedPostResponse.builder()
					.postId(POST_ID)
					.imageUrl("https://cdn.tenure.app/post/2026/06/cover.jpg")
					.build();
			given(featuredPostAssembler.toCards(any())).willReturn(List.of(card));

			List<FeaturedPostResponse> response =
					listingService.replaceFeaturedPosts(USER_ID, LISTING_ID, List.of(POST_ID));

			assertThat(response).containsExactly(card);
			verify(listingFeaturedPostRepository).deleteAllByListingId(LISTING_ID);
			verify(listingFeaturedPostRepository).saveAll(any());
		}

		@Test
		@DisplayName("판매가 끝난 등록은 대표 OOTD를 교체할 수 없다")
		void replace_closedListing_rejected() {
			ItemListing listing = listing(item(), seller(UserGrade.RECORD));
			listing.close(LocalDateTime.now());
			given(itemListingRepository.findById(LISTING_ID)).willReturn(Optional.of(listing));

			assertThatThrownBy(() -> listingService.replaceFeaturedPosts(
					USER_ID, LISTING_ID, List.of(POST_ID)))
					.isInstanceOf(CustomException.class)
					.extracting(ListingServiceTest::errorCodeOf)
					.isEqualTo(ListingErrorCode.LISTING_409_CLOSED);

			verify(listingFeaturedPostRepository, never()).deleteAllByListingId(any());
		}

		@Test
		@DisplayName("남의 판매 등록은 건드릴 수 없다")
		void replace_othersListing_forbidden() {
			ItemListing listing = listing(item(), user(OTHER_USER_ID, UserGrade.RECORD));
			given(itemListingRepository.findById(LISTING_ID)).willReturn(Optional.of(listing));

			assertThatThrownBy(() -> listingService.replaceFeaturedPosts(
					USER_ID, LISTING_ID, List.of(POST_ID)))
					.isInstanceOf(CustomException.class)
					.extracting(ListingServiceTest::errorCodeOf)
					.isEqualTo(ListingErrorCode.LISTING_403);
		}

		@Test
		@DisplayName("5개를 넘으면 막는다")
		void replace_tooManyFeaturedPosts_rejected() {
			ItemListing listing = listing(item(), seller(UserGrade.RECORD));
			given(itemListingRepository.findById(LISTING_ID)).willReturn(Optional.of(listing));

			assertThatThrownBy(() -> listingService.replaceFeaturedPosts(
					USER_ID, LISTING_ID, List.of(1L, 2L, 3L, 4L, 5L, 6L)))
					.isInstanceOf(CustomException.class)
					.extracting(ListingServiceTest::errorCodeOf)
					.isEqualTo(ListingErrorCode.LISTING_400_FEATURED_RANGE);
		}

		@Test
		@DisplayName("남의 게시물은 걸 수 없다")
		void replace_othersPost_forbidden() {
			ItemListing listing = listing(item(), seller(UserGrade.RECORD));
			given(itemListingRepository.findById(LISTING_ID)).willReturn(Optional.of(listing));
			given(postRepository.findActiveByIds(any()))
					.willReturn(List.of(post(POST_ID, OTHER_USER_ID)));

			assertThatThrownBy(() -> listingService.replaceFeaturedPosts(
					USER_ID, LISTING_ID, List.of(POST_ID)))
					.isInstanceOf(CustomException.class)
					.extracting(ListingServiceTest::errorCodeOf)
					.isEqualTo(PostErrorCode.POST_403);
		}
	}

	// ── 픽스처

	private void givenSellable(Item item, UserGrade grade) {
		givenOwnedItem(item, grade);
		given(payoutAccountRepository.existsByUserId(USER_ID)).willReturn(true);
		given(itemListingRepository.findOpenByItemId(ITEM_ID)).willReturn(Optional.empty());
		givenItemServiceApplies();
		given(postRepository.findActiveByIds(any())).willReturn(List.of(post(POST_ID, USER_ID)));
		given(itemListingRepository.save(any(ItemListing.class)))
				.willAnswer(invocation -> {
					ItemListing saved = invocation.getArgument(0);
					ReflectionTestUtils.setField(saved, "id", LISTING_ID);
					return saved;
				});
	}

	private void givenOwnedItem(Item item) {
		givenOwnedItem(item, UserGrade.RECORD);
	}

	private void givenOwnedItem(Item item, UserGrade grade) {
		given(itemRepository.findActiveForUpdate(ITEM_ID)).willReturn(Optional.of(item));
		given(itemOwnershipRepository.findCurrent(ITEM_ID))
				.willReturn(Optional.of(ownershipOf(item, user(USER_ID, grade))));
		given(tradeOrderRepository.existsInProgress(anyLong(), any())).willReturn(false);
	}

	private void givenUpdatableListing(ItemListing listing) {
		given(itemListingRepository.findById(LISTING_ID)).willReturn(Optional.of(listing));
		givenItemServiceApplies();
		given(postRepository.findActiveByIds(any())).willReturn(List.of(post(POST_ID, USER_ID)));
	}

	/** 아이템 폼 반영은 ItemFormApplier 가 정본이라 여기서는 위임만 확인한다. */
	private void givenItemServiceApplies() {
		given(itemFormApplier.apply(any(), any()))
				.willReturn(leafCategory(MeasurementScheme.TOP));
	}

	private static ListingCreateRequest request(Integer price, Integer shippingFee, FeeBearer feeBearer) {
		return requestBuilder(price, shippingFee, feeBearer).build();
	}

	private static ListingCreateRequest.ListingCreateRequestBuilder requestBuilder(
			Integer price, Integer shippingFee, FeeBearer feeBearer
	) {
		return ListingCreateRequest.builder()
				.item(ListingItemRequest.builder()
						.brandName("Levis")
						.name("LVC 1955 501")
						.categoryId(CATEGORY_ID)
						.sizeLabel("L")
						.wearTarget(WearTarget.MALE)
						.coverImageKey("item/2026/09/new.jpg")
						.build())
				.price(price)
				.shippingFee(shippingFee)
				.feeBearer(feeBearer)
				.sellerNote("핏은 여유 있는 편입니다.")
				.featuredPostIds(List.of(POST_ID))
				.measurements(topMeasurements())
				.conditions(List.of(ConditionCode.STAIN));
	}

	/** 상의 세트가 받는 네 항목. */
	private static Map<MeasurementField, BigDecimal> topMeasurements() {
		return Map.of(
				MeasurementField.SHOULDER_WIDTH, BigDecimal.valueOf(45.0),
				MeasurementField.CHEST_WIDTH, BigDecimal.valueOf(52.0),
				MeasurementField.SLEEVE_LENGTH, BigDecimal.valueOf(61.0),
				MeasurementField.TOTAL_LENGTH, BigDecimal.valueOf(70.0));
	}

	private static Category leafCategory(MeasurementScheme scheme) {
		Category root = newCategory();
		ReflectionTestUtils.setField(root, "id", ROOT_CATEGORY_ID);
		ReflectionTestUtils.setField(root, "depth", 1);
		ReflectionTestUtils.setField(root, "sizeScheme", SizeScheme.TOP);
		ReflectionTestUtils.setField(root, "measurementScheme", scheme);

		Category leaf = newCategory();
		ReflectionTestUtils.setField(leaf, "id", CATEGORY_ID);
		ReflectionTestUtils.setField(leaf, "depth", 2);
		ReflectionTestUtils.setField(leaf, "name", "긴소매 티셔츠");
		ReflectionTestUtils.setField(leaf, "parent", root);
		return leaf;
	}

	private static Category newCategory() {
		try {
			var constructor = Category.class.getDeclaredConstructor();
			constructor.setAccessible(true);
			return constructor.newInstance();
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	private static Item item() {
		Item item = Item.register(user(USER_ID, UserGrade.RECORD), leafCategory(MeasurementScheme.TOP),
				"옛 이름", "옛 브랜드", "L", WearTarget.MALE, "item/2026/09/old.jpg",
				LocalDate.of(2026, 4, 20));
		ReflectionTestUtils.setField(item, "id", ITEM_ID);
		return item;
	}

	private static ItemListing listing(Item item, User seller) {
		ItemListing listing = ItemListing.open(
				item, seller, 320000, 5000, FeeBearer.SELLER, "설명", LocalDateTime.now());
		ReflectionTestUtils.setField(listing, "id", LISTING_ID);
		return listing;
	}

	private static ItemOwnership ownershipOf(Item item, User owner) {
		ItemOwnership ownership = ItemOwnership.openInitial(item, owner, LocalDate.of(2026, 4, 20));
		ReflectionTestUtils.setField(ownership, "id", 900L);
		return ownership;
	}

	private static Post post(Long id, Long authorId) {
		Post post = Post.publish(user(authorId, UserGrade.RECORD), LocalDate.of(2026, 5, 26),
				AspectRatio.RATIO_3_4, false);
		ReflectionTestUtils.setField(post, "id", id);
		return post;
	}

	private static User seller(UserGrade grade) {
		return user(USER_ID, grade);
	}

	private static User user(Long id, UserGrade grade) {
		User user = User.signUp("a@b.com", "$2a$10$hashed", "홍길동", "821012345678",
				"Sujun", null, Gender.MALE, 170, 64);
		ReflectionTestUtils.setField(user, "id", id);
		ReflectionTestUtils.setField(user, "grade", grade);
		return user;
	}

	private static ErrorCode errorCodeOf(Throwable e) {
		return ((CustomException) e).getErrorCode();
	}
}
