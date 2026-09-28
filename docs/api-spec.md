# Tenure API 명세

> 정본 데이터 모델: [`schema.yaml`](./schema.yaml) · ERD: [`erd.md`](./erd.md)
> 엔드포인트 **95개** · 화면 **47개 전수 커버** · Spring Boot 4.1.1 / MySQL 8

> 이 문서는 **코드에 없는 정보**만 담는다 — API ID, 화면↔API 매핑, CRUD 커버리지, 설계 근거.
> 구현이 시작되면 springdoc 이 생성하는 OpenAPI 가 스키마의 정본이 된다.

---

## 1. 개요

**베이스 URL** `/api/v1`

**인증** `Authorization: Bearer <access token>` (JWT). 47개 화면 중 44개가 인증을 요구한다.
인증이 불필요한 것은 로그인·회원가입·비밀번호 찾기 경로뿐이다.

**공통 응답** — 성공·실패 모두 `BaseResponse<T>` 로 감싼다. ProblemDetail 을 쓰지 않는다.

성공:

```json
{ "success": true, "code": "COMMON_200", "message": "요청에 성공했습니다.", "data": {} }
```

실패:

```json
{ "success": false, "code": "USER_404", "message": "사용자를 찾을 수 없습니다.", "data": null }
```

HTTP 상태 코드는 `ErrorCode.getStatus()` 가 결정한다.

**페이징** — 목록은 예외 없이 페이징한다.

| 파라미터 | 기본 | 설명 |
|---|---|---|
| `page` | 0 | **0부터** 시작 |
| `size` | 20 | 최대 100 |
| `sort` | 화면별 상이 | `필드,asc\|desc` |

응답은 `BaseResponse<PageResponse<T>>` 이며 `content` · `page` · `size` · `totalElements` · `totalPages` · `hasNext` 를 갖는다. `first`/`last` 는 쓰지 않는다.

**필드 규칙** — JSON 은 camelCase. 금액은 문자열로 직렬화. 날짜는 ISO 8601.
`deletedAt` · `passwordHash` · `voidedAt` 같은 내부 필드는 응답에 노출하지 않는다.
이미지 컬럼은 **저장소 키**를 담으므로(`DEC-006`) 응답에서는 `FileStorage.toUrl()` 로 변환한 전체 URL 을 내려준다.

---

## 2. 전체 API 목록

| ID | Method | Path | 설명 | 관련 화면 | 관련 엔티티 | 인증 |
|---|---|---|---|---|---|:--:|
| API-001 | `POST` | `/api/v1/auth/login` | 이메일·비밀번호 로그인 | SCR-001 | User | X |
| API-002 | `POST` | `/api/v1/auth/logout` | 로그아웃 (refresh token 무효화) | SCR-010 | — | O |
| API-003 | `POST` | `/api/v1/auth/reissue` | access token 재발급 | —(화면 근거 없음) | — | X |
| API-004 | `POST` | `/api/v1/auth/email-verifications` | 인증번호 발송 (가입·비밀번호 찾기) | SCR-002, SCR-003 | —(Redis) | X |
| API-005 | `POST` | `/api/v1/auth/email-verifications/verify` | 인증번호 확인 | SCR-002, SCR-003 | —(Redis) | X |
| API-006 | `PATCH` | `/api/v1/auth/password` | 비밀번호 재설정 | SCR-003 | User | X |
| API-010 | `GET` | `/api/v1/users/check-email` | 이메일 중복 확인 | SCR-002 | User | X |
| API-011 | `GET` | `/api/v1/users/check-nickname` | 닉네임 중복 확인 | SCR-002 | User | X |
| API-012 | `POST` | `/api/v1/users` | 회원가입 (약관·주소·프로필 일괄) | SCR-002 | User, UserAgreement, ShippingAddress | X |
| API-013 | `GET` | `/api/v1/users/me/account` | 계정 정보 조회 (실명·휴대폰·이메일) | SCR-004 | User | O |
| API-014 | `GET` | `/api/v1/users/me` | 내 프로필 + 카운터 | SCR-005, SCR-020 | User, Item, Wishlist, Follow | O |
| API-015 | `PATCH` | `/api/v1/users/me` | 프로필 수정 (사진·닉네임·성별·키·몸무게) | SCR-005 | User | O |
| API-016 | `DELETE` | `/api/v1/users/me` | 회원탈퇴 (사유 수집 + 즉시 익명화) | SCR-009 | User, UserWithdrawal | O |
| API-017 | `GET` | `/api/v1/users/{userId}` | 타인 프로필 | SCR-012 | User, Follow, UserBlock, 최근 열람(Redis) | O |
| API-020 | `GET` | `/api/v1/shipping-addresses` | 배송지 목록 | SCR-006 | ShippingAddress | O |
| API-021 | `POST` | `/api/v1/shipping-addresses` | 배송지 추가 | SCR-006, SCR-002 | ShippingAddress | O |
| API-022 | `PUT` | `/api/v1/shipping-addresses/{addressId}` | 배송지 수정 | SCR-006 | ShippingAddress | O |
| API-023 | `DELETE` | `/api/v1/shipping-addresses/{addressId}` | 배송지 삭제 | SCR-006 | ShippingAddress | O |
| API-024 | `PATCH` | `/api/v1/shipping-addresses/{addressId}/default` | 기본 배송지 지정 | SCR-006 | ShippingAddress | O |
| API-030 | `GET` | `/api/v1/payout-accounts` | 정산 계좌 목록 | SCR-007 | PayoutAccount | O |
| API-031 | `POST` | `/api/v1/payout-accounts` | 정산 계좌 등록 | SCR-007 | PayoutAccount | O |
| API-032 | `PUT` | `/api/v1/payout-accounts/{accountId}` | 정산 계좌 수정 | SCR-007 | PayoutAccount | O |
| API-033 | `DELETE` | `/api/v1/payout-accounts/{accountId}` | 정산 계좌 삭제 | SCR-007 | PayoutAccount | O |
| API-040 | `GET` | `/api/v1/notification-settings` | 알림 수신 설정 조회 | SCR-008 | NotificationSetting | O |
| API-041 | `PATCH` | `/api/v1/notification-settings` | 알림 수신 설정 변경 | SCR-008 | NotificationSetting | O |
| API-050 | `GET` | `/api/v1/users/{userId}/followers` | 팔로워 목록 | SCR-011 | Follow, User | O |
| API-051 | `GET` | `/api/v1/users/{userId}/followings` | 팔로잉 목록 | SCR-011 | Follow, User | O |
| API-052 | `POST` | `/api/v1/users/{userId}/follow` | 팔로우 | SCR-011, SCR-012, SCR-015 | Follow | O |
| API-053 | `DELETE` | `/api/v1/users/{userId}/follow` | 언팔로우 | SCR-011, SCR-012 | Follow | O |
| API-054 | `POST` | `/api/v1/users/{userId}/block` | 사용자 차단 | SCR-013, SCR-019, SCR-026, SCR-043 | UserBlock | O |
| API-055 | `DELETE` | `/api/v1/users/{userId}/block` | 차단 해제 | SCR-013, SCR-043 | UserBlock | O |
| API-056 | `POST` | `/api/v1/reports` | 신고 (게시물·상품·사용자) | SCR-013, SCR-019, SCR-026, SCR-043 | Report | O |
| API-060 | `POST` | `/api/v1/images` | 이미지 업로드 → 저장소 키 반환 | SCR-017, SCR-018, SCR-024, SCR-042 | —(PostImage·ItemImage·ChatMessage 의 선행 단계) | O |
| API-070 | `GET` | `/api/v1/posts` | 홈 피드 (모두 / 팔로우 탭) | SCR-014 | Post, PostImage, Follow, UserBlock | O |
| API-071 | `GET` | `/api/v1/posts/{postId}` | OOTD 상세 | SCR-015 | Post, PostImage, PostItemTag, PostLike, PostSave, Follow, 최근 열람(Redis) | O |
| API-072 | `POST` | `/api/v1/posts` | OOTD 게시 | SCR-018 | Post, PostImage, PostItemTag | O |
| API-073 | `PATCH` | `/api/v1/posts/{postId}` | 게시물 수정 (본문·태그) | SCR-019 | Post, PostItemTag | O |
| API-074 | `DELETE` | `/api/v1/posts/{postId}` | 게시물 삭제 (soft) | SCR-019 | Post | O |
| API-075 | `POST` | `/api/v1/posts/{postId}/like` | 좋아요 | SCR-015 | PostLike | O |
| API-076 | `DELETE` | `/api/v1/posts/{postId}/like` | 좋아요 취소 | SCR-015 | PostLike | O |
| API-077 | `POST` | `/api/v1/posts/{postId}/save` | 저장 | SCR-015 | PostSave | O |
| API-078 | `DELETE` | `/api/v1/posts/{postId}/save` | 저장 취소 | SCR-015 | PostSave | O |
| API-079 | `GET` | `/api/v1/post-images/{imageId}/tagged-items` | 사진 속 태그된 아이템 목록 | SCR-016 | PostItemTag, Item, ItemListing | O |
| API-080 | `POST` | `/api/v1/posts/analyze` | AI 아이템 분석 (태그 후보 조회) | SCR-018 | — | O |
| API-081 | `GET` | `/api/v1/users/me/posts` | 내 게시물 (게시물 / 좋아요 / 저장 탭) | SCR-020 | Post, PostLike, PostSave | O |
| API-082 | `GET` | `/api/v1/users/{userId}/posts` | 타인 게시물 목록 | SCR-012 | Post | O |
| API-083 | `PATCH` | `/api/v1/posts/tags` | 태그 일괄 수정 (MVP 외) | SCR-021 | PostItemTag, PostTagEditLog | O |
| API-090 | `GET` | `/api/v1/categories` | 카테고리 트리 (대분류 10 · 세부 102) | SCR-024, SCR-047 | Category | O |
| API-091 | `GET` | `/api/v1/items` | 내가 등록한 아이템 목록 | SCR-022 | Item, ItemListing | O |
| API-092 | `POST` | `/api/v1/items` | 아이템 등록 | SCR-024, SCR-018 | Item, ItemImage, ItemMeasurement, ItemCondition, ItemOwnership | O |
| API-093 | `GET` | `/api/v1/items/{itemId}` | 아이템 상세 (소유자 시점) | SCR-023 | Item, ItemImage, ItemOwnership, ItemWearRecord, ItemMeasurement, ItemCondition, ItemListing | O |
| API-094 | `PATCH` | `/api/v1/items/{itemId}` | 아이템 상세 정보 수정 | SCR-024 | Item, ItemImage, ItemMeasurement, ItemCondition | O |
| API-095 | `DELETE` | `/api/v1/items/{itemId}` | 아이템 삭제 (soft) | SCR-022 | Item | O |
| API-096 | `GET` | `/api/v1/items/{itemId}/history` | 아이템 히스토리 (등장 OOTD 그리드) | SCR-025 | ItemWearRecord, Post, PostImage | O |
| API-097 | `GET` | `/api/v1/items/{itemId}/ownerships` | Tenure 기록 기준 (소유 구간 + 인증 횟수) | SCR-023, SCR-026 | ItemOwnership, ItemWearRecord | O |
| API-098 | `GET` | `/api/v1/items/me/taggable` | 태그 가능한 내 아이템 (본인 보유분) | SCR-018 | Item | O |
| API-110 | `GET` | `/api/v1/listings/{listingId}` | 상품 상세 (구매자 시점) | SCR-026, SCR-031 | ItemListing, Item, ItemOwnership, ItemWearRecord | O |
| API-111 | `POST` | `/api/v1/items/{itemId}/listings` | 판매 전환 | SCR-027 | ItemListing, ListingFeaturedPost, ItemMeasurement, ItemCondition, PayoutAccount | O |
| API-112 | `PATCH` | `/api/v1/listings/{listingId}` | 판매 정보 수정 (가격·배송비·수수료 부담) | SCR-027 | ItemListing | O |
| API-113 | `PATCH` | `/api/v1/listings/{listingId}/status` | 판매 상태 변경 (판매중 / 미판매 / 판매완료) | SCR-029 | ItemListing | O |
| API-114 | `GET` | `/api/v1/listings/{listingId}/featured-posts` | 대표 OOTD 조회 | SCR-028 | ListingFeaturedPost, Post | O |
| API-115 | `PUT` | `/api/v1/listings/{listingId}/featured-posts` | 대표 OOTD 교체 (1~5개) | SCR-028 | ListingFeaturedPost | O |
| API-120 | `GET` | `/api/v1/wishlists` | 위시리스트 | SCR-030 | Wishlist, Item, ItemListing | O |
| API-121 | `POST` | `/api/v1/items/{itemId}/wishlist` | 관심 등록 | SCR-026, SCR-030 | Wishlist | O |
| API-122 | `DELETE` | `/api/v1/items/{itemId}/wishlist` | 관심 해제 | SCR-026, SCR-030 | Wishlist | O |
| API-123 | `PATCH` | `/api/v1/wishlists/{wishlistId}/alarm` | 위시 알림 on/off | SCR-030 | Wishlist | O |
| API-140 | `GET` | `/api/v1/listings/{listingId}/offer-preview` | 결제 금액 미리보기 (수수료·배송비 계산) | SCR-032, SCR-033, SCR-034 | ItemListing, User | O |
| API-141 | `POST` | `/api/v1/listings/{listingId}/offers` | 구매 제안 / 거래 의사 전송 (결제 승인 포함) | SCR-032, SCR-033, SCR-034 | PurchaseOffer, Payment, ShippingAddress | O |
| API-142 | `GET` | `/api/v1/offers/{offerId}` | 제안서 상세 (구매자·판매자 시점 분기) | SCR-035, SCR-036 | PurchaseOffer, Payment, Settlement, PayoutAccount, ShippingAddress | O |
| API-143 | `DELETE` | `/api/v1/offers/{offerId}` | 제안 취소 (구매자) | SCR-035 | PurchaseOffer, Payment | O |
| API-144 | `POST` | `/api/v1/offers/{offerId}/accept` | 제안 수락 (판매자) → 거래 생성 | SCR-036 | PurchaseOffer, Payment, TradeOrder | O |
| API-145 | `POST` | `/api/v1/offers/{offerId}/reject` | 제안 거절 (판매자) | SCR-036 | PurchaseOffer, Payment | O |
| API-160 | `GET` | `/api/v1/orders/{orderId}` | 거래 상세 (타임라인·배송·결제·정산) | SCR-037 | TradeOrder, OrderStatusHistory, OrderShipment, Payment, Settlement | O |
| API-161 | `GET` | `/api/v1/orders/sales` | 판매 내역 4개 탭 (제안 + 거래 유니온) | SCR-039 | TradeOrder, PurchaseOffer, ItemListing | O |
| API-162 | `GET` | `/api/v1/orders/purchases` | 구매 내역 4개 탭 (제안 + 거래 유니온) | SCR-040 | TradeOrder, PurchaseOffer | O |
| API-163 | `PATCH` | `/api/v1/orders/{orderId}/shipment` | 운송장 번호 입력 → 상품 발송 | SCR-038 | OrderShipment, TradeOrder | O |
| API-164 | `POST` | `/api/v1/orders/{orderId}/deliver` | 배송 완료 처리 | SCR-037 | TradeOrder, OrderShipment | O |
| API-165 | `POST` | `/api/v1/orders/{orderId}/confirm` | 구매 확정 → 소유권 승계 | SCR-037 | TradeOrder, ItemOwnership, Settlement | O |
| API-166 | `POST` | `/api/v1/orders/{orderId}/cancel` | 거래 취소 (상품 발송 전) → 환불 | SCR-037 | TradeOrder, Payment | O |
| API-180 | `GET` | `/api/v1/chat-rooms` | 채팅방 목록 (전체/구매/판매/읽지않음) | SCR-041 | ChatRoom, ChatRoomMember, ChatMessage | O |
| API-181 | `POST` | `/api/v1/items/{itemId}/chat-rooms` | 채팅방 개설 | SCR-026 | ChatRoom, ChatRoomMember | O |
| API-182 | `GET` | `/api/v1/chat-rooms/{roomId}` | 채팅방 헤더 (아이템·거래 단계 CTA) | SCR-042 | ChatRoom, Item, ItemListing | O |
| API-183 | `GET` | `/api/v1/chat-rooms/{roomId}/messages` | 메시지 목록 (커서 페이징) | SCR-042 | ChatMessage | O |
| API-184 | `POST` | `/api/v1/chat-rooms/{roomId}/messages` | 메시지 전송 (텍스트 / 이미지) | SCR-042 | ChatMessage, ChatRoom | O |
| API-185 | `PATCH` | `/api/v1/chat-rooms/{roomId}/read` | 읽음 처리 | SCR-042 | ChatRoomMember | O |
| API-186 | `DELETE` | `/api/v1/chat-rooms/{roomId}/me` | 채팅방 나가기 | SCR-043 | ChatRoomMember | O |
| API-190 | `GET` | `/api/v1/notifications` | 알림 목록 (탭 5종) | SCR-044 | Notification | O |
| API-191 | `PATCH` | `/api/v1/notifications/{notificationId}/read` | 알림 읽음 | SCR-044 | Notification | O |
| API-192 | `PATCH` | `/api/v1/notifications/read-all` | 전체 읽음 | SCR-044 | Notification | O |
| API-200 | `GET` | `/api/v1/search/posts` | 게시물 검색 (필터 적용) | SCR-046, SCR-047 | Post, Item, ItemListing, Category, User, SearchHistory | O |
| API-201 | `GET` | `/api/v1/search/users` | 계정 검색 | SCR-046 | User, SearchHistory | O |
| API-202 | `GET` | `/api/v1/search/recommendations` | 추천 섹션 (유사·인기·최신·인기 사용자) | SCR-045 | Post, User, 최근 열람(Redis) | O |
| API-203 | `GET` | `/api/v1/search/histories` | 최근 검색어 + 최근 본 사용자 | SCR-045 | SearchHistory, 최근 열람(Redis) | O |
| API-204 | `DELETE` | `/api/v1/search/histories/{historyId}` | 최근 검색어 개별 삭제 | SCR-045 | SearchHistory | O |
| API-205 | `DELETE` | `/api/v1/search/histories` | 최근 검색어 전체 삭제 | SCR-045 | SearchHistory | O |
---

## 3. CRUD 커버리지

없는 동작은 **이유**를 적는다. 화면에 없는 기능을 기계적으로 만들지 않았다.

이메일 인증번호·토큰 · refresh token · 최근 열람 이력 · 공휴일 캐시는 **Redis** 에 있어 이 표에 없다(`DEC-009`·`DEC-010`·`DEC-014`·`DEC-003`).

| 엔티티 | 목록 | 상세 | 생성 | 수정 | 삭제 |
|---|---|---|---|---|---|
| `user` | API-017 (타인) | API-013 API-014 API-017 | API-012 (가입) | API-015 | API-016 (탈퇴=익명화) |
| `user_grade_history` | 없음(화면 없음) | 없음 | 없음(승격 판정이 자동 생성) | 없음(append-only) | 없음(append-only) |
| `user_agreement` | 없음(단독 조회 화면 없음) | 없음 | API-012 에 포함 | 없음(append-only) | 없음(append-only) |
| `user_withdrawal` | 없음(운영 도구 범위 밖) | 없음 | API-016 에 포함 | 없음(append-only) | 없음(append-only) |
| `follow` | API-050 API-051 | 없음(목록으로 충분) | API-052 | 없음(수정 개념 없음) | API-053 |
| `user_block` | 없음(목록 화면 없음) | 없음 | API-054 | 없음 | API-055 |
| `report` | 없음(관리자 기능 범위 밖) | 없음 | API-056 | 없음(append-only) | 없음(append-only) |
| `shipping_address` | API-020 | 없음(목록에 전체 필드) | API-021 | API-022 API-024 | API-023 |
| `payout_account` | API-030 | 없음(목록에 전체 필드) | API-031 | API-032 | API-033 |
| `notification_setting` | API-040 | 없음 | 없음(가입 시 기본값 생성) | API-041 | 없음(계정 종속) |
| `post` | API-070 API-081 API-082 | API-071 | API-072 | API-073 | API-074 |
| `post_image` | 없음(게시물에 중첩) | 없음 | API-072 에 포함 | API-073 에 포함 | 없음(부모 종속) |
| `post_item_tag` | API-079 | 없음 | API-072 에 포함 | API-073 API-083 | 없음(태그 교체로 대체) |
| `post_like` | API-081 (좋아요 탭) | 없음 | API-075 | 없음 | API-076 |
| `post_save` | API-081 (저장 탭) | 없음 | API-077 | 없음 | API-078 |
| `post_tag_edit_log` | 없음(MVP 외) | 없음 | API-083 이 자동 생성 | 없음(append-only) | 없음(append-only) |
| `category` | API-090 | 없음(트리로 일괄) | 없음(마스터 데이터) | 없음(마스터 데이터) | 없음(마스터 데이터) |
| `item` | API-091 API-098 | API-093 | API-092 | API-094 | API-095 |
| `item_image` | 없음(아이템에 중첩) | API-093 에 포함 | API-092 에 포함 | API-094 에 포함 (전체 교체) | 없음(교체로 대체) |
| `item_measurement` | 없음(아이템에 중첩) | 없음 | API-092 에 포함 | API-094 에 포함 | 없음(부모 종속) |
| `item_condition` | 없음(아이템에 중첩) | 없음 | API-092 에 포함 | API-094 에 포함 | 없음(부모 종속) |
| `item_ownership` | API-097 | 없음 | API-092 API-165 가 자동 생성 | 없음(append-only) | 없음(append-only) |
| `item_wear_record` | API-096 | 없음 | API-072 가 자동 적립 | 없음(voided_at 무효화만) | 없음(append-only) |
| `item_listing` | API-091 에 포함 | API-110 API-093 에 포함 | API-111 | API-112 API-113 | 없음(상태 변경으로 대체) |
| `listing_featured_post` | API-114 | 없음 | API-111 에 포함 | API-115 | 없음(교체로 대체) |
| `wishlist` | API-120 | 없음 | API-121 | API-123 (알림) | API-122 |
| `purchase_offer` | API-161 API-162 (유니온 행) | API-142 | API-141 | API-144 API-145 | API-143 (취소=상태 전이) |
| `payment` | 없음(제안·거래에 중첩) | API-142 API-160 에 포함 | API-141 에 포함 | 없음(상태는 거래가 전이) | 없음(append-only) |
| `trade_order` | API-161 API-162 | API-160 | API-144 가 자동 생성 | API-163~166 | 없음(append-only) |
| `order_shipment` | 없음(거래에 중첩) | API-160 에 포함 | API-144 가 자동 생성 | API-163 API-164 | 없음(append-only) |
| `order_status_history` | API-160 에 포함 | 없음 | 상태 전이가 자동 생성 | 없음(append-only) | 없음(append-only) |
| `settlement` | 없음(거래에 중첩) | API-160 에 포함 | API-165 가 자동 생성 | 없음(배치가 정산) | 없음(append-only) |
| `chat_room` | API-180 | API-182 | API-181 | 없음(상태 파생) | 없음(나가기로 대체) |
| `chat_room_member` | 없음(방에 중첩) | 없음 | API-181 이 자동 생성 | API-185 (읽음) | API-186 (나가기) |
| `chat_message` | API-183 | 없음 | API-184 | 없음(수정 화면 없음) | 없음(삭제 화면 없음) |
| `notification` | API-190 | 없음(목록에 전체 필드) | 없음(시스템 발행) | API-191 API-192 | 없음(보관 정책 없음) |
| `search_history` | API-203 | 없음 | API-200 API-201 이 부수효과로 기록 | 없음 | API-204 API-205 |

> `user_grade_history` 는 어떤 응답에도 나오지 않는다. 레코드 등급 승격·강등의 **판정 근거를 남기는 감사 테이블**이며
> 화면에 노출되지 않는다. 과잉설계가 아니라 **레코드 승격 정책**(직전 30일 유효 OOTD 20개, 게시일 기준)의 재현성을 위한 것이다.

---

## 4. 화면 ↔ API 매핑

47개 화면 전부가 아래 표에 있다. 빈 칸이 있으면 누락이다.

| 화면 | 이름 | 액션 | 인증 | 호출 API |
|---|---|---|:--:|---|
| SCR-001 | 온보딩 / 로그인 | read | X | API-001 |
| SCR-002 | 회원가입 | create | X | API-004 API-005 API-010 API-011 API-012 API-021 |
| SCR-003 | 비밀번호 찾기 | update | X | API-004 API-005 API-006 |
| SCR-004 | 계정 정보 (로그인 정보) | read | O | API-013 |
| SCR-005 | 프로필 수정 | read, update | O | API-014 API-015 |
| SCR-006 | 배송지 관리 | read, create, update, delete | O | API-020 API-021 API-022 API-023 API-024 |
| SCR-007 | 정산 계좌 관리 | read, create, update, delete | O | API-030 API-031 API-032 API-033 |
| SCR-008 | 알림 설정 | read, update | O | API-040 API-041 |
| SCR-009 | 회원탈퇴 | delete | O | API-016 |
| SCR-010 | 로그아웃 | — | O | API-002 |
| SCR-011 | 팔로워 · 팔로잉 목록 | read, create, delete | O | API-050 API-051 API-052 API-053 |
| SCR-012 | 사용자 프로필 (타인) | read | O | API-017 API-052 API-053 API-082 |
| SCR-013 | 차단 · 신고 | create, delete | O | API-054 API-055 API-056 |
| SCR-014 | 홈 피드 | read, filter, paging | O | API-070 API-075 API-076 API-077 API-078 |
| SCR-015 | OOTD 상세 | read, create, delete | O | API-052 API-071 API-075 API-076 API-077 API-078 |
| SCR-016 | 태그된 아이템 목록 | read | O | API-079 |
| SCR-017 | OOTD 촬영 | create | O | API-060 (촬영 결과 업로드 → API-072 로 이어진다) |
| SCR-018 | OOTD 게시물 작성 | create | O | API-060 API-072 API-080 API-092 API-098 |
| SCR-019 | 게시물 더보기 (수정 · 삭제 · 차단 · 신고) | update, delete, create | O | API-054 API-056 API-073 API-074 |
| SCR-020 | 마이페이지 | read, filter, paging | O | API-014 API-081 API-091 API-120 API-161 API-162 |
| SCR-021 | 태그 일괄 수정 (MVP 외) | update | O | API-083 |
| SCR-022 | 등록한 아이템 목록 | read, create, paging | O | API-091 API-095 |
| SCR-023 | 등록한 아이템 상세 (소유자 시점) | read, update | O | API-093 API-096 API-097 API-113 |
| SCR-024 | 아이템 등록 · 상세 정보 수정 | create, update | O | API-060 API-090 API-092 API-094 |
| SCR-025 | 아이템 히스토리 | read, paging | O | API-096 |
| SCR-026 | 상품 상세페이지 (구매자 시점) | read, create, delete | O | API-054 API-056 API-097 API-110 API-121 API-122 API-181 |
| SCR-027 | 판매 전환 | create, update | O | API-030 API-111 API-112 API-114 |
| SCR-028 | 대표 OOTD 선택 | read, create, delete | O | API-114 API-115 |
| SCR-029 | 판매 상태 변경 | update | O | API-113 |
| SCR-030 | 위시리스트 | read, create, delete, update, paging | O | API-120 API-121 API-122 API-123 |
| SCR-031 | 공유하기 | read | O | API-110 |
| SCR-032 | 구매 제안 (미판매 상품) | create | O | API-140 API-141 |
| SCR-033 | 거래 의사 (판매 중 상품) | create | O | API-140 API-141 |
| SCR-034 | 결제 | create | O | API-140 API-141 |
| SCR-035 | 구매 제안서 (구매자 시점) | read, delete | O | API-142 API-143 |
| SCR-036 | 구매 제안서 (판매자 시점) | read, update | O | API-030 API-142 API-144 API-145 |
| SCR-037 | 거래 상세 | read, update | O | API-160 API-164 API-165 API-166 |
| SCR-038 | 운송장 번호 입력 | update | O | API-163 |
| SCR-039 | 판매 내역 | read, filter, paging | O | API-161 |
| SCR-040 | 구매 내역 | read, filter, paging | O | API-162 |
| SCR-041 | 채팅방 목록 | read, filter, paging | O | API-180 |
| SCR-042 | 채팅방 | read, create, paging | O | API-060 API-182 API-183 API-184 API-185 |
| SCR-043 | 채팅방 관리 (차단 · 나가기 · 신고) | update, delete, create | O | API-054 API-055 API-056 API-186 |
| SCR-044 | 알림 | read, filter, paging | O | API-190 API-191 API-192 |
| SCR-045 | 검색 | read, create, delete, search | O | API-200 API-201 API-202 API-203 API-204 API-205 |
| SCR-046 | 검색 결과 | read, search, filter, paging | O | API-200 API-201 |
| SCR-047 | 검색 필터 | filter | O | API-090 API-200 API-201 |

> `SCR-010 로그아웃`·`SCR-031 공유하기`·`SCR-047 검색 필터`는 저장 대상이 없거나 다른 화면의 파라미터로 흡수된다.
> 각각 `API-002`, `API-110`, `API-090`+`API-200` 이 대응한다.
---

## 5. ErrorCode 목록

메시지는 사용자에게 그대로 보여줄 수 있는 문장이다.

**ErrorCode 는 도메인 패키지당 최대 1개다.** 어느 패키지에 놓이는지를 여기서 못박는다 —
`domain/<도메인>/exception/{도메인}ErrorCode`.

| 도메인 패키지 | ErrorCode enum |
|---|---|
| `user` | `UserErrorCode` |
| `auth` | `AuthErrorCode` |
| `follow` | `FollowErrorCode` |
| `block` | `BlockErrorCode` |
| `report` | `ReportErrorCode` |
| `address` | `AddressErrorCode` |
| `payout` | `PayoutErrorCode` |
| `post` | `PostErrorCode` |
| `item` | `ItemErrorCode` |
| `category` | `CategoryErrorCode` |
| `listing` | `ListingErrorCode` |
| `wishlist` | `WishlistErrorCode` |
| `offer` | `OfferErrorCode` |
| `payment` | `PaymentErrorCode` |
| `order` | `OrderErrorCode` |
| `settlement` | — |
| `chat` | `ChatErrorCode` |
| `notification` | `NotificationErrorCode` |
| `search` | `SearchErrorCode` |
| **`global`** | `CommonErrorCode` · `MediaErrorCode` |

**enum 이름은 코드 접두사와 일치시킨다. 예외는 없다.**
`settlement` 는 사용자 조작이 개입하지 않아(배치가 계산한다) 전용 에러 코드가 없다.

### CommonErrorCode

| 코드 | HTTP | 메시지 | 발생 API |
|---|:--:|---|---|
| `COMMON_400` | 400 | 잘못된 요청입니다. | 전체 |
| `COMMON_401` | 401 | 인증이 필요합니다. | 인증 필요 전체 |
| `COMMON_403` | 403 | 접근 권한이 없습니다. | 소유자 검증 대상 전체 |
| `COMMON_404` | 404 | 요청한 리소스를 찾을 수 없습니다. | 전체 |
| `COMMON_500` | 500 | 서버 오류가 발생했습니다. | 전체 |

### AuthErrorCode

| 코드 | HTTP | 메시지 | 발생 API |
|---|:--:|---|---|
| `AUTH_401_CREDENTIAL` | 401 | 이메일 또는 비밀번호가 올바르지 않습니다. | API-001 |
| `AUTH_404_EMAIL` | 404 | 가입되지 않은 이메일입니다. | API-001, API-004 |
| `AUTH_401_TOKEN` | 401 | 유효하지 않은 토큰입니다. | API-003 |
| `AUTH_400_CODE` | 400 | 인증번호가 틀렸습니다. | API-005 |
| `AUTH_410_CODE` | 410 | 인증번호가 만료되었습니다. | API-005 |
| `AUTH_400_NOT_VERIFIED` | 400 | 이메일 인증이 완료되지 않았습니다. | API-012, API-006 |
| `AUTH_401_VERIFICATION_TOKEN` | 401 | 유효하지 않은 인증 토큰입니다. | API-012, API-006 |
| `AUTH_410_VERIFICATION_TOKEN` | 410 | 인증 토큰이 만료되었습니다. | API-012, API-006 |
| `AUTH_409_TOKEN_CONSUMED` | 409 | 이미 사용된 인증 토큰입니다. | API-012, API-006 |
| `AUTH_400_PURPOSE_MISMATCH` | 400 | 용도가 맞지 않는 인증 토큰입니다. | API-012, API-006 |

### UserErrorCode

| 코드 | HTTP | 메시지 | 발생 API |
|---|:--:|---|---|
| `USER_404` | 404 | 사용자를 찾을 수 없습니다. | API-017, API-050~055 |
| `USER_409_EMAIL` | 409 | 이미 존재하는 이메일입니다. | API-010, API-012 |
| `USER_409_NICKNAME` | 409 | 중복된 닉네임입니다. | API-011, API-012, API-015 |
| `USER_400_PASSWORD` | 400 | 비밀번호는 8자 이상이어야 합니다. | API-012, API-006 |
| `USER_400_AGREEMENT` | 400 | 필수 약관에 모두 동의해야 합니다. | API-012 |
| `USER_409_WITHDRAWN` | 409 | 이미 탈퇴한 계정입니다. | API-016 |
| `USER_409_ACTIVE_TRADE` | 409 | 진행 중인 거래가 있어 탈퇴할 수 없습니다. | API-016 |

### AddressErrorCode

| 코드 | HTTP | 메시지 | 발생 API |
|---|:--:|---|---|
| `ADDRESS_404` | 404 | 배송지를 찾을 수 없습니다. | API-022~024 |
| `ADDRESS_403` | 403 | 본인의 배송지가 아닙니다. | API-022~024 |
| `ADDRESS_409_LAST_DEFAULT` | 409 | 기본 배송지는 삭제할 수 없습니다. | API-023 |

### PayoutErrorCode

| 코드 | HTTP | 메시지 | 발생 API |
|---|:--:|---|---|
| `PAYOUT_404` | 404 | 정산 계좌를 찾을 수 없습니다. | API-032, API-033 |
| `PAYOUT_403` | 403 | 본인의 정산 계좌가 아닙니다. | API-032, API-033 |
| `PAYOUT_409_PENDING_SETTLEMENT` | 409 | 정산 대기 중인 거래가 있어 삭제할 수 없습니다. | API-033 |
| `PAYOUT_400_REQUIRED` | 400 | 판매 전환을 위해 정산 계좌를 먼저 등록해주세요. | API-111 |

### FollowErrorCode

| 코드 | HTTP | 메시지 | 발생 API |
|---|:--:|---|---|
| `FOLLOW_409` | 409 | 이미 팔로우한 사용자입니다. | API-052 |
| `FOLLOW_400_SELF` | 400 | 자기 자신을 팔로우할 수 없습니다. | API-052 |

### BlockErrorCode

| 코드 | HTTP | 메시지 | 발생 API |
|---|:--:|---|---|
| `BLOCK_409` | 409 | 이미 차단한 사용자입니다. | API-054 |
| `BLOCK_400_SELF` | 400 | 자기 자신을 차단할 수 없습니다. | API-054 |

### ReportErrorCode

| 코드 | HTTP | 메시지 | 발생 API |
|---|:--:|---|---|
| `REPORT_404_TARGET` | 404 | 신고 대상을 찾을 수 없습니다. | API-056 |

### PostErrorCode

| 코드 | HTTP | 메시지 | 발생 API |
|---|:--:|---|---|
| `POST_404` | 404 | 게시물을 찾을 수 없습니다. | API-071~078 |
| `POST_404_IMAGE` | 404 | 사진을 찾을 수 없습니다. | API-079 |
| `POST_403` | 403 | 본인의 게시물이 아닙니다. | API-073, API-074 |
| `POST_400_TAG_LIMIT` | 400 | 태그는 사진당 최대 5개까지 가능합니다. | API-072, API-073 |
| `POST_400_TAG_OWNERSHIP` | 400 | 본인이 보유한 아이템만 태그할 수 있습니다. | API-072, API-073 |
| `POST_400_IMAGE_REQUIRED` | 400 | 사진을 1장 이상 등록해주세요. | API-072 |
| `POST_409_FEATURED` | 409 | 판매 중인 상품의 대표 OOTD 는 삭제할 수 없습니다. | API-074 |
| `POST_400_EDIT_WINDOW` | 400 | 바로 게시한 지 3일이 지난 게시물은 태그를 수정할 수 없습니다. | API-083 |
| `POST_409_LIKE` | 409 | 이미 좋아요한 게시물입니다. | API-075 |
| `POST_409_SAVE` | 409 | 이미 저장한 게시물입니다. | API-077 |

### ItemErrorCode

| 코드 | HTTP | 메시지 | 발생 API |
|---|:--:|---|---|
| `ITEM_404` | 404 | 아이템을 찾을 수 없습니다. | API-093~097 |
| `ITEM_403` | 403 | 본인의 아이템이 아닙니다. | API-094, API-095, API-111 |
| `ITEM_409_ON_SALE` | 409 | 판매 중이거나 거래가 진행 중이라 삭제할 수 없습니다. | API-095 |
| `ITEM_400_MEASUREMENT` | 400 | 해당 카테고리에 맞지 않는 실측 항목입니다. | API-092, API-094 |
| `ITEM_400_CATEGORY` | 400 | 세부 카테고리를 선택해주세요. | API-092, API-094 |
| `ITEM_409_TAG_CLOSED_PERIOD` | 409 | 이미 판매된 아이템의 과거 태그는 수정할 수 없습니다. | API-073, API-083 |

### CategoryErrorCode

| 코드 | HTTP | 메시지 | 발생 API |
|---|:--:|---|---|
| `CATEGORY_404` | 404 | 카테고리를 찾을 수 없습니다. | API-092, API-094 |

### ListingErrorCode

| 코드 | HTTP | 메시지 | 발생 API |
|---|:--:|---|---|
| `LISTING_404` | 404 | 판매 상품을 찾을 수 없습니다. | API-110~115, API-141 |
| `LISTING_403` | 403 | 본인의 판매 상품이 아닙니다. | API-112, API-113, API-115 |
| `LISTING_400_FEATURED_RANGE` | 400 | 대표 OOTD 는 1개 이상 5개 이하로 선택해주세요. | API-111, API-115 |
| `LISTING_403_FEE_BEARER` | 403 | 기본 사용자는 수수료 부담과 배송비를 지정할 수 없습니다. | API-111, API-112 |
| `LISTING_409_SOLD` | 409 | 이미 판매 완료된 상품입니다. | API-112, API-113, API-141 |

### WishlistErrorCode

| 코드 | HTTP | 메시지 | 발생 API |
|---|:--:|---|---|
| `WISHLIST_409` | 409 | 이미 관심 등록한 아이템입니다. | API-121 |
| `WISHLIST_404` | 404 | 위시리스트 항목을 찾을 수 없습니다. | API-122, API-123 |

### OfferErrorCode

| 코드 | HTTP | 메시지 | 발생 API |
|---|:--:|---|---|
| `OFFER_404` | 404 | 제안을 찾을 수 없습니다. | API-142~145 |
| `OFFER_403` | 403 | 본인과 관련된 제안이 아닙니다. | API-142~145 |
| `OFFER_409_ALREADY_USED` | 409 | 이 아이템에는 이미 구매 의사를 보냈습니다. | API-141 |
| `OFFER_400_NOT_ALLOWED` | 400 | 구매 제안을 받지 않는 상품입니다. | API-141 |
| `OFFER_403_BLOCKED` | 403 | 차단 관계인 사용자에게는 제안할 수 없습니다. | API-141 |
| `OFFER_400_SELF` | 400 | 본인의 상품에는 제안할 수 없습니다. | API-141 |
| `OFFER_409_NOT_PENDING` | 409 | 이미 응답했거나 만료된 제안입니다. | API-143, API-144, API-145 |
| `OFFER_410_EXPIRED` | 410 | 제안 유효 시간이 지났습니다. | API-143, API-144 |
| `OFFER_400_ADDRESS_REQUIRED` | 400 | 배송지를 먼저 등록해주세요. | API-141 |

### PaymentErrorCode

| 코드 | HTTP | 메시지 | 발생 API |
|---|:--:|---|---|
| `PAYMENT_402` | 402 | 결제 승인에 실패했습니다. | API-141 |
| `PAYMENT_409_CAPTURE` | 409 | 결제 확정에 실패했습니다. | API-144 |
| `PAYMENT_409_REFUND` | 409 | 환불 처리에 실패했습니다. | API-166 |

### OrderErrorCode

| 코드 | HTTP | 메시지 | 발생 API |
|---|:--:|---|---|
| `ORDER_404` | 404 | 거래를 찾을 수 없습니다. | API-160, API-163~166 |
| `ORDER_403` | 403 | 본인과 관련된 거래가 아닙니다. | API-160, API-163~166 |
| `ORDER_409_STATUS` | 409 | 현재 단계에서 수행할 수 없는 요청입니다. | API-163~166 |
| `ORDER_409_CANCEL_WINDOW` | 409 | 상품 발송 이후에는 거래를 취소할 수 없습니다. | API-166 |
| `ORDER_400_TRACKING` | 400 | 운송장 번호 형식이 올바르지 않습니다. | API-163 |
| `ORDER_404_TRACKING` | 404 | 주문 정보를 찾을 수 없어요. | API-163 |

### ChatErrorCode

| 코드 | HTTP | 메시지 | 발생 API |
|---|:--:|---|---|
| `CHAT_404_ROOM` | 404 | 채팅방을 찾을 수 없습니다. | API-182~186 |
| `CHAT_403` | 403 | 참여 중인 채팅방이 아닙니다. | API-182~186 |
| `CHAT_400_NOT_OPENABLE` | 400 | 구매 제안을 받지 않는 미판매 상품은 채팅을 시작할 수 없습니다. | API-181 |
| `CHAT_403_BLOCKED` | 403 | 차단 관계라 메시지를 보낼 수 없습니다. | API-184 |
| `CHAT_400_SELF` | 400 | 본인의 상품에는 채팅방을 열 수 없습니다. | API-181 |

### NotificationErrorCode

| 코드 | HTTP | 메시지 | 발생 API |
|---|:--:|---|---|
| `NOTIFICATION_404` | 404 | 알림을 찾을 수 없습니다. | API-191 |
| `NOTIFICATION_403` | 403 | 본인의 알림이 아닙니다. | API-191 |

### MediaErrorCode

| 코드 | HTTP | 메시지 | 발생 API |
|---|:--:|---|---|
| `MEDIA_400_TYPE` | 400 | 지원하지 않는 이미지 형식입니다. | API-060 |
| `MEDIA_413` | 413 | 이미지 용량이 너무 큽니다. | API-060 |
| `MEDIA_500_STORAGE` | 500 | 이미지 저장에 실패했습니다. | API-060 |

### SearchErrorCode

| 코드 | HTTP | 메시지 | 발생 API |
|---|:--:|---|---|
| `SEARCH_404_HISTORY` | 404 | 검색 기록을 찾을 수 없습니다. | API-204 |

> 총 **82개** · 16개 도메인. 공통(인증·검증·서버)은 `CommonErrorCode` 로 분리한다.

---

## 6. 엔드포인트 상세

공통 사항은 §1 에 있다. 요청·응답 필드는 **그 화면이 실제로 보여주는 것**만 담는다 —
목록과 상세가 같은 스키마를 억지로 공유하지 않는다.

> JSON 예시의 값은 형식을 보이기 위한 것이다. 금액은 문자열, 날짜는 ISO 8601 이다.

### 인증

#### API-001 · `POST /api/v1/auth/login`

이메일·비밀번호 로그인. 인증 **불필요**. 관련 화면 SCR-001.

요청 `{ email, password, keepLoggedIn }` → 응답 `{ accessToken, refreshToken, user: { id, nickname, profileImageUrl, grade } }`. `keepLoggedIn` 은 refresh token 수명을 늘린다(`로그인 상태 유지`).

**요청 본문**

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|:--:|---|---|
| `email` | String | O | 이메일 형식 | 로그인 이메일 |
| `password` | String | O | 8자 이상 | 비밀번호 |
| `keepLoggedIn` | Boolean |  | 기본 false | refresh token TTL 연장 |

**응답** (`data`)

| 필드 | 타입 | 설명 |
|---|---|---|
| `accessToken` | String | 액세스 토큰 |
| `refreshToken` | String | 리프레시 토큰 (Redis 보관) |
| `user.id` | Long | 사용자 id |
| `user.nickname` | String | 닉네임 |
| `user.profileImageUrl` | String | 프로필 URL |
| `user.grade` | UserGrade | BASIC \| RECORD |

```json
{
  "success": true,
  "code": "COMMON_200",
  "message": "요청에 성공했습니다.",
  "data": {
    "accessToken": "<String>",
    "refreshToken": "<String>",
    "user": {
      "id": 1,
      "nickname": "<String>",
      "profileImageUrl": "https://cdn.tenure.app/post/2026/06/ab12.jpg",
      "grade": "<UserGrade>"
    }
  }
}
```

에러 — AUTH_401_CREDENTIAL, AUTH_404_EMAIL

#### API-002 · `POST /api/v1/auth/logout`

로그아웃 (refresh token 무효화). 인증 **필요**. 관련 화면 SCR-010.

refresh token 을 무효화한다. 본문 없음.

**응답** `data` 는 `null` 이다.

에러 — COMMON_401

#### API-003 · `POST /api/v1/auth/reissue`

access token 재발급. 인증 **불필요**. 관련 화면 —(화면 근거 없음).

요청 `{ refreshToken }` → 새 access token. 화면 근거가 없는 인프라 엔드포인트다.

**요청 본문**

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|:--:|---|---|
| `refreshToken` | String | O | — | 재발급용 토큰 |

**응답** (`data`)

| 필드 | 타입 | 설명 |
|---|---|---|
| `accessToken` | String | 새 액세스 토큰 |

```json
{
  "success": true,
  "code": "COMMON_200",
  "message": "요청에 성공했습니다.",
  "data": {
    "accessToken": "<String>"
  }
}
```

에러 — AUTH_401_TOKEN

#### API-004 · `POST /api/v1/auth/email-verifications`

인증번호 발송 (가입·비밀번호 찾기). 인증 **불필요**. 관련 화면 SCR-002, SCR-003.

요청 `{ email, purpose: SIGN_UP|PASSWORD_RESET }`. 기존 미인증 코드는 무효화하고 새로 발급한다.

**요청 본문**

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|:--:|---|---|
| `email` | String | O | 이메일 형식 | — |
| `purpose` | VerificationPurpose | O | SIGN_UP \| PASSWORD_RESET | — |

**응답** (`data`)

| 필드 | 타입 | 설명 |
|---|---|---|
| `expiresAt` | LocalDateTime | 인증번호 만료 (발급 +3분) |

```json
{
  "success": true,
  "code": "COMMON_200",
  "message": "요청에 성공했습니다.",
  "data": {
    "expiresAt": "2026-06-21T18:30:00"
  }
}
```

에러 — AUTH_404_EMAIL(PASSWORD_RESET 인데 미가입), USER_409_EMAIL(SIGN_UP 인데 기존 가입)

#### API-005 · `POST /api/v1/auth/email-verifications/verify`

인증번호 확인. 인증 **불필요**. 관련 화면 SCR-002, SCR-003.

요청 `{ email, purpose, code }` → `{ verificationToken, expiresAt }`. 인증 완료를 증명하는 **1회용 토큰**으로 수명은 **10분**이다(가입 플로우를 마칠 여유). `purpose` 가 토큰에 바인딩되어 `SIGN_UP` 토큰으로 `PASSWORD_RESET` 을 수행할 수 없다. 토큰은 Redis 키 `email-verify-token:{token}` 에 있고, 소비할 때 `GETDEL` 로 읽으면서 지워 **재사용이 구조적으로 불가능**하다(`DEC-009`).

**요청 본문**

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|:--:|---|---|
| `email` | String | O | — | — |
| `purpose` | VerificationPurpose | O | SIGN_UP \| PASSWORD_RESET | — |
| `code` | String | O | — | 인증번호 |

**응답** (`data`)

| 필드 | 타입 | 설명 |
|---|---|---|
| `verificationToken` | String | 1회용 토큰 |
| `expiresAt` | LocalDateTime | 토큰 만료 (발급 +10분) |

```json
{
  "success": true,
  "code": "COMMON_200",
  "message": "요청에 성공했습니다.",
  "data": {
    "verificationToken": "<String>",
    "expiresAt": "2026-06-21T18:30:00"
  }
}
```

에러 — AUTH_400_CODE, AUTH_410_CODE

#### API-006 · `PATCH /api/v1/auth/password`

비밀번호 재설정. 인증 **불필요**. 관련 화면 SCR-003.

요청 `{ email, verificationToken, newPassword }`. 토큰은 `purpose=PASSWORD_RESET` 이어야 한다. `GETDEL` 로 읽는 즉시 키가 사라지므로 같은 토큰을 두 번 쓸 수 없다.

**요청 본문**

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|:--:|---|---|
| `email` | String | O | — | — |
| `verificationToken` | String | O | purpose=PASSWORD_RESET | — |
| `newPassword` | String | O | 8자 이상 | — |

**응답** `data` 는 `null` 이다.

에러 — AUTH_400_NOT_VERIFIED, AUTH_401_VERIFICATION_TOKEN, AUTH_410_VERIFICATION_TOKEN, AUTH_409_TOKEN_CONSUMED, AUTH_400_PURPOSE_MISMATCH, USER_400_PASSWORD

### 사용자

#### API-010 · `GET /api/v1/users/check-email`

이메일 중복 확인. 인증 **불필요**. 관련 화면 SCR-002.

쿼리 `email`. 응답 `{ available: true }`.

**쿼리 파라미터**

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|:--:|---|---|
| `email` | String | O | — | 중복 확인할 이메일 |

**응답** (`data`)

| 필드 | 타입 | 설명 |
|---|---|---|
| `available` | Boolean | 사용 가능 여부 |

```json
{
  "success": true,
  "code": "COMMON_200",
  "message": "요청에 성공했습니다.",
  "data": {
    "available": true
  }
}
```

에러 — `COMMON_400`(파라미터 누락). 중복 여부는 `available` 로만 응답하며 예외를 던지지 않는다

#### API-011 · `GET /api/v1/users/check-nickname`

닉네임 중복 확인. 인증 **불필요**. 관련 화면 SCR-002.

쿼리 `nickname`. 응답 `{ available: true }`.

**쿼리 파라미터**

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|:--:|---|---|
| `nickname` | String | O | — | 중복 확인할 닉네임 |

**응답** (`data`)

| 필드 | 타입 | 설명 |
|---|---|---|
| `available` | Boolean | 사용 가능 여부 |

```json
{
  "success": true,
  "code": "COMMON_200",
  "message": "요청에 성공했습니다.",
  "data": {
    "available": true
  }
}
```

에러 — `COMMON_400`(파라미터 누락). 중복 여부는 `available` 로만 응답하며 예외를 던지지 않는다

#### API-012 · `POST /api/v1/users`

회원가입 (약관·주소·프로필 일괄). 인증 **불필요**. 관련 화면 SCR-002.

요청 `UserCreateRequest` — `email` `password` `verificationToken` `realName` `phone` `nickname` `profileImageKey?` `gender?` `heightCm?` `weightKg?` `agreements[]` `address`. 약관 3종 동의와 주소 1건을 함께 받아 화면 흐름(가입→약관→주소→프로필)을 한 번에 마감한다.

**요청 본문**

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|:--:|---|---|
| `email` | String | O | UNIQUE | — |
| `password` | String | O | 8자 이상 | — |
| `verificationToken` | String | O | purpose=SIGN_UP | — |
| `realName` | String | O | — | 실명 |
| `phone` | String | O | — | 휴대폰번호 |
| `nickname` | String | O | UNIQUE | — |
| `profileImageKey` | String |  | — | 미지정 시 기본 이미지 |
| `gender` | Gender |  | MALE \| FEMALE | — |
| `heightCm` | Integer |  | — | 키 |
| `weightKg` | Integer |  | — | 몸무게 |
| `agreements[].agreementType` | AgreementType | O | SERVICE_TERMS \| PRIVACY_POLICY \| THIRD_PARTY_PROVISION | 3종 모두 필요 |
| `agreements[].agreed` | Boolean | O | true 여야 함 | — |
| `address.recipientName` | String | O | — | — |
| `address.phone` | String | O | — | — |
| `address.address` | String | O | — | — |
| `address.zipcode` | String | O | 10자 이내 | 우편번호 |
| `address.addressDetail` | String |  | — | — |
| `address.deliveryRequest` | String |  | — | — |

**응답** (`data`)

| 필드 | 타입 | 설명 |
|---|---|---|
| `id` | Long | 생성된 사용자 id |
| `accessToken` | String | 가입 직후 자동 로그인 |
| `refreshToken` | String | — |

```json
{
  "success": true,
  "code": "COMMON_200",
  "message": "요청에 성공했습니다.",
  "data": {
    "id": 1,
    "accessToken": "<String>",
    "refreshToken": "<String>"
  }
}
```

에러 — USER_409_EMAIL, USER_409_NICKNAME, USER_400_PASSWORD, USER_400_AGREEMENT, AUTH_401_VERIFICATION_TOKEN, AUTH_410_VERIFICATION_TOKEN, AUTH_409_TOKEN_CONSUMED, AUTH_400_PURPOSE_MISMATCH

#### API-013 · `GET /api/v1/users/me/account`

계정 정보 조회 (실명·휴대폰·이메일). 인증 **필요**. 관련 화면 SCR-004.

응답 `{ realName, phone, email }`. 프로필(닉네임)과 다른 값이다.

**응답** (`data`)

| 필드 | 타입 | 설명 |
|---|---|---|
| `realName` | String | 실명 |
| `phone` | String | 휴대폰번호 |
| `email` | String | 계정 이메일 |

```json
{
  "success": true,
  "code": "COMMON_200",
  "message": "요청에 성공했습니다.",
  "data": {
    "realName": "<String>",
    "phone": "<String>",
    "email": "<String>"
  }
}
```

에러 — COMMON_401

#### API-014 · `GET /api/v1/users/me`

내 프로필 + 카운터. 인증 **필요**. 관련 화면 SCR-005, SCR-020.

응답 `{ id, nickname, profileImageUrl, gender, heightCm, weightKg, grade, gradeExpiresAt, followerCount, postCount, itemCount, wishCount }`. `itemCount`·`wishCount` 는 카운터 컬럼이 아니라 COUNT 로 계산한다(§6-3 파생 컬럼 대장).

헤더는 피그마 `마이페이지`(`2825:42638`) 기준으로 `레코드 사용자 | 168cm · 58kg` + 카운터 **4종**(`피드 60` · `아이템 16` · `위시 4` · `팔로워 201`) 이다. 타인 프로필(`API-017`)은 `위시` 가 빠진 3종이다.
`gender` 는 화면에 표시되지 않지만 **프로필 수정 폼의 초기값**으로 필요해 본인 응답에만 싣는다.

**응답** (`data`)

| 필드 | 타입 | 설명 |
|---|---|---|
| `id` | Long | — |
| `nickname` | String | — |
| `profileImageUrl` | String | — |
| `gender` | Gender | MALE \| FEMALE |
| `heightCm` | Integer | — |
| `weightKg` | Integer | — |
| `grade` | UserGrade | BASIC \| RECORD |
| `gradeExpiresAt` | LocalDateTime | 레코드 보장 만료 |
| `followerCount` | Integer | 카운터 컬럼 |
| `postCount` | Integer | 카운터 컬럼 |
| `itemCount` | Integer | COUNT 계산 |
| `wishCount` | Integer | COUNT 계산 |

```json
{
  "success": true,
  "code": "COMMON_200",
  "message": "요청에 성공했습니다.",
  "data": {
    "id": 1,
    "nickname": "<String>",
    "profileImageUrl": "https://cdn.tenure.app/post/2026/06/ab12.jpg",
    "gender": "<Gender>",
    "heightCm": 1,
    "weightKg": 1,
    "grade": "<UserGrade>",
    "gradeExpiresAt": "2026-06-21T18:30:00",
    "followerCount": 1,
    "postCount": 1,
    "itemCount": 1,
    "wishCount": 1
  }
}
```

에러 — COMMON_401

#### API-015 · `PATCH /api/v1/users/me`

프로필 수정 (사진·닉네임·성별·키·몸무게). 인증 **필요**. 관련 화면 SCR-005.

요청 `UserUpdateRequest` — `nickname?` `profileImageKey?` `gender?` `heightCm?` `weightKg?`. `profileImageKey` 를 `null` 로 보내면 기본 프로필로 되돌린다.

**요청 본문**

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|:--:|---|---|
| `nickname` | String |  | UNIQUE | — |
| `profileImageKey` | String |  | null 이면 기본 이미지로 | — |
| `gender` | Gender |  | MALE \| FEMALE | — |
| `heightCm` | Integer |  | — | — |
| `weightKg` | Integer |  | — | — |

**응답** (`data`)

| 필드 | 타입 | 설명 |
|---|---|---|
| `id` | Long | — |

```json
{
  "success": true,
  "code": "COMMON_200",
  "message": "요청에 성공했습니다.",
  "data": {
    "id": 1
  }
}
```

에러 — USER_409_NICKNAME

#### API-016 · `DELETE /api/v1/users/me`

회원탈퇴 (사유 수집 + 즉시 익명화). 인증 **필요**. 관련 화면 SCR-009.

요청 `{ reason? }`. **즉시 익명화**한다 — `email` 은 `withdrawn_{id}@deleted.local`, `phone`·`realName` 은 마스크, `nickname` 은 `탈퇴한사용자_{id}` 로 치환하고 배송지·정산계좌·검색기록·알림을 하드 삭제한다. `user` 행과 FK 는 유지되어 `Tenure 기록 기준`의 이전 소유자 표시가 남는다(`DEC-004`).

**요청 본문**

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|:--:|---|---|
| `reason` | String |  | 500자 이내 | 탈퇴 사유 |

**응답** `data` 는 `null` 이다.

에러 — USER_409_WITHDRAWN, USER_409_ACTIVE_TRADE

#### API-017 · `GET /api/v1/users/{userId}`

타인 프로필. 인증 **필요**. 관련 화면 SCR-012.

응답 `{ id, nickname, profileImageUrl, grade, heightCm, weightKg, postCount, itemCount, followerCount, isFollowing, isBlocked }`.

헤더 2행은 피그마 `상대방 프로필 볼 때 화면`(`3910:44008`) 에서 확정했다 — `레코드 사용자 | 168cm · 58kg`.
`grade` 의 등급명과 `heightCm`·`weightKg` 가 구분선(`|`)과 가운뎃점(`·`)으로 이어진 **한 줄**이고, **`gender` 는 이 줄에 없다.** 성별은 프로필 작성·수정(`API-014`/`API-015`)과 검색 필터(`API-200`)에서만 쓰는 비공개 입력값이라 타인 응답에 싣지 않는다.

카운터는 **3종**이다 — `피드 60` · `아이템 16` · `팔로워 201`. 본인 마이페이지(`API-014`)에 있는 `위시 4` 는 타인 프로필에 **없다**(남의 위시리스트는 공개하지 않는다).

탈퇴 사용자는 `nickname` 이 `탈퇴한 사용자` 로 내려가고 `profileImageUrl`·`heightCm`·`weightKg` 는 `null` 이다(익명화 시 NULL 처리, `DEC-004`).

**부수효과** — 조회 시 Redis 의 `recent-view:{userId}:USER` 에 `ZADD` 한다(재열람이면 시각만 갱신). `API-203` 의 `최근 본 사용자` 입력이다(`DEC-014`).

**경로 파라미터**

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|:--:|---|---|
| `userId` | Long | O | — | — |

**응답** (`data`)

| 필드 | 타입 | 설명 |
|---|---|---|
| `id` | Long | — |
| `nickname` | String | 탈퇴자는 '탈퇴한 사용자' |
| `profileImageUrl` | String | 탈퇴자는 null |
| `grade` | UserGrade | BASIC \| RECORD — 헤더의 '레코드 사용자' 배지 |
| `heightCm` | Integer | '168cm' — 탈퇴자는 null |
| `weightKg` | Integer | '58kg' — 탈퇴자는 null |
| `postCount` | Integer | '피드 60' — 카운터 컬럼 |
| `itemCount` | Integer | '아이템 16' — COUNT 계산 |
| `followerCount` | Integer | '팔로워 201' — 카운터 컬럼 |
| `isFollowing` | Boolean | 팔로우 버튼 상태 |
| `isBlocked` | Boolean | — |

```json
{
  "success": true,
  "code": "COMMON_200",
  "message": "요청에 성공했습니다.",
  "data": {
    "id": 1,
    "nickname": "<String>",
    "profileImageUrl": "https://cdn.tenure.app/post/2026/06/ab12.jpg",
    "grade": "<UserGrade>",
    "heightCm": 168,
    "weightKg": 58,
    "postCount": 60,
    "itemCount": 16,
    "followerCount": 201,
    "isFollowing": true,
    "isBlocked": true
  }
}
```

에러 — USER_404

### 배송지

#### API-020 · `GET /api/v1/shipping-addresses`

배송지 목록. 인증 **필요**. 관련 화면 SCR-006.

응답 `ShippingAddressResponse[]` — `{ id, recipientName, phone, zipcode, address, addressDetail, deliveryRequest, isDefault }`. 개수가 적어 페이징하지 않는다(기본 배송지 우선 정렬).

**응답** (`data` 배열)

| 필드 | 타입 | 설명 |
|---|---|---|
| `id` | Long | — |
| `recipientName` | String | 수령인 |
| `phone` | String | — |
| `address` | String | — |
| `zipcode` | String | 우편번호 |
| `addressDetail` | String | — |
| `deliveryRequest` | String | — |
| `isDefault` | Boolean | — |

```json
{
  "success": true,
  "code": "COMMON_200",
  "message": "요청에 성공했습니다.",
  "data": [
    {
      "id": 1,
      "recipientName": "<String>",
      "phone": "<String>",
      "address": "<String>",
      "zipcode": "07027",
      "addressDetail": "<String>",
      "deliveryRequest": "<String>",
      "isDefault": true
    }
  ]
}
```

에러 — COMMON_401

#### API-021 · `POST /api/v1/shipping-addresses`

배송지 추가. 인증 **필요**. 관련 화면 SCR-006, SCR-002.

요청 `ShippingAddressCreateRequest` — `recipientName` `phone` `zipcode` `address` `addressDetail?` `deliveryRequest?` `isDefault?`. 첫 배송지는 자동으로 기본이 된다. 회원가입(API-012)에서도 같은 스키마를 재사용한다.

**요청 본문**

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|:--:|---|---|
| `recipientName` | String | O | — | — |
| `phone` | String | O | — | — |
| `zipcode` | String | O | 10자 이내 | 우편번호 |
| `address` | String | O | — | 도로명주소 |
| `addressDetail` | String |  | — | — |
| `deliveryRequest` | String |  | — | — |
| `isDefault` | Boolean |  | 첫 배송지는 자동 true | — |

**응답** (`data`)

| 필드 | 타입 | 설명 |
|---|---|---|
| `id` | Long | — |

```json
{
  "success": true,
  "code": "COMMON_200",
  "message": "요청에 성공했습니다.",
  "data": {
    "id": 1
  }
}
```

에러 — COMMON_400

#### API-022 · `PUT /api/v1/shipping-addresses/{addressId}`

배송지 수정. 인증 **필요**. 관련 화면 SCR-006.

전체 수정(PUT). 주문에 쓰인 배송지는 `order_shipment` 가 스냅샷을 갖고 있어 과거 거래에 영향이 없다.

**요청 본문**

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|:--:|---|---|
| `recipientName` | String | O | — | — |
| `phone` | String | O | — | — |
| `zipcode` | String | O | 10자 이내 | 우편번호 |
| `address` | String | O | — | 도로명주소 |
| `addressDetail` | String |  | — | — |
| `deliveryRequest` | String |  | — | — |

**응답** (`data`)

| 필드 | 타입 | 설명 |
|---|---|---|
| `id` | Long | — |

```json
{
  "success": true,
  "code": "COMMON_200",
  "message": "요청에 성공했습니다.",
  "data": {
    "id": 1
  }
}
```

에러 — ADDRESS_404, ADDRESS_403

#### API-023 · `DELETE /api/v1/shipping-addresses/{addressId}`

배송지 삭제. 인증 **필요**. 관련 화면 SCR-006.

하드 삭제. 기본 배송지가 하나뿐이면 거부한다.

**경로 파라미터**

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|:--:|---|---|
| `addressId` | Long | O | — | — |

**응답** `data` 는 `null` 이다.

에러 — ADDRESS_404, ADDRESS_403, ADDRESS_409_LAST_DEFAULT

#### API-024 · `PATCH /api/v1/shipping-addresses/{addressId}/default`

기본 배송지 지정. 인증 **필요**. 관련 화면 SCR-006.

기존 기본 배송지를 해제하고 지정한다. 변경할 때마다 토스트를 띄운다.

**경로 파라미터**

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|:--:|---|---|
| `addressId` | Long | O | — | — |

**응답** `data` 는 `null` 이다.

에러 — ADDRESS_404, ADDRESS_403

### 정산 계좌

#### API-030 · `GET /api/v1/payout-accounts`

정산 계좌 목록. 인증 **필요**. 관련 화면 SCR-007.

응답 `PayoutAccountResponse[]` — `{ id, bankCode, accountNumber, accountHolder, isDefault }`. 계좌번호는 마스킹해서 내려준다.

**응답** (`data` 배열)

| 필드 | 타입 | 설명 |
|---|---|---|
| `id` | Long | — |
| `bankCode` | String | 은행 |
| `accountNumber` | String | 마스킹해서 내려준다 |
| `accountHolder` | String | 예금주 |
| `isDefault` | Boolean | — |

```json
{
  "success": true,
  "code": "COMMON_200",
  "message": "요청에 성공했습니다.",
  "data": [
    {
      "id": 1,
      "bankCode": "<String>",
      "accountNumber": "<String>",
      "accountHolder": "<String>",
      "isDefault": true
    }
  ]
}
```

에러 — COMMON_401

#### API-031 · `POST /api/v1/payout-accounts`

정산 계좌 등록. 인증 **필요**. 관련 화면 SCR-007.

요청 `{ bankCode, accountNumber, accountHolder, isDefault? }`. **판매 전환의 선행 조건**이라 판매를 시작하려면 최소 1건이 필요하다.

**요청 본문**

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|:--:|---|---|
| `bankCode` | String | O | — | — |
| `accountNumber` | String | O | — | — |
| `accountHolder` | String | O | — | — |
| `isDefault` | Boolean |  | — | — |

**응답** (`data`)

| 필드 | 타입 | 설명 |
|---|---|---|
| `id` | Long | — |

```json
{
  "success": true,
  "code": "COMMON_200",
  "message": "요청에 성공했습니다.",
  "data": {
    "id": 1
  }
}
```

에러 — COMMON_400

#### API-032 · `PUT /api/v1/payout-accounts/{accountId}`

정산 계좌 수정. 인증 **필요**. 관련 화면 SCR-007.

전체 수정(PUT). 과거 정산은 `settlement` 스냅샷을 쓰므로 영향이 없다.

**요청 본문**

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|:--:|---|---|
| `bankCode` | String | O | — | — |
| `accountNumber` | String | O | — | — |
| `accountHolder` | String | O | — | — |

**응답** (`data`)

| 필드 | 타입 | 설명 |
|---|---|---|
| `id` | Long | — |

```json
{
  "success": true,
  "code": "COMMON_200",
  "message": "요청에 성공했습니다.",
  "data": {
    "id": 1
  }
}
```

에러 — PAYOUT_404, PAYOUT_403

#### API-033 · `DELETE /api/v1/payout-accounts/{accountId}`

정산 계좌 삭제. 인증 **필요**. 관련 화면 SCR-007.

하드 삭제. **정산 미완료 거래가 있으면 거부한다** — 입금할 계좌가 사라지기 때문이다.

**경로 파라미터**

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|:--:|---|---|
| `accountId` | Long | O | — | — |

**응답** `data` 는 `null` 이다.

에러 — PAYOUT_404, PAYOUT_403, PAYOUT_409_PENDING_SETTLEMENT

### 알림

#### API-040 · `GET /api/v1/notification-settings`

알림 수신 설정 조회. 인증 **필요**. 관련 화면 SCR-008.

응답 `{ settings[{ category, enabled }] }` — `ACTION_REQUIRED` · `ITEM_NEWS` · `TRADE_STATUS` · `WISHLIST` 4종. 가입 시 전부 `true` 로 생성된다.

**응답** (`data` 배열)

| 필드 | 타입 | 설명 |
|---|---|---|
| `category` | NotificationCategory | ACTION_REQUIRED \| ITEM_NEWS \| TRADE_STATUS \| WISHLIST |
| `enabled` | Boolean | — |

```json
{
  "success": true,
  "code": "COMMON_200",
  "message": "요청에 성공했습니다.",
  "data": [
    {
      "category": "<NotificationCategory>",
      "enabled": true
    }
  ]
}
```

에러 — COMMON_401

#### API-041 · `PATCH /api/v1/notification-settings`

알림 수신 설정 변경. 인증 **필요**. 관련 화면 SCR-008.

요청 `{ settings[{ category, enabled }] }` — 부분 갱신. 활성화할 때마다 토스트를 띄운다.

**요청 본문**

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|:--:|---|---|
| `settings[].category` | NotificationCategory | O | 4종 중 하나 | — |
| `settings[].enabled` | Boolean | O | — | — |

**응답** `data` 는 `null` 이다.

에러 — COMMON_400

### 소셜

#### API-050 · `GET /api/v1/users/{userId}/followers`

팔로워 목록. 인증 **필요**. 관련 화면 SCR-011.

페이징. 응답 `UserSummaryResponse[]` — `{ id, nickname, profileImageUrl, followerCount, postCount, isFollowing }`. 목록 반복 노출이라 카운터 컬럼을 읽는다.

**경로 파라미터**

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|:--:|---|---|
| `userId` | Long | O | — | — |

**응답** (`data` — PageResponse)

| 필드 | 타입 | 설명 |
|---|---|---|
| `id` | Long | — |
| `nickname` | String | — |
| `profileImageUrl` | String | — |
| `followerCount` | Integer | — |
| `postCount` | Integer | — |
| `isFollowing` | Boolean | — |

```json
{
  "success": true,
  "code": "COMMON_200",
  "message": "요청에 성공했습니다.",
  "data": {
    "content": [
      {
        "id": 1,
        "nickname": "<String>",
        "profileImageUrl": "https://cdn.tenure.app/post/2026/06/ab12.jpg",
        "followerCount": 1,
        "postCount": 1,
        "isFollowing": true
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 137,
    "totalPages": 7,
    "hasNext": true
  }
}
```

에러 — USER_404

#### API-051 · `GET /api/v1/users/{userId}/followings`

팔로잉 목록. 인증 **필요**. 관련 화면 SCR-011.

API-050 과 동일한 스키마. 방향만 다르다.

**경로 파라미터**

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|:--:|---|---|
| `userId` | Long | O | — | — |

**응답** (`data` — PageResponse)

| 필드 | 타입 | 설명 |
|---|---|---|
| `id` | Long | — |
| `nickname` | String | — |
| `profileImageUrl` | String | — |
| `followerCount` | Integer | — |
| `postCount` | Integer | — |
| `isFollowing` | Boolean | — |

```json
{
  "success": true,
  "code": "COMMON_200",
  "message": "요청에 성공했습니다.",
  "data": {
    "content": [
      {
        "id": 1,
        "nickname": "<String>",
        "profileImageUrl": "https://cdn.tenure.app/post/2026/06/ab12.jpg",
        "followerCount": 1,
        "postCount": 1,
        "isFollowing": true
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 137,
    "totalPages": 7,
    "hasNext": true
  }
}
```

에러 — USER_404

#### API-052 · `POST /api/v1/users/{userId}/follow`

팔로우. 인증 **필요**. 관련 화면 SCR-011, SCR-012, SCR-015.

멱등하지 않다. `follow` 생성과 같은 트랜잭션에서 대상의 `follower_count` 를 +1 한다.

**경로 파라미터**

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|:--:|---|---|
| `userId` | Long | O | — | — |

**응답** `data` 는 `null` 이다.

에러 — USER_404, FOLLOW_409, FOLLOW_400_SELF

#### API-053 · `DELETE /api/v1/users/{userId}/follow`

언팔로우. 인증 **필요**. 관련 화면 SCR-011, SCR-012.

`follower_count` 를 -1 한다. 없는 관계면 404 대신 조용히 성공 처리한다.

**경로 파라미터**

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|:--:|---|---|
| `userId` | Long | O | — | — |

**응답** `data` 는 `null` 이다.

에러 — USER_404

#### API-054 · `POST /api/v1/users/{userId}/block`

사용자 차단. 인증 **필요**. 관련 화면 SCR-013, SCR-019, SCR-026, SCR-043.

차단하면 상대가 새 거래 의사·구매 제안을 보낼 수 없고 채팅도 막힌다. 기존 채팅방은 남는다.

**경로 파라미터**

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|:--:|---|---|
| `userId` | Long | O | — | — |

**응답** `data` 는 `null` 이다.

에러 — USER_404, BLOCK_409, BLOCK_400_SELF

#### API-055 · `DELETE /api/v1/users/{userId}/block`

차단 해제. 인증 **필요**. 관련 화면 SCR-013, SCR-043.

하드 삭제. `다시 더보기 아이콘 클릭 시에는 차단해제` 흐름이다.

**경로 파라미터**

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|:--:|---|---|
| `userId` | Long | O | — | — |

**응답** `data` 는 `null` 이다.

에러 — USER_404

#### API-056 · `POST /api/v1/reports`

신고 (게시물·상품·사용자). 인증 **필요**. 관련 화면 SCR-013, SCR-019, SCR-026, SCR-043.

요청 `{ targetType: POST|ITEM|USER, targetId, reason, detail? }`. 사유 10종은 `ReportReason` enum 이다. **접수까지만 담는다** — 운영자 처리 흐름은 이번 범위 밖이다.

**요청 본문**

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|:--:|---|---|
| `targetType` | ReportTargetType | O | POST \| ITEM \| USER | — |
| `targetId` | Long | O | — | — |
| `reason` | ReportReason | O | COUNTERFEIT \| INFO_MISMATCH \| STOLEN_PHOTO \| FRAUD_EXTERNAL_TRADE \| PROHIBITED_ITEM \| INAPPROPRIATE \| NOT_WORN_OOTD \| SPAM \| PRIVACY_EXPOSURE \| IRRELEVANT | — |
| `detail` | String |  | 500자 이내 | — |

**응답** (`data`)

| 필드 | 타입 | 설명 |
|---|---|---|
| `id` | Long | — |

```json
{
  "success": true,
  "code": "COMMON_200",
  "message": "요청에 성공했습니다.",
  "data": {
    "id": 1
  }
}
```

에러 — REPORT_404_TARGET, COMMON_400

### 미디어

#### API-060 · `POST /api/v1/images`

이미지 업로드 → 저장소 키 반환. 인증 **필요**. 관련 화면 SCR-017, SCR-018, SCR-024, SCR-042.

`multipart/form-data` 로 파일을 받아 **저장소 키**를 돌려준다. 요청 `{ file, domain: PROFILE|POST|ITEM|CHAT }` → 응답 `{ key, url }`. `app.storage.type` 에 따라 로컬 또는 S3 에 저장된다(`DEC-006`). 이후 게시물·아이템 생성 API 에는 `key` 만 넘긴다.

**요청 (multipart/form-data)**

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|:--:|---|---|
| `file` | MultipartFile | O | 이미지 형식 | — |
| `domain` | String | O | PROFILE \| POST \| ITEM \| CHAT | 저장 경로 구분 |

**응답** (`data`)

| 필드 | 타입 | 설명 |
|---|---|---|
| `key` | String | 저장소 키 — 이후 생성 API 에 넘긴다 |
| `url` | String | 즉시 미리보기용 URL |

```json
{
  "success": true,
  "code": "COMMON_200",
  "message": "요청에 성공했습니다.",
  "data": {
    "key": "<String>",
    "url": "<String>"
  }
}
```

에러 — MEDIA_400_TYPE, MEDIA_413, MEDIA_500_STORAGE

### 게시물

#### API-070 · `GET /api/v1/posts`

홈 피드 (모두 / 팔로우 탭). 인증 **필요**. 관련 화면 SCR-014.

쿼리 `tab=ALL|FOLLOWING` + 페이징. 정렬 `createdAt,desc` 고정. 차단한 사용자와 `deletedAt IS NOT NULL` 은 제외한다.
응답은 `PostSummaryResponse` — `{ id, imageUrl, aspectRatio, author{id, nickname, profileImageUrl}, likeCount, saveCount, isLiked, isSaved }`.
카드는 **대표 사진 1장**만 싣는다(나머지는 상세에서). `likeCount`·`saveCount` 는 카운터 컬럼을 그대로 읽는다 — 피드에서 게시물마다 집계하지 않으려고 둔 컬럼이다(§6-3 파생 컬럼 대장).

**쿼리 파라미터**

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|:--:|---|---|
| `tab` | String |  | ALL \| FOLLOWING | 기본 ALL |
| `page` | Integer |  | 0부터 | — |
| `size` | Integer |  | 기본 20, 최대 100 | — |

**응답** (`data` — PageResponse)

| 필드 | 타입 | 설명 |
|---|---|---|
| `id` | Long | — |
| `imageUrl` | String | 대표 사진 1장 |
| `aspectRatio` | AspectRatio | RATIO_3_4 \| RATIO_4_5 \| RATIO_1_1 — masonry 높이 계산 |
| `author.id` | Long | — |
| `author.nickname` | String | 카드 상단 작성자 |
| `author.profileImageUrl` | String | — |
| `likeCount` | Integer | 카운터 컬럼 |
| `saveCount` | Integer | 카운터 컬럼 |
| `isLiked` | Boolean | 하트 아이콘 상태 |
| `isSaved` | Boolean | 북마크 아이콘 상태 |

```json
{
  "success": true,
  "code": "COMMON_200",
  "message": "요청에 성공했습니다.",
  "data": {
    "content": [
      {
        "id": 1,
        "imageUrl": "https://cdn.tenure.app/post/2026/06/ab12.jpg",
        "aspectRatio": "<AspectRatio>",
        "author": {
          "id": 1,
          "nickname": "<String>",
          "profileImageUrl": "https://cdn.tenure.app/post/2026/06/ab12.jpg"
        },
        "likeCount": 1,
        "saveCount": 1,
        "isLiked": true,
        "isSaved": true
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 137,
    "totalPages": 7,
    "hasNext": true
  }
}
```

에러 — COMMON_401

#### API-071 · `GET /api/v1/posts/{postId}`

OOTD 상세. 인증 **필요**. 관련 화면 SCR-015.

응답 `PostResponse` — `content` · `images[].tags[{itemId, itemName, brandName, saleStatus, posX, posY}]` · `author{id, nickname, profileImageUrl, followerCount, postCount}` · `isFollowing` · `isLiked` · `isSaved`.

태그 말풍선은 **2행**이다 — 피그마 `OOTD 상세 페이지`(`2791:27177`) 의 말풍선(`2791:27203`) 이 `Levis LVC 1955` 아래에 `#판매중` 을 붙이고 있다. 그래서 `saleStatus` 를 함께 내려준다. `ON_SALE` 일 때만 해시태그를 그리고 그 외에는 1행으로 접는다 — 같은 화면의 다른 말풍선(`Our Legacy / Third Cut`)에는 2행이 없다. 판매 등록이 없는 아이템은 `null` 이다.

작성자 줄은 `팔로우 101명 · 피드 60개` 다. **여기엔 키·몸무게가 없다** — 체형은 프로필(`API-017`)에서만 보여준다.
`isLiked`·`isSaved` 가 있어야 하트·북마크 토글의 초기 상태를 그릴 수 있다.
**부수효과** — 조회 시 Redis 의 `recent-view:{userId}:POST` 에 `ZADD` 한다(재열람이면 시각만 갱신). `API-202` 의 `방금 보신 OOTD와 유사한 게시물` 입력이다(`DEC-014`).

**경로 파라미터**

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|:--:|---|---|
| `postId` | Long | O | — | — |

**응답** (`data`)

| 필드 | 타입 | 설명 |
|---|---|---|
| `id` | Long | — |
| `content` | String | — |
| `wornDate` | LocalDate | — |
| `aspectRatio` | AspectRatio | — |
| `images[].id` | Long | `API-079` 의 입력 키 |
| `images[].url` | String | — |
| `images[].isCover` | Boolean | — |
| `images[].tags[].itemId` | Long | — |
| `images[].tags[].itemName` | String | — |
| `images[].tags[].brandName` | String | — |
| `images[].tags[].saleStatus` | SaleStatus | NOT_FOR_SALE \| ON_SALE \| SOLD — ON_SALE 이면 '#판매중'. 판매 등록 없으면 null |
| `images[].tags[].posX` | BigDecimal | 0.0000~1.0000 |
| `images[].tags[].posY` | BigDecimal | — |
| `author.id` | Long | 본인 게시물 여부 판별 — 더보기 메뉴 분기 |
| `author.nickname` | String | — |
| `author.profileImageUrl` | String | — |
| `author.followerCount` | Integer | — |
| `author.postCount` | Integer | — |
| `isFollowing` | Boolean | — |
| `isLiked` | Boolean | 하트 아이콘 상태 |
| `isSaved` | Boolean | 북마크 아이콘 상태 |
| `likeCount` | Integer | — |
| `saveCount` | Integer | — |

```json
{
  "success": true,
  "code": "COMMON_200",
  "message": "요청에 성공했습니다.",
  "data": {
    "id": 1,
    "content": "<String>",
    "wornDate": "2026-06-21",
    "aspectRatio": "<AspectRatio>",
    "images": [
      {
        "id": 1,
        "url": "<String>",
        "isCover": true,
        "tags": [
          {
            "itemId": 1,
            "itemName": "<String>",
            "brandName": "<String>",
            "saleStatus": "<SaleStatus>",
            "posX": "0.5",
            "posY": "0.5"
          }
        ]
      }
    ],
    "author": {
      "id": 1,
      "nickname": "<String>",
      "profileImageUrl": "https://cdn.tenure.app/post/2026/06/ab12.jpg",
      "followerCount": 1,
      "postCount": 1
    },
    "isFollowing": true,
    "isLiked": true,
    "isSaved": true,
    "likeCount": 1,
    "saveCount": 1
  }
}
```

에러 — POST_404

#### API-072 · `POST /api/v1/posts`

OOTD 게시. 인증 **필요**. 관련 화면 SCR-018.

요청 `PostCreateRequest` — `content?` `wornDate` `aspectRatio` `quickPosted` `images[{imageKey, sortOrder, isCover, tags[{itemId, posX, posY}]}]`. 태그 대상은 **본인이 보유한 아이템**만 허용한다. 게시와 동시에 `item_wear_record` 를 적립하고, 직전 30일 유효 OOTD 가 20개가 되면 그 자리에서 `RECORD` 로 승격한다(`user` 제약 — 레코드 승격).
같은 트랜잭션에서 `user.post_count` 를 +1 한다(§6-3 파생 컬럼 대장).

**요청 본문**

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|:--:|---|---|
| `content` | String |  | — | 본문 |
| `wornDate` | LocalDate | O | — | 착용일 — 달력에서 선택 |
| `aspectRatio` | AspectRatio | O | RATIO_3_4 \| RATIO_4_5 \| RATIO_1_1 | — |
| `quickPosted` | Boolean |  | 기본 false | 바로 게시 여부 |
| `images[].imageKey` | String | O | API-060 응답 | — |
| `images[].sortOrder` | Integer | O | — | — |
| `images[].isCover` | Boolean |  | 1장만 true | — |
| `images[].tags[].itemId` | Long |  | 본인 보유 아이템만 | 이미지당 최대 5개 |
| `images[].tags[].posX` | BigDecimal |  | 0~1 | — |
| `images[].tags[].posY` | BigDecimal |  | 0~1 | — |

**응답** (`data`)

| 필드 | 타입 | 설명 |
|---|---|---|
| `id` | Long | — |
| `gradePromoted` | Boolean | 이 게시로 RECORD 승격됐는지 |

```json
{
  "success": true,
  "code": "COMMON_200",
  "message": "요청에 성공했습니다.",
  "data": {
    "id": 1,
    "gradePromoted": true
  }
}
```

에러 — POST_400_IMAGE_REQUIRED, POST_400_TAG_LIMIT, POST_400_TAG_OWNERSHIP, ITEM_404

#### API-073 · `PATCH /api/v1/posts/{postId}`

게시물 수정 (본문·태그). 인증 **필요**. 관련 화면 SCR-019.

요청 `PostUpdateRequest` — `content?` `tags?`. 태그를 A→B 로 바꾸면 A 의 `item_wear_record` 를 `voided_at` + `TAG_CHANGED` 로 무효화하고 B 에 새 행을 적립한다. 태그를 지우면 `TAG_DELETED` 다. 이미 판매돼 **종료된 소유 구간**의 태그는 수정할 수 없다.

**요청 본문**

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|:--:|---|---|
| `content` | String |  | — | — |
| `tags[].postItemTagId` | Long |  | — | — |
| `tags[].itemId` | Long |  | 본인 보유 아이템만 | — |

**응답** (`data`)

| 필드 | 타입 | 설명 |
|---|---|---|
| `id` | Long | — |

```json
{
  "success": true,
  "code": "COMMON_200",
  "message": "요청에 성공했습니다.",
  "data": {
    "id": 1
  }
}
```

에러 — POST_403, POST_404, ITEM_409_TAG_CLOSED_PERIOD, POST_400_TAG_OWNERSHIP

#### API-074 · `DELETE /api/v1/posts/{postId}`

게시물 삭제 (soft). 인증 **필요**. 관련 화면 SCR-019.

soft delete (`deleted_at`). 판매 중인 상품의 대표 OOTD 로 걸려 있으면 거부한다.
같은 트랜잭션에서 `user.post_count` 를 -1 하고, 이 게시물이 적립한 `item_wear_record` 를 `voided_at` + `TAG_DELETED` 로 무효화한다 — 단 종료된 소유 구간의 기록은 건드리지 않는다.

**경로 파라미터**

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|:--:|---|---|
| `postId` | Long | O | — | — |

**응답** `data` 는 `null` 이다.

에러 — POST_403, POST_409_FEATURED

#### API-075 · `POST /api/v1/posts/{postId}/like`

좋아요. 인증 **필요**. 관련 화면 SCR-015.

멱등하지 않다. 이미 눌렀으면 409. 같은 트랜잭션에서 `post.like_count` 를 +1 하고 갱신값을 돌려준다.

**경로 파라미터**

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|:--:|---|---|
| `postId` | Long | O | — | — |

**응답** (`data`)

| 필드 | 타입 | 설명 |
|---|---|---|
| `likeCount` | Integer | 갱신된 값 |

```json
{
  "success": true,
  "code": "COMMON_200",
  "message": "요청에 성공했습니다.",
  "data": {
    "likeCount": 1
  }
}
```

에러 — POST_409_LIKE, POST_404

#### API-076 · `DELETE /api/v1/posts/{postId}/like`

좋아요 취소. 인증 **필요**. 관련 화면 SCR-015.

같은 트랜잭션에서 `post.like_count` 를 -1 한다. 없는 관계면 조용히 성공 처리한다.

**경로 파라미터**

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|:--:|---|---|
| `postId` | Long | O | — | — |

**응답** (`data`)

| 필드 | 타입 | 설명 |
|---|---|---|
| `likeCount` | Integer | — |

```json
{
  "success": true,
  "code": "COMMON_200",
  "message": "요청에 성공했습니다.",
  "data": {
    "likeCount": 1
  }
}
```

에러 — POST_404

#### API-077 · `POST /api/v1/posts/{postId}/save`

저장. 인증 **필요**. 관련 화면 SCR-015.

멱등하지 않다. 이미 저장했으면 409. 같은 트랜잭션에서 `post.save_count` 를 +1 한다.

**경로 파라미터**

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|:--:|---|---|
| `postId` | Long | O | — | — |

**응답** (`data`)

| 필드 | 타입 | 설명 |
|---|---|---|
| `saveCount` | Integer | — |

```json
{
  "success": true,
  "code": "COMMON_200",
  "message": "요청에 성공했습니다.",
  "data": {
    "saveCount": 1
  }
}
```

에러 — POST_409_SAVE, POST_404

#### API-078 · `DELETE /api/v1/posts/{postId}/save`

저장 취소. 인증 **필요**. 관련 화면 SCR-015.

같은 트랜잭션에서 `post.save_count` 를 -1 한다.

**경로 파라미터**

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|:--:|---|---|
| `postId` | Long | O | — | — |

**응답** (`data`)

| 필드 | 타입 | 설명 |
|---|---|---|
| `saveCount` | Integer | — |

```json
{
  "success": true,
  "code": "COMMON_200",
  "message": "요청에 성공했습니다.",
  "data": {
    "saveCount": 1
  }
}
```

에러 — POST_404

#### API-079 · `GET /api/v1/post-images/{imageId}/tagged-items`

사진 속 태그된 아이템 목록. 인증 **필요**. 관련 화면 SCR-016.

**단위는 게시물이 아니라 사진이다** — `post_item_tag` 가 `post_image_id` 에 매달리고 화면 문구도
`사진 속에서 태그된 것만 모아봅니다` 이므로 경로 키는 `post_image_id` 다. `imageId` 는 `API-071` 의
`images[].id` 로 받는다.

응답 `{ items[{ itemId, listingId?, brandName, name, coverImageUrl, status: ON_SALE|NOT_FOR_SALE|SOLD|DELETED, price?, offerAllowed }] }`.
삭제된 아이템은 `DELETED` 로 내려가고 화면에서 회색 처리된다. `listingId` 가 `null` 이면 판매 등록이
한 번도 없었던 아이템이라 **CTA 버튼을 띄우지 않는다**(`구매제안 불가능 제품에 관하여는 CTA 버튼이 없다`).

**경로 파라미터**

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|:--:|---|---|
| `imageId` | Long | O | `post_image.id` | `API-071` 의 `images[].id` |

**응답** (`data` 배열)

| 필드 | 타입 | 설명 |
|---|---|---|
| `itemId` | Long | — |
| `listingId` | Long | 최신 판매 등록. null 이면 CTA 없음 |
| `brandName` | String | — |
| `name` | String | — |
| `coverImageUrl` | String | — |
| `status` | String | ON_SALE \| NOT_FOR_SALE \| SOLD \| DELETED |
| `price` | String | 금액은 문자열 |
| `offerAllowed` | Boolean | — |

```json
{
  "success": true,
  "code": "COMMON_200",
  "message": "요청에 성공했습니다.",
  "data": [
    {
      "itemId": 1,
      "listingId": 1,
      "brandName": "<String>",
      "name": "<String>",
      "coverImageUrl": "https://cdn.tenure.app/post/2026/06/ab12.jpg",
      "status": "<String>",
      "price": "360000",
      "offerAllowed": true
    }
  ]
}
```

에러 — POST_404_IMAGE

#### API-080 · `POST /api/v1/posts/analyze`

AI 아이템 분석 (태그 후보 조회). 인증 **필요**. 관련 화면 SCR-018.

요청 `{ imageKey, x, y }` → 응답 `{ candidates[{ itemId, brandName, name, coverImageUrl, score }] }`. 후보는 **본인 옷장 범위**에서만 찾으며 결과를 저장하지 않는다(설계 범위 제외). 사용자가 고른 것만 API-072 로 확정된다.

**요청 본문**

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|:--:|---|---|
| `imageKey` | String | O | — | — |
| `x` | BigDecimal | O | 0~1 | 터치 좌표 |
| `y` | BigDecimal | O | 0~1 | — |

**응답** (`data`)

| 필드 | 타입 | 설명 |
|---|---|---|
| `candidates[].itemId` | Long | 본인 옷장 범위 |
| `candidates[].brandName` | String | — |
| `candidates[].name` | String | — |
| `candidates[].coverImageUrl` | String | — |
| `candidates[].score` | BigDecimal | 유사도 — 저장하지 않는다 |

```json
{
  "success": true,
  "code": "COMMON_200",
  "message": "요청에 성공했습니다.",
  "data": {
    "candidates": [
      {
        "itemId": 1,
        "brandName": "<String>",
        "name": "<String>",
        "coverImageUrl": "https://cdn.tenure.app/post/2026/06/ab12.jpg",
        "score": "0.5"
      }
    ]
  }
}
```

에러 — MEDIA_400_TYPE

#### API-081 · `GET /api/v1/users/me/posts`

내 게시물 (게시물 / 좋아요 / 저장 탭). 인증 **필요**. 관련 화면 SCR-020.

쿼리 `tab=POSTS|LIKED|SAVED`, `yearMonth?`(`2026-06`) + 페이징. 정렬 `wornDate,desc`.

**쿼리 파라미터**

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|:--:|---|---|
| `tab` | String |  | POSTS \| LIKED \| SAVED | 기본 POSTS |
| `yearMonth` | String |  | 2026-06 | 날짜별 그룹 |
| `page` | Integer |  | — | — |
| `size` | Integer |  | — | — |

**응답** (`data` — PageResponse)

| 필드 | 타입 | 설명 |
|---|---|---|
| `id` | Long | — |
| `imageUrl` | String | 그리드 썸네일 |
| `wornDate` | LocalDate | 월별 그룹 헤더용 |
| `isLiked` | Boolean | 그리드 오버레이 아이콘 |
| `isSaved` | Boolean | — |

```json
{
  "success": true,
  "code": "COMMON_200",
  "message": "요청에 성공했습니다.",
  "data": {
    "content": [
      {
        "id": 1,
        "imageUrl": "https://cdn.tenure.app/post/2026/06/ab12.jpg",
        "wornDate": "2026-06-21",
        "isLiked": true,
        "isSaved": true
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 137,
    "totalPages": 7,
    "hasNext": true
  }
}
```

에러 — COMMON_401

#### API-082 · `GET /api/v1/users/{userId}/posts`

타인 게시물 목록. 인증 **필요**. 관련 화면 SCR-012.

페이징. 차단 관계면 빈 목록을 돌려준다.

**경로 파라미터**

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|:--:|---|---|
| `userId` | Long | O | — | — |

**응답** (`data` — PageResponse)

| 필드 | 타입 | 설명 |
|---|---|---|
| `id` | Long | — |
| `imageUrl` | String | 그리드 썸네일 |
| `wornDate` | LocalDate | — |

```json
{
  "success": true,
  "code": "COMMON_200",
  "message": "요청에 성공했습니다.",
  "data": {
    "content": [
      {
        "id": 1,
        "imageUrl": "https://cdn.tenure.app/post/2026/06/ab12.jpg",
        "wornDate": "2026-06-21"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 137,
    "totalPages": 7,
    "hasNext": true
  }
}
```

에러 — USER_404

#### API-083 · `PATCH /api/v1/posts/tags`

태그 일괄 수정 (MVP 외). 인증 **필요**. 관련 화면 SCR-021.

**MVP 외.** 요청 `{ edits[{ postItemTagId, afterItemId }] }`. 최근 3일 이내 `quickPosted=true` 게시물만 대상이다.

**요청 본문**

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|:--:|---|---|
| `edits[].postItemTagId` | Long | O | — | — |
| `edits[].afterItemId` | Long | O | 본인 보유 아이템만 | — |

**응답** (`data`)

| 필드 | 타입 | 설명 |
|---|---|---|
| `updatedCount` | Integer | — |

```json
{
  "success": true,
  "code": "COMMON_200",
  "message": "요청에 성공했습니다.",
  "data": {
    "updatedCount": 1
  }
}
```

에러 — POST_400_EDIT_WINDOW, ITEM_409_TAG_CLOSED_PERIOD

### 아이템

#### API-090 · `GET /api/v1/categories`

카테고리 트리 (대분류 10 · 세부 102). 인증 **필요**. 관련 화면 SCR-024, SCR-047.

응답 `{ categories[{ id, name, depth, sizeScheme, children[] }] }`. 대분류 10종 · 세부 102종. id 는 `V2__seed_category.sql` 의 고정값이다(대분류 1~10, 세부 101~202). **`sizeScheme` 값 목록은 미확정(`Q6`)** — 현재는 `null` 로 내려간다.

**응답** (`data` 배열)

| 필드 | 타입 | 설명 |
|---|---|---|
| `id` | Long | — |
| `name` | String | 대분류 10종 / 세부 102종 |
| `depth` | Integer | 1 \| 2 |
| `sizeScheme` | String | **Q6 미확정 — null** |
| `children[]` | Category | 하위 카테고리 |

```json
{
  "success": true,
  "code": "COMMON_200",
  "message": "요청에 성공했습니다.",
  "data": [
    {
      "id": 1,
      "name": "<String>",
      "depth": 1,
      "sizeScheme": "<String>",
      "children": [
        "<Category>"
      ]
    }
  ]
}
```

에러 — COMMON_401

#### API-091 · `GET /api/v1/items`

내가 등록한 아이템 목록. 인증 **필요**. 관련 화면 SCR-022.

페이징. 응답에 `listing { id, saleStatus, price, offerAllowed, wishCount }` 를 중첩한다.

**쿼리 파라미터**

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|:--:|---|---|
| `page` | Integer |  | — | — |
| `size` | Integer |  | — | — |

**응답** (`data` — PageResponse)

| 필드 | 타입 | 설명 |
|---|---|---|
| `id` | Long | — |
| `brandName` | String | — |
| `name` | String | — |
| `coverImageUrl` | String | — |
| `listing.id` | Long | — |
| `listing.saleStatus` | SaleStatus | NOT_FOR_SALE \| ON_SALE \| SOLD |
| `listing.price` | String | — |
| `listing.offerAllowed` | Boolean | — |
| `listing.wishCount` | Integer | — |

```json
{
  "success": true,
  "code": "COMMON_200",
  "message": "요청에 성공했습니다.",
  "data": {
    "content": [
      {
        "id": 1,
        "brandName": "<String>",
        "name": "<String>",
        "coverImageUrl": "https://cdn.tenure.app/post/2026/06/ab12.jpg",
        "listing": {
          "id": 1,
          "saleStatus": "<SaleStatus>",
          "price": "360000",
          "offerAllowed": true,
          "wishCount": 1
        }
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 137,
    "totalPages": 7,
    "hasNext": true
  }
}
```

에러 — COMMON_401

#### API-092 · `POST /api/v1/items`

아이템 등록. 인증 **필요**. 관련 화면 SCR-024, SCR-018.

요청 `ItemCreateRequest` — `name` `brandName` `categoryId` `sizeLabel?` `wearTarget?` `coverImageKey` `description?` `firstOwnedDate?` `images[]` `measurements?` `conditions[]`. 실측은 **대분류 이름**으로 필수·금지가 갈린다 — 상의·아우터·원피스 4종, 하의 6종, 치마 4종, 신발·가방·모자·액세서리·주얼리는 입력 자체가 금지다. 생성과 동시에 `item_ownership` 최초 구간을 연다.

**요청 본문**

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|:--:|---|---|
| `name` | String | O | — | 아이템명 |
| `brandName` | String | O | — | — |
| `categoryId` | Long | O | depth=2 | — |
| `sizeLabel` | String |  | — | — |
| `wearTarget` | WearTarget |  | MALE \| FEMALE \| UNISEX | — |
| `coverImageKey` | String | O | API-060 응답 | 대표 사진 |
| `description` | String |  | — | — |
| `firstOwnedDate` | LocalDate |  | — | 최초 보유 날짜 = 최초 소유 구간 start_date |
| `images[].imageKey` | String |  | — | — |
| `images[].sortOrder` | Integer |  | — | — |
| `measurements.shoulderWidth` | BigDecimal |  | 대분류별 필수·금지 | 상의·아우터·원피스 필수 |
| `measurements.chestWidth` | BigDecimal |  | — | 상의·아우터·원피스 필수 |
| `measurements.sleeveLength` | BigDecimal |  | — | 상의·아우터·원피스 필수 |
| `measurements.totalLength` | BigDecimal |  | — | 상의·아우터·원피스·하의·치마 필수 |
| `measurements.waistWidth` | BigDecimal |  | — | 하의·치마 필수 |
| `measurements.thighWidth` | BigDecimal |  | — | 하의 필수 |
| `measurements.rise` | BigDecimal |  | — | 하의 필수 |
| `measurements.inseam` | BigDecimal |  | — | 하의 필수 |
| `measurements.hemWidth` | BigDecimal |  | — | 하의·치마 필수 |
| `measurements.hipWidth` | BigDecimal |  | — | 치마 필수 |
| `conditions[]` | ConditionCode |  | STAIN \| TEAR \| PILLING_DISCOLOR \| REPAIRED \| MISSING_PARTS | 복수 선택 |

**응답** (`data`)

| 필드 | 타입 | 설명 |
|---|---|---|
| `id` | Long | — |

```json
{
  "success": true,
  "code": "COMMON_200",
  "message": "요청에 성공했습니다.",
  "data": {
    "id": 1
  }
}
```

에러 — ITEM_400_MEASUREMENT, ITEM_400_CATEGORY, CATEGORY_404

#### API-093 · `GET /api/v1/items/{itemId}`

아이템 상세 (소유자 시점). 인증 **필요**. 관련 화면 SCR-023.

응답에 `coverImageUrl` · `images[]` · `measurements` · `conditions` · `ownerships[{ ownerNickname, startDate, endDate, wearCount, acquiredBy }]` · `recentWornDate` · `totalWearCount` · `listing` 을 중첩한다.
`listing` 은 **nullable** 이다 — 판매 전환을 한 번도 하지 않은 아이템에는 `item_listing` 행이 없다. `listing` 이 `null` 이면 화면은 `판매 전환` CTA 를, 있으면 `판매 상태 변경`(`API-113`)·`판매 정보 수정`(`API-112`) CTA 를 띄운다 — 둘 다 `listing.id` 를 받으므로 상세 응답에 반드시 실어야 한다.

**경로 파라미터**

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|:--:|---|---|
| `itemId` | Long | O | — | — |

**응답** (`data`)

| 필드 | 타입 | 설명 |
|---|---|---|
| `id` | Long | — |
| `name` | String | — |
| `brandName` | String | — |
| `coverImageUrl` | String | 대표 사진 |
| `images[].id` | Long | — |
| `images[].url` | String | 상세 사진 캐러셀 |
| `images[].sortOrder` | Integer | — |
| `category.id` | Long | 수정 화면의 드롭다운 초기값 |
| `category.name` | String | — |
| `sizeLabel` | String | — |
| `wearTarget` | WearTarget | — |
| `description` | String | — |
| `firstOwnedDate` | LocalDate | — |
| `measurements` | ProductMeasurements | 10필드 고정. 카테고리별로 채우는 것이 다르다 |
| `conditions[]` | ConditionCode | — |
| `ownerships[].ownerNickname` | String | — |
| `ownerships[].startDate` | LocalDate | — |
| `ownerships[].endDate` | LocalDate | null 이면 현재 |
| `ownerships[].wearCount` | Integer | voided 제외 |
| `ownerships[].acquiredBy` | AcquiredBy | INITIAL_REGISTRATION \| TRADE |
| `recentWornDate` | LocalDate | 최근 착용 |
| `totalWearCount` | Integer | 소유 구간 합계 — voided 제외 |
| `listing` | Object | **nullable** — 판매 전환 이력이 없으면 null |
| `listing.id` | Long | `API-112`·`API-113` 의 입력 키 |
| `listing.saleStatus` | SaleStatus | NOT_FOR_SALE \| ON_SALE \| SOLD |
| `listing.price` | String | 미판매면 null |
| `listing.offerAllowed` | Boolean | `구매 제안 허용` 토글 상태 |
| `listing.wishCount` | Integer | 관심 N명 |

```json
{
  "success": true,
  "code": "COMMON_200",
  "message": "요청에 성공했습니다.",
  "data": {
    "id": 1,
    "name": "<String>",
    "brandName": "<String>",
    "category": {
      "id": 1,
      "name": "<String>"
    },
    "coverImageUrl": "https://cdn.tenure.app/item/2026/06/ab12.jpg",
    "images": [
      {
        "id": 1,
        "url": "https://cdn.tenure.app/item/2026/06/ab12.jpg",
        "sortOrder": 0
      }
    ],
    "sizeLabel": "<String>",
    "wearTarget": "<WearTarget>",
    "description": "<String>",
    "firstOwnedDate": "2026-06-21",
    "measurements": "<ProductMeasurements>",
    "conditions": [
      "<ConditionCode>"
    ],
    "ownerships": [
      {
        "ownerNickname": "<String>",
        "startDate": "2026-06-21",
        "endDate": "2026-06-21",
        "wearCount": 1,
        "acquiredBy": "<AcquiredBy>"
      }
    ],
    "recentWornDate": "2026-06-21",
    "totalWearCount": 20,
    "listing": {
      "id": 1,
      "saleStatus": "<SaleStatus>",
      "price": "360000",
      "offerAllowed": true,
      "wishCount": 14
    }
  }
}
```

에러 — ITEM_404, ITEM_403

#### API-094 · `PATCH /api/v1/items/{itemId}`

아이템 상세 정보 수정. 인증 **필요**. 관련 화면 SCR-024.

요청 `ItemUpdateRequest`. 실측 규칙은 API-092 와 동일하다. `firstOwnedDate` 를 바꾸면 **최초 소유 구간의 `start_date` 도 함께 갱신**된다 — 단 소유 구간이 1개이고 본인이 현재 소유자일 때만 허용하며, 거래 이력이 생기면 잠긴다(`DEC-007`).

사진도 여기서 바꾼다 — `coverImageKey` 는 교체, `images[]` 는 **전체 교체**다(보낸 배열이 곧 최종 상태이고, 생략하면 유지한다). `item_image` 는 하드 삭제 대상이라 개별 삭제 엔드포인트를 따로 두지 않는다.

**요청 본문**

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|:--:|---|---|
| `name` | String |  | — | — |
| `brandName` | String |  | — | — |
| `categoryId` | Long |  | depth=2 | — |
| `sizeLabel` | String |  | — | — |
| `wearTarget` | WearTarget |  | — | — |
| `coverImageKey` | String |  | API-060 응답 | 대표 사진 교체 |
| `images[].imageKey` | String |  | API-060 응답 | **전체 교체** — 생략하면 유지 |
| `images[].sortOrder` | Integer |  | — | — |
| `description` | String |  | — | — |
| `firstOwnedDate` | LocalDate |  | 구간 1개 + 본인 소유일 때만 | 최초 구간 start_date 도 갱신 |
| `measurements` | ProductMeasurements |  | 대분류별 필수·금지 | — |
| `conditions[]` | ConditionCode |  | — | — |

**응답** (`data`)

| 필드 | 타입 | 설명 |
|---|---|---|
| `id` | Long | — |

```json
{
  "success": true,
  "code": "COMMON_200",
  "message": "요청에 성공했습니다.",
  "data": {
    "id": 1
  }
}
```

에러 — ITEM_403, ITEM_400_MEASUREMENT, CATEGORY_404

#### API-095 · `DELETE /api/v1/items/{itemId}`

아이템 삭제 (soft). 인증 **필요**. 관련 화면 SCR-022.

soft delete. 태그는 남아 `태그된 아이템` 목록에 `삭제된 아이템` 으로 표시된다.

**경로 파라미터**

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|:--:|---|---|
| `itemId` | Long | O | — | — |

**응답** `data` 는 `null` 이다.

에러 — ITEM_403, ITEM_409_ON_SALE

#### API-096 · `GET /api/v1/items/{itemId}/history`

아이템 히스토리 (등장 OOTD 그리드). 인증 **필요**. 관련 화면 SCR-025.

페이징. 응답 `{ posts[{ postId, imageUrl, wornDate }] }` — 이 아이템이 등장한 OOTD 사진 그리드. `voided_at IS NULL` 인 착용 기록만 센다.

**경로 파라미터**

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|:--:|---|---|
| `itemId` | Long | O | — | — |

**응답** (`data` — PageResponse)

| 필드 | 타입 | 설명 |
|---|---|---|
| `postId` | Long | — |
| `imageUrl` | String | — |
| `wornDate` | LocalDate | — |

```json
{
  "success": true,
  "code": "COMMON_200",
  "message": "요청에 성공했습니다.",
  "data": {
    "content": [
      {
        "postId": 1,
        "imageUrl": "https://cdn.tenure.app/post/2026/06/ab12.jpg",
        "wornDate": "2026-06-21"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 137,
    "totalPages": 7,
    "hasNext": true
  }
}
```

에러 — ITEM_404

#### API-097 · `GET /api/v1/items/{itemId}/ownerships`

Tenure 기록 기준 (소유 구간 + 인증 횟수). 인증 **필요**. 관련 화면 SCR-023, SCR-026.

응답 `{ ownerships[{ ownerNickname, startDate, endDate, wearCount, isInitial }], totalWearCount, firstOwnedDate, recentWornDate }`. `Tenure 기록 기준` 섹션이 이 응답이다.

**경로 파라미터**

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|:--:|---|---|
| `itemId` | Long | O | — | — |

**응답** (`data`)

| 필드 | 타입 | 설명 |
|---|---|---|
| `ownerships[].ownerNickname` | String | 탈퇴자는 '탈퇴한 사용자' |
| `ownerships[].startDate` | LocalDate | — |
| `ownerships[].endDate` | LocalDate | — |
| `ownerships[].wearCount` | Integer | OOTD 인증 N회 |
| `ownerships[].isInitial` | Boolean | 최초 등록 |
| `totalWearCount` | Integer | — |
| `firstOwnedDate` | LocalDate | — |
| `recentWornDate` | LocalDate | — |

```json
{
  "success": true,
  "code": "COMMON_200",
  "message": "요청에 성공했습니다.",
  "data": {
    "ownerships": [
      {
        "ownerNickname": "<String>",
        "startDate": "2026-06-21",
        "endDate": "2026-06-21",
        "wearCount": 1,
        "isInitial": true
      }
    ],
    "totalWearCount": 1,
    "firstOwnedDate": "2026-06-21",
    "recentWornDate": "2026-06-21"
  }
}
```

에러 — ITEM_404

#### API-098 · `GET /api/v1/items/me/taggable`

태그 가능한 내 아이템 (본인 보유분). 인증 **필요**. 관련 화면 SCR-018.

쿼리 `keyword?` + 페이징. 현재 소유 구간이 열려 있는(`end_date IS NULL`) 본인 아이템만 내려간다.

**쿼리 파라미터**

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|:--:|---|---|
| `keyword` | String |  | — | — |
| `page` | Integer |  | — | — |
| `size` | Integer |  | — | — |

**응답** (`data` — PageResponse)

| 필드 | 타입 | 설명 |
|---|---|---|
| `id` | Long | — |
| `brandName` | String | — |
| `name` | String | — |
| `coverImageUrl` | String | — |

```json
{
  "success": true,
  "code": "COMMON_200",
  "message": "요청에 성공했습니다.",
  "data": {
    "content": [
      {
        "id": 1,
        "brandName": "<String>",
        "name": "<String>",
        "coverImageUrl": "https://cdn.tenure.app/post/2026/06/ab12.jpg"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 137,
    "totalPages": 7,
    "hasNext": true
  }
}
```

에러 — COMMON_401

### 판매·위시리스트

#### API-110 · `GET /api/v1/listings/{listingId}`

상품 상세 (구매자 시점). 인증 **필요**. 관련 화면 SCR-026, SCR-031.

응답 `ListingResponse` — 상품 정보 + `measurements` + `conditions` + `ownerships`(Tenure 기록 기준) + `featuredPosts[]` + `seller{ id, nickname, profileImageUrl, followerCount, grade }` + `wishCount` + `isWished` + `offerAllowed` + `myOfferStatus`. 판매 완료·미판매·구매제안 off 상태에 따라 CTA 가 달라진다.

**`itemId` 를 반드시 함께 내려준다.** 이 화면에서 나가는 동작이 전부 아이템 기준이기 때문이다 —
관심 등록(`API-121`)·해제(`API-122`)·채팅 개설(`API-181`) 은 경로가 `/items/{itemId}/…` 이고
신고(`API-056`)는 `targetType=ITEM` + `targetId=itemId` 다. `id`(=`listingId`)만으로는 호출할 수 없다.

**경로 파라미터**

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|:--:|---|---|
| `listingId` | Long | O | — | — |

**응답** (`data`)

| 필드 | 타입 | 설명 |
|---|---|---|
| `id` | Long | `listingId` |
| `itemId` | Long | 관심·채팅·신고 호출 키 |
| `item.brandName` | String | — |
| `item.name` | String | — |
| `item.images[]` | String | 상품 사진 |
| `price` | String | — |
| `shippingFee` | String | 0 이면 판매자 부담 |
| `feeBearer` | FeeBearer | SELLER \| BUYER \| SPLIT |
| `saleStatus` | SaleStatus | NOT_FOR_SALE \| ON_SALE \| SOLD |
| `offerAllowed` | Boolean | 구매 제안 O/X |
| `sellerNote` | String | 판매자 설명 |
| `category.name` | String | — |
| `sizeLabel` | String | — |
| `wearTarget` | WearTarget | — |
| `measurements` | ProductMeasurements | 실측 입력 |
| `conditions[]` | ConditionCode | 상태 이상 체크 |
| `firstOwnedDate` | LocalDate | Tenure 기록 기준 |
| `recentWornDate` | LocalDate | 최근 착용 |
| `totalWearCount` | Integer | OOTD 인증 |
| `ownerships[]` | Ownership | 소유자별 구간 + 인증 횟수 |
| `featuredPosts[].postId` | Long | 대표 OOTD |
| `featuredPosts[].imageUrl` | String | — |
| `seller.id` | Long | — |
| `seller.nickname` | String | — |
| `seller.profileImageUrl` | String | — |
| `seller.followerCount` | Integer | — |
| `seller.grade` | UserGrade | BASIC \| RECORD — 수수료·배송비 표기가 갈린다 |
| `wishCount` | Integer | 관심 N명 |
| `isWished` | Boolean | — |
| `myOfferStatus` | OfferStatus | 내가 보낸 제안 상태. 없으면 null |

```json
{
  "success": true,
  "code": "COMMON_200",
  "message": "요청에 성공했습니다.",
  "data": {
    "id": 1,
    "itemId": 1,
    "item": {
      "brandName": "<String>",
      "name": "<String>",
      "images": [
        "<String>"
      ]
    },
    "price": "360000",
    "shippingFee": "360000",
    "feeBearer": "<FeeBearer>",
    "saleStatus": "<SaleStatus>",
    "offerAllowed": true,
    "sellerNote": "<String>",
    "category": {
      "name": "<String>"
    },
    "sizeLabel": "<String>",
    "wearTarget": "<WearTarget>",
    "measurements": "<ProductMeasurements>",
    "conditions": [
      "<ConditionCode>"
    ],
    "firstOwnedDate": "2026-06-21",
    "recentWornDate": "2026-06-21",
    "totalWearCount": 1,
    "ownerships": [
      "<Ownership>"
    ],
    "featuredPosts": [
      {
        "postId": 1,
        "imageUrl": "https://cdn.tenure.app/post/2026/06/ab12.jpg"
      }
    ],
    "seller": {
      "id": 1,
      "nickname": "<String>",
      "profileImageUrl": "https://cdn.tenure.app/post/2026/06/ab12.jpg",
      "followerCount": 1,
      "grade": "<UserGrade>"
    },
    "wishCount": 1,
    "isWished": true,
    "myOfferStatus": "<OfferStatus>"
  }
}
```

에러 — LISTING_404

#### API-111 · `POST /api/v1/items/{itemId}/listings`

판매 전환. 인증 **필요**. 관련 화면 SCR-027.

요청 `ListingCreateRequest` — `price` `shippingFee?` `feeBearer?` `sellerNote?` `featuredPostIds[1..5]` `measurements?` `conditions[]`. **정산 계좌가 없으면 거부한다.** 기본 사용자는 `feeBearer`·`shippingFee` 를 보낼 수 없다(보내면 403).

**요청 본문**

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|:--:|---|---|
| `price` | String | O | — | 가격 |
| `shippingFee` | String |  | BASIC 은 입력 불가 | 0 이면 판매자 부담 |
| `feeBearer` | FeeBearer |  | BASIC 은 SELLER 고정 | SELLER \| BUYER \| SPLIT |
| `sellerNote` | String |  | — | — |
| `featuredPostIds[]` | Long | O | 1개 이상 5개 이하 | 대표 OOTD |
| `measurements` | ProductMeasurements |  | 대분류별 필수·금지 | — |
| `conditions[]` | ConditionCode |  | — | — |

**응답** (`data`)

| 필드 | 타입 | 설명 |
|---|---|---|
| `id` | Long | — |

```json
{
  "success": true,
  "code": "COMMON_200",
  "message": "요청에 성공했습니다.",
  "data": {
    "id": 1
  }
}
```

에러 — PAYOUT_400_REQUIRED, LISTING_400_FEATURED_RANGE, LISTING_403_FEE_BEARER, ITEM_403

#### API-112 · `PATCH /api/v1/listings/{listingId}`

판매 정보 수정 (가격·배송비·수수료 부담). 인증 **필요**. 관련 화면 SCR-027.

진행 중인 제안이 있어도 이미 전송된 제안의 금액 스냅샷은 바뀌지 않는다.

**요청 본문**

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|:--:|---|---|
| `price` | String |  | — | — |
| `shippingFee` | String |  | BASIC 은 입력 불가 | — |
| `feeBearer` | FeeBearer |  | BASIC 은 SELLER 고정 | — |
| `sellerNote` | String |  | — | — |

**응답** (`data`)

| 필드 | 타입 | 설명 |
|---|---|---|
| `id` | Long | — |

```json
{
  "success": true,
  "code": "COMMON_200",
  "message": "요청에 성공했습니다.",
  "data": {
    "id": 1
  }
}
```

에러 — LISTING_403, LISTING_409_SOLD, LISTING_403_FEE_BEARER

#### API-113 · `PATCH /api/v1/listings/{listingId}/status`

판매 상태 변경 (판매중 / 미판매 / 판매완료). 인증 **필요**. 관련 화면 SCR-029.

요청 `{ saleStatus, offerAllowed? }`. `미판매 + 구매제안 off` 로 바꾸면 신규 채팅 개설이 막힌다(기존 방은 유지).

**요청 본문**

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|:--:|---|---|
| `saleStatus` | SaleStatus | O | NOT_FOR_SALE \| ON_SALE \| SOLD | — |
| `offerAllowed` | Boolean |  | — | 미판매일 때만 의미 있음 |

**응답** (`data`)

| 필드 | 타입 | 설명 |
|---|---|---|
| `id` | Long | — |
| `saleStatus` | SaleStatus | — |

```json
{
  "success": true,
  "code": "COMMON_200",
  "message": "요청에 성공했습니다.",
  "data": {
    "id": 1,
    "saleStatus": "<SaleStatus>"
  }
}
```

에러 — LISTING_403, LISTING_409_SOLD

#### API-114 · `GET /api/v1/listings/{listingId}/featured-posts`

대표 OOTD 조회. 인증 **필요**. 관련 화면 SCR-028.

**경로 파라미터**

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|:--:|---|---|
| `listingId` | Long | O | — | — |

**응답** (`data` 배열)

| 필드 | 타입 | 설명 |
|---|---|---|
| `postId` | Long | — |
| `imageUrl` | String | — |
| `wornDate` | LocalDate | — |

```json
{
  "success": true,
  "code": "COMMON_200",
  "message": "요청에 성공했습니다.",
  "data": [
    {
      "postId": 1,
      "imageUrl": "https://cdn.tenure.app/post/2026/06/ab12.jpg",
      "wornDate": "2026-06-21"
    }
  ]
}
```

에러 — LISTING_404

#### API-115 · `PUT /api/v1/listings/{listingId}/featured-posts`

대표 OOTD 교체 (1~5개). 인증 **필요**. 관련 화면 SCR-028.

요청 `{ postIds[1..5] }` — 전체 교체(PUT). 본인 게시물만 가능하다.

**요청 본문**

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|:--:|---|---|
| `postIds[]` | Long | O | 1~5개, 본인 게시물 | 전체 교체 |

**응답** (`data` 배열)

| 필드 | 타입 | 설명 |
|---|---|---|
| `postId` | Long | — |
| `imageUrl` | String | — |

```json
{
  "success": true,
  "code": "COMMON_200",
  "message": "요청에 성공했습니다.",
  "data": [
    {
      "postId": 1,
      "imageUrl": "https://cdn.tenure.app/post/2026/06/ab12.jpg"
    }
  ]
}
```

에러 — LISTING_403, LISTING_400_FEATURED_RANGE, POST_404

#### API-120 · `GET /api/v1/wishlists`

위시리스트. 인증 **필요**. 관련 화면 SCR-030.

페이징. 항목마다 `saleStatus` 배지와 `alarmEnabled` 를 함께 내려준다.
`listingId` 는 **nullable** 이다 — 위시는 아이템 기준이라 판매 등록이 없는 아이템도 담긴다. 값이 있으면 항목 탭 시 상품 상세(`API-110`)로 이동하고, `null` 이면 이동할 상세가 없다.

**쿼리 파라미터**

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|:--:|---|---|
| `page` | Integer |  | — | — |
| `size` | Integer |  | — | — |

**응답** (`data` — PageResponse)

| 필드 | 타입 | 설명 |
|---|---|---|
| `wishlistId` | Long | — |
| `itemId` | Long | 관심 해제(`API-122`) 호출 키 |
| `listingId` | Long | **nullable** — 상품 상세(`API-110`) 이동 키 |
| `brandName` | String | — |
| `name` | String | — |
| `coverImageUrl` | String | — |
| `price` | String | — |
| `saleStatus` | SaleStatus | 판매중 / 미판매 배지 |
| `sellerNickname` | String | — |
| `listedAgo` | String | '1일 전' |
| `alarmEnabled` | Boolean | 알림 벨 |
| `offerAllowed` | Boolean | '구매 제안' 버튼 노출 여부 |

```json
{
  "success": true,
  "code": "COMMON_200",
  "message": "요청에 성공했습니다.",
  "data": {
    "content": [
      {
        "wishlistId": 1,
        "itemId": 1,
        "listingId": 1,
        "brandName": "<String>",
        "name": "<String>",
        "coverImageUrl": "https://cdn.tenure.app/post/2026/06/ab12.jpg",
        "price": "360000",
        "saleStatus": "<SaleStatus>",
        "sellerNickname": "<String>",
        "listedAgo": "<String>",
        "alarmEnabled": true,
        "offerAllowed": true
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 137,
    "totalPages": 7,
    "hasNext": true
  }
}
```

에러 — COMMON_401

#### API-121 · `POST /api/v1/items/{itemId}/wishlist`

관심 등록. 인증 **필요**. 관련 화면 SCR-026, SCR-030.

같은 트랜잭션에서 해당 아이템의 최신 `item_listing.wish_count` 를 +1 한다. 판매 등록이 없는 아이템이면 `wishlist` 행만 쌓이고 카운터는 없다 — 응답 `wishCount` 는 그 경우 `wishlist` 를 COUNT 해서 돌려준다.

**경로 파라미터**

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|:--:|---|---|
| `itemId` | Long | O | — | — |

**응답** (`data`)

| 필드 | 타입 | 설명 |
|---|---|---|
| `wishCount` | Integer | 갱신된 값 |

```json
{
  "success": true,
  "code": "COMMON_200",
  "message": "요청에 성공했습니다.",
  "data": {
    "wishCount": 1
  }
}
```

에러 — WISHLIST_409, ITEM_404

#### API-122 · `DELETE /api/v1/items/{itemId}/wishlist`

관심 해제. 인증 **필요**. 관련 화면 SCR-026, SCR-030.

같은 트랜잭션에서 `item_listing.wish_count` 를 -1 한다.

**경로 파라미터**

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|:--:|---|---|
| `itemId` | Long | O | — | — |

**응답** (`data`)

| 필드 | 타입 | 설명 |
|---|---|---|
| `wishCount` | Integer | — |

```json
{
  "success": true,
  "code": "COMMON_200",
  "message": "요청에 성공했습니다.",
  "data": {
    "wishCount": 1
  }
}
```

에러 — WISHLIST_404

#### API-123 · `PATCH /api/v1/wishlists/{wishlistId}/alarm`

위시 알림 on/off. 인증 **필요**. 관련 화면 SCR-030.

요청 `{ alarmEnabled }`.

**요청 본문**

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|:--:|---|---|
| `alarmEnabled` | Boolean | O | — | — |

**응답** `data` 는 `null` 이다.

에러 — WISHLIST_404, COMMON_403

### 구매 제안

#### API-140 · `GET /api/v1/listings/{listingId}/offer-preview`

결제 금액 미리보기 (수수료·배송비 계산). 인증 **필요**. 관련 화면 SCR-032, SCR-033, SCR-034.

쿼리 `offerPrice?`(미판매 제안일 때). 응답 `{ itemPrice, shippingFee, feeRate, feeAmount, buyerFeeAmount, sellerFeeAmount, totalPaymentAmount, expectedPayoutAmount }`. **요율은 소유자 등급 기준**(BASIC 6% / RECORD 3%)이고 상품 금액에만 붙는다. 반반 부담이면 홀수 1원을 판매자가 낸다.

**쿼리 파라미터**

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|:--:|---|---|
| `offerPrice` | String |  | 미판매 제안일 때만 | 제안 금액 |

**응답** (`data`)

| 필드 | 타입 | 설명 |
|---|---|---|
| `itemPrice` | String | 상품 금액 |
| `shippingFee` | String | 배송비 |
| `feeRate` | BigDecimal | 0.06 \| 0.03 |
| `feeAmount` | String | 총 수수료 |
| `buyerFeeAmount` | String | 구매자 부담 |
| `sellerFeeAmount` | String | 판매자 부담 — 홀수 1원 |
| `totalPaymentAmount` | String | 총 결제 금액 |
| `expectedPayoutAmount` | String | 총 정산 예정 금액 |

```json
{
  "success": true,
  "code": "COMMON_200",
  "message": "요청에 성공했습니다.",
  "data": {
    "itemPrice": "360000",
    "shippingFee": "360000",
    "feeRate": "0.5",
    "feeAmount": "360000",
    "buyerFeeAmount": "360000",
    "sellerFeeAmount": "360000",
    "totalPaymentAmount": "360000",
    "expectedPayoutAmount": "360000"
  }
}
```

에러 — LISTING_404

#### API-141 · `POST /api/v1/listings/{listingId}/offers`

구매 제안 / 거래 의사 전송 (결제 승인 포함). 인증 **필요**. 관련 화면 SCR-032, SCR-033, SCR-034.

요청 `OfferCreateRequest` — `offerType` `offerPrice`(미판매만) `shippingAddressId` `buyerRequest?` `paymentMethod`. 전송 시점에 **결제 승인을 임시 확보**하고 24시간 만료를 건다. 미판매 아이템은 아이템당 1회만 가능하며, 자동 취소(`OTHER_OFFER_ACCEPTED`)된 이력은 횟수에서 제외한다.

**요청 본문**

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|:--:|---|---|
| `offerType` | OfferType | O | PURCHASE_OFFER \| TRADE_INTENT | — |
| `offerPrice` | String |  | 미판매만 | 제안 금액 |
| `shippingAddressId` | Long | O | — | 배송지 |
| `buyerRequest` | String |  | 500자 이내 | 거래 요청사항 |
| `paymentMethod` | PaymentMethod | O | TOSS_PAY \| KAKAO_PAY \| NAVER_PAY | — |

**응답** (`data`)

| 필드 | 타입 | 설명 |
|---|---|---|
| `offerId` | Long | — |
| `expiresAt` | LocalDateTime | 생성 +24h |
| `totalPaymentAmount` | String | — |

```json
{
  "success": true,
  "code": "COMMON_200",
  "message": "요청에 성공했습니다.",
  "data": {
    "offerId": 1,
    "expiresAt": "2026-06-21T18:30:00",
    "totalPaymentAmount": "360000"
  }
}
```

에러 — OFFER_409_ALREADY_USED, OFFER_400_NOT_ALLOWED, OFFER_403_BLOCKED, OFFER_400_SELF, OFFER_400_ADDRESS_REQUIRED, PAYMENT_402, LISTING_409_SOLD

#### API-142 · `GET /api/v1/offers/{offerId}`

제안서 상세 (구매자·판매자 시점 분기). 인증 **필요**. 관련 화면 SCR-035, SCR-036.

구매자·판매자에 따라 응답이 다르다. 구매자는 `결제 정보`(총 결제 금액), 판매자는 `정산 정보`(총 정산 예정 금액)를 본다. 공통으로 `expiresAt` 잔여 시간을 내려준다.

**경로 파라미터**

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|:--:|---|---|
| `offerId` | Long | O | — | — |

**응답** (`data`)

| 필드 | 타입 | 설명 |
|---|---|---|
| `id` | Long | — |
| `offerType` | OfferType | — |
| `status` | OfferStatus | PENDING \| ACCEPTED \| REJECTED \| CANCELED \| EXPIRED |
| `expiresAt` | LocalDateTime | 제안 만료까지 |
| `item.brandName` | String | — |
| `item.name` | String | — |
| `item.coverImageUrl` | String | — |
| `offerPrice` | String | 제안 금액 |
| `shippingFee` | String | — |
| `feeAmount` | String | 수수료 |
| `totalPaymentAmount` | String | 구매자 시점 — 총 결제 금액 |
| `expectedPayoutAmount` | String | 판매자 시점 — 총 정산 예정 금액 |
| `shipping.recipientName` | String | 받는 분 |
| `shipping.phone` | String | — |
| `shipping.address` | String | — |
| `shipping.addressDetail` | String | — |
| `buyerRequest` | String | 구매자 요청사항 |
| `payoutAccount` | String | 판매자 시점 — 정산 계좌 |
| `counterpart.nickname` | String | 상대 닉네임 |
| `paymentMethod` | PaymentMethod | 구매자 시점 |

```json
{
  "success": true,
  "code": "COMMON_200",
  "message": "요청에 성공했습니다.",
  "data": {
    "id": 1,
    "offerType": "<OfferType>",
    "status": "<OfferStatus>",
    "expiresAt": "2026-06-21T18:30:00",
    "item": {
      "brandName": "<String>",
      "name": "<String>",
      "coverImageUrl": "https://cdn.tenure.app/post/2026/06/ab12.jpg"
    },
    "offerPrice": "360000",
    "shippingFee": "360000",
    "feeAmount": "360000",
    "totalPaymentAmount": "360000",
    "expectedPayoutAmount": "360000",
    "shipping": {
      "recipientName": "<String>",
      "phone": "<String>",
      "address": "<String>",
      "addressDetail": "<String>"
    },
    "buyerRequest": "<String>",
    "payoutAccount": "<String>",
    "counterpart": {
      "nickname": "<String>"
    },
    "paymentMethod": "<PaymentMethod>"
  }
}
```

에러 — OFFER_404, OFFER_403

#### API-143 · `DELETE /api/v1/offers/{offerId}`

제안 취소 (구매자). 인증 **필요**. 관련 화면 SCR-035.

`CANCELED` + `BUYER_CANCELED` 로 전이하고 결제 승인을 해제한다. **제안 기회는 복구되지 않는다.**

**경로 파라미터**

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|:--:|---|---|
| `offerId` | Long | O | — | — |

**응답** `data` 는 `null` 이다.

에러 — OFFER_403, OFFER_409_NOT_PENDING, OFFER_410_EXPIRED

#### API-144 · `POST /api/v1/offers/{offerId}/accept`

제안 수락 (판매자) → 거래 생성. 인증 **필요**. 관련 화면 SCR-036.

결제를 확정(`captured`)하고 `trade_order` 를 생성한다. 동시에 **같은 아이템의 나머지 대기 제안을 전부 자동 취소**(`OTHER_OFFER_ACCEPTED`)하고 각 승인을 해제한 뒤 알림을 보낸다. 자동 취소된 제안자의 1회 기회는 복구된다.

**경로 파라미터**

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|:--:|---|---|
| `offerId` | Long | O | — | — |

**응답** (`data`)

| 필드 | 타입 | 설명 |
|---|---|---|
| `orderId` | Long | 생성된 거래 |
| `orderNo` | String | — |
| `autoCanceledCount` | Integer | 함께 취소된 다른 제안 수 |

```json
{
  "success": true,
  "code": "COMMON_200",
  "message": "요청에 성공했습니다.",
  "data": {
    "orderId": 1,
    "orderNo": "<String>",
    "autoCanceledCount": 1
  }
}
```

에러 — OFFER_403, OFFER_409_NOT_PENDING, OFFER_410_EXPIRED, PAYMENT_409_CAPTURE

#### API-145 · `POST /api/v1/offers/{offerId}/reject`

제안 거절 (판매자). 인증 **필요**. 관련 화면 SCR-036.

`REJECTED` 로 전이하고 승인을 해제한다. 거부한 제안은 다시 수락할 수 없다.

**경로 파라미터**

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|:--:|---|---|
| `offerId` | Long | O | — | — |

**응답** `data` 는 `null` 이다.

에러 — OFFER_403, OFFER_409_NOT_PENDING

### 거래

#### API-160 · `GET /api/v1/orders/{orderId}`

거래 상세 (타임라인·배송·결제·정산). 인증 **필요**. 관련 화면 SCR-037.

응답 `OrderResponse` — `orderNo` · `status` · `statusHistory[{ status, changedAt }]` · `shipment` · `payment` · `settlement` · `buyer` · `seller` · `item`. **판매자/구매자 × 판매/미판매 4가지 뷰**로 노출 필드가 갈린다. `autoConfirmAt` 잔여 시간을 함께 내려준다.

**경로 파라미터**

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|:--:|---|---|
| `orderId` | Long | O | — | — |

**응답** (`data`)

| 필드 | 타입 | 설명 |
|---|---|---|
| `id` | Long | — |
| `orderNo` | String | 주문 번호 |
| `status` | OrderStatus | PAYMENT_COMPLETED \| SHIPPED \| DELIVERED \| PURCHASE_CONFIRMED \| SETTLED \| COMPLETED \| CANCELED |
| `viewerRole` | String | BUYER \| SELLER — 노출 필드가 갈린다 |
| `item.brandName` | String | — |
| `item.name` | String | — |
| `item.coverImageUrl` | String | — |
| `statusHistory[].status` | OrderStatus | — |
| `statusHistory[].changedAt` | LocalDateTime | 타임라인 날짜 |
| `shipment.courierCode` | CourierCode | CJ_LOGISTICS \| KOREA_POST \| GS_POSTBOX \| CU_CONVENIENCE |
| `shipment.trackingNo` | String | — |
| `shipment.recipientName` | String | — |
| `shipment.phone` | String | — |
| `shipment.address` | String | — |
| `shipment.addressDetail` | String | — |
| `shipment.deliveryRequest` | String | 요청사항 |
| `itemPrice` | String | 상품 금액 |
| `shippingFee` | String | 배송비 |
| `feeAmount` | String | 수수료 |
| `totalPaymentAmount` | String | 구매자 — 결제 금액 |
| `payoutAmount` | String | 판매자 — 총 정산 금액 |
| `payment.method` | PaymentMethod | — |
| `payment.status` | PaymentStatus | AUTHORIZED \| CAPTURED \| RELEASED \| REFUNDED |
| `settlement.accountNumber` | String | 정산 계좌 |
| `settlement.settledAt` | LocalDateTime | — |
| `autoConfirmAt` | LocalDateTime | '자동 구매 확정까지' |
| `counterpart.nickname` | String | — |

```json
{
  "success": true,
  "code": "COMMON_200",
  "message": "요청에 성공했습니다.",
  "data": {
    "id": 1,
    "orderNo": "<String>",
    "status": "<OrderStatus>",
    "viewerRole": "<String>",
    "item": {
      "brandName": "<String>",
      "name": "<String>",
      "coverImageUrl": "https://cdn.tenure.app/post/2026/06/ab12.jpg"
    },
    "statusHistory": [
      {
        "status": "<OrderStatus>",
        "changedAt": "2026-06-21T18:30:00"
      }
    ],
    "shipment": {
      "courierCode": "<CourierCode>",
      "trackingNo": "<String>",
      "recipientName": "<String>",
      "phone": "<String>",
      "address": "<String>",
      "addressDetail": "<String>",
      "deliveryRequest": "<String>"
    },
    "itemPrice": "360000",
    "shippingFee": "360000",
    "feeAmount": "360000",
    "totalPaymentAmount": "360000",
    "payoutAmount": "360000",
    "payment": {
      "method": "<PaymentMethod>",
      "status": "<PaymentStatus>"
    },
    "settlement": {
      "accountNumber": "<String>",
      "settledAt": "2026-06-21T18:30:00"
    },
    "autoConfirmAt": "2026-06-21T18:30:00",
    "counterpart": {
      "nickname": "<String>"
    }
  }
}
```

에러 — ORDER_404, ORDER_403

#### API-161 · `GET /api/v1/orders/sales`

판매 내역 (전체/제안받음/거래중/거래완료). 인증 **필요**. 관련 화면 SCR-039.

쿼리 `tab=ALL|OFFERED|IN_PROGRESS|DONE` + 페이징. 정렬 `createdAt,desc`.

**화면의 탭 4개를 이 엔드포인트 하나가 전부 담당한다.** 판매 내역에는 아직 거래가 아닌 `제안받음`과
이미 거래가 된 `거래중`·`거래완료`가 **한 화면에 섞여** 있고 `전체` 탭은 둘을 시간순으로 함께 보여준다.
목록을 둘로 쪼개면 `전체` 탭에서 두 응답을 클라이언트가 합쳐야 하고 그 순간 페이징이 깨진다.
그래서 행을 `rowType` 으로 구분하는 **유니온**으로 내려준다.

| tab | 담는 것 |
|---|---|
| `ALL` | `OFFER`(PENDING) + `ORDER` 전부 — `createdAt` 내림차순으로 섞는다 |
| `OFFERED` | `OFFER` 중 `PENDING` 만 |
| `IN_PROGRESS` | `ORDER` 중 `PAYMENT_COMPLETED` ~ `SETTLED` |
| `DONE` | `ORDER` 중 `COMPLETED` · `CANCELED` |

`rowType=OFFER` 면 `offerId`·`expiresAt` 이, `rowType=ORDER` 면 `orderId`·`orderNo`·`payoutAmount` 가 채워진다.
반대쪽 필드는 `null` 이다.

**쿼리 파라미터**

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|:--:|---|---|
| `tab` | String |  | ALL \| OFFERED \| IN_PROGRESS \| DONE | 기본 ALL |
| `page` | Integer |  | — | — |
| `size` | Integer |  | — | — |

**응답** (`data` — PageResponse)

| 필드 | 타입 | 설명 |
|---|---|---|
| `rowType` | String | OFFER \| ORDER |
| `offerId` | Long | `OFFER` 행 — 제안서(`API-142`) 이동 키 |
| `offerType` | OfferType | `OFFER` 행 — 거래 의사 / 구매 제안 문구 분기 |
| `offerStatus` | OfferStatus | `OFFER` 행 — 목록은 PENDING 만 싣는다 |
| `offerPrice` | String | `OFFER` 행 — '360,000원 구매 제안' |
| `expiresAt` | LocalDateTime | `OFFER` 행 — '23시간 18분 남음' |
| `orderId` | Long | `ORDER` 행 — 거래 상세(`API-160`) 이동 키 |
| `orderNo` | String | `ORDER` 행 — 주문 번호 |
| `orderStatus` | OrderStatus | `ORDER` 행 — 거래 단계 |
| `payoutAmount` | String | `ORDER` 행 — 총 정산 금액 |
| `counterpart.nickname` | String | 구매자 닉네임 |
| `counterpart.profileImageUrl` | String | — |
| `item.brandName` | String | — |
| `item.name` | String | — |
| `item.coverImageUrl` | String | — |
| `createdAt` | LocalDateTime | '26.04.04' — 정렬 키 |

```json
{
  "success": true,
  "code": "COMMON_200",
  "message": "요청에 성공했습니다.",
  "data": {
    "content": [
      {
        "rowType": "OFFER",
        "offerId": 1,
        "offerType": "<OfferType>",
        "offerStatus": "PENDING",
        "offerPrice": "360000",
        "expiresAt": "2026-06-22T18:30:00",
        "orderId": null,
        "orderNo": null,
        "orderStatus": null,
        "payoutAmount": null,
        "counterpart": {
          "nickname": "<String>",
          "profileImageUrl": "https://cdn.tenure.app/post/2026/06/ab12.jpg"
        },
        "item": {
          "brandName": "<String>",
          "name": "<String>",
          "coverImageUrl": "https://cdn.tenure.app/item/2026/06/ab12.jpg"
        },
        "createdAt": "2026-06-21T18:30:00"
      },
      {
        "rowType": "ORDER",
        "offerId": null,
        "offerType": null,
        "offerStatus": null,
        "offerPrice": null,
        "expiresAt": null,
        "orderId": 1,
        "orderNo": "2026042919502395",
        "orderStatus": "<OrderStatus>",
        "payoutAmount": "365000",
        "counterpart": {
          "nickname": "<String>",
          "profileImageUrl": "https://cdn.tenure.app/post/2026/06/ab12.jpg"
        },
        "item": {
          "brandName": "<String>",
          "name": "<String>",
          "coverImageUrl": "https://cdn.tenure.app/item/2026/06/ab12.jpg"
        },
        "createdAt": "2026-06-20T10:00:00"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 137,
    "totalPages": 7,
    "hasNext": true
  }
}
```

에러 — COMMON_401

#### API-162 · `GET /api/v1/orders/purchases`

구매 내역 (전체/제안중/거래중/거래완료). 인증 **필요**. 관련 화면 SCR-040.

쿼리 `tab=ALL|OFFERING|IN_PROGRESS|DONE` + 페이징. 정렬 `createdAt,desc`.

`API-161` 과 대칭이다 — 같은 유니온 행이고 방향(`buyer_id` 기준)과 금액 필드만 다르다.
`OFFERING` 탭은 내가 보낸 `PENDING` 제안이고, 금액은 `payoutAmount` 대신 `totalPaymentAmount` 다.
`offerStatus` 는 여기서 **PENDING 외 값도 실린다** — 구매자는 자기 제안이 거절·만료됐다는 사실을
`ALL` 탭에서 확인해야 하기 때문이다(`제안 기회는 복구되지 않아요`).

| tab | 담는 것 |
|---|---|
| `ALL` | `OFFER` 전부(상태 무관) + `ORDER` 전부 — `createdAt` 내림차순 |
| `OFFERING` | `OFFER` 중 `PENDING` 만 |
| `IN_PROGRESS` | `ORDER` 중 `PAYMENT_COMPLETED` ~ `SETTLED` |
| `DONE` | `ORDER` 중 `COMPLETED` · `CANCELED` |

**쿼리 파라미터**

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|:--:|---|---|
| `tab` | String |  | ALL \| OFFERING \| IN_PROGRESS \| DONE | 기본 ALL |
| `page` | Integer |  | — | — |
| `size` | Integer |  | — | — |

**응답** (`data` — PageResponse)

| 필드 | 타입 | 설명 |
|---|---|---|
| `rowType` | String | OFFER \| ORDER |
| `offerId` | Long | `OFFER` 행 — 제안서(`API-142`) 이동 키 |
| `offerType` | OfferType | `OFFER` 행 |
| `offerStatus` | OfferStatus | `OFFER` 행 — PENDING 외 값도 실린다 |
| `offerPrice` | String | `OFFER` 행 |
| `expiresAt` | LocalDateTime | `OFFER` 행 — 제안 만료까지 |
| `orderId` | Long | `ORDER` 행 — 거래 상세(`API-160`) 이동 키 |
| `orderNo` | String | `ORDER` 행 |
| `orderStatus` | OrderStatus | `ORDER` 행 |
| `totalPaymentAmount` | String | `ORDER` 행 — 총 결제 금액 |
| `counterpart.nickname` | String | 판매자 닉네임 |
| `counterpart.profileImageUrl` | String | — |
| `item.brandName` | String | — |
| `item.name` | String | — |
| `item.coverImageUrl` | String | — |
| `createdAt` | LocalDateTime | 정렬 키 |

```json
{
  "success": true,
  "code": "COMMON_200",
  "message": "요청에 성공했습니다.",
  "data": {
    "content": [
      {
        "rowType": "ORDER",
        "offerId": null,
        "offerType": null,
        "offerStatus": null,
        "offerPrice": null,
        "expiresAt": null,
        "orderId": 1,
        "orderNo": "2026042919502395",
        "orderStatus": "<OrderStatus>",
        "totalPaymentAmount": "377000",
        "counterpart": {
          "nickname": "<String>",
          "profileImageUrl": "https://cdn.tenure.app/post/2026/06/ab12.jpg"
        },
        "item": {
          "brandName": "<String>",
          "name": "<String>",
          "coverImageUrl": "https://cdn.tenure.app/item/2026/06/ab12.jpg"
        },
        "createdAt": "2026-06-21T18:30:00"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 137,
    "totalPages": 7,
    "hasNext": true
  }
}
```

에러 — COMMON_401

#### API-163 · `PATCH /api/v1/orders/{orderId}/shipment`

운송장 번호 입력 → 상품 발송. 인증 **필요**. 관련 화면 SCR-038.

요청 `{ courierCode, trackingNo }`. `CJ_LOGISTICS` · `KOREA_POST` · `GS_POSTBOX` · `CU_CONVENIENCE` 4종. 성공 시 `SHIPPED` 로 전이한다. **배송 완료(`DELIVERED`) 전까지 재입력할 수 있다** — 이전 번호는 남기지 않는다(`DEC-008`).

**요청 본문**

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|:--:|---|---|
| `courierCode` | CourierCode | O | CJ_LOGISTICS \| KOREA_POST \| GS_POSTBOX \| CU_CONVENIENCE | — |
| `trackingNo` | String | O | 숫자만 | DELIVERED 전까지 재입력 가능 |

**응답** (`data`)

| 필드 | 타입 | 설명 |
|---|---|---|
| `status` | OrderStatus | SHIPPED 로 전이 |

```json
{
  "success": true,
  "code": "COMMON_200",
  "message": "요청에 성공했습니다.",
  "data": {
    "status": "<OrderStatus>"
  }
}
```

에러 — ORDER_403, ORDER_409_STATUS, ORDER_400_TRACKING, ORDER_404_TRACKING

#### API-164 · `POST /api/v1/orders/{orderId}/deliver`

배송 완료 처리. 인증 **필요**. 관련 화면 SCR-037.

판매자가 배송 완료를 확인한다. `DELIVERED` 로 전이하고 `autoConfirmAt = now + 72h` 를 건다. 되돌릴 수 없다.

**경로 파라미터**

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|:--:|---|---|
| `orderId` | Long | O | — | — |

**응답** (`data`)

| 필드 | 타입 | 설명 |
|---|---|---|
| `status` | OrderStatus | DELIVERED |
| `autoConfirmAt` | LocalDateTime | +72h |

```json
{
  "success": true,
  "code": "COMMON_200",
  "message": "요청에 성공했습니다.",
  "data": {
    "status": "<OrderStatus>",
    "autoConfirmAt": "2026-06-21T18:30:00"
  }
}
```

에러 — ORDER_403, ORDER_409_STATUS

#### API-165 · `POST /api/v1/orders/{orderId}/confirm`

구매 확정 → 소유권 승계. 인증 **필요**. 관련 화면 SCR-037.

구매자 확정 또는 72시간 경과 시 배치가 호출한다. `PURCHASE_CONFIRMED` 로 전이하고 **소유권을 승계**한다 — 이전 `item_ownership` 의 `end_date` 를 닫고 구매자 구간을 `acquired_by=TRADE` 로 연다. 이전 소유자의 착용 기록은 그대로 남는다. 정산 예정일은 확정일 +2영업일이다.

**경로 파라미터**

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|:--:|---|---|
| `orderId` | Long | O | — | — |

**응답** (`data`)

| 필드 | 타입 | 설명 |
|---|---|---|
| `status` | OrderStatus | PURCHASE_CONFIRMED |
| `settlementScheduledAt` | LocalDate | 확정일 +2영업일 |

```json
{
  "success": true,
  "code": "COMMON_200",
  "message": "요청에 성공했습니다.",
  "data": {
    "status": "<OrderStatus>",
    "settlementScheduledAt": "2026-06-21"
  }
}
```

에러 — ORDER_403, ORDER_409_STATUS

#### API-166 · `POST /api/v1/orders/{orderId}/cancel`

거래 취소 (상품 발송 전) → 환불. 인증 **필요**. 관련 화면 SCR-037.

**상품 발송 전(`PAYMENT_COMPLETED`)까지만** 가능하다. 최종 결제를 환불하고 `payment.status=REFUNDED` · `refunded_at` 을 세팅한 뒤 `CANCELED` 로 전이한다.

**경로 파라미터**

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|:--:|---|---|
| `orderId` | Long | O | — | — |

**응답** (`data`)

| 필드 | 타입 | 설명 |
|---|---|---|
| `status` | OrderStatus | CANCELED |
| `refundedAt` | LocalDateTime | — |

```json
{
  "success": true,
  "code": "COMMON_200",
  "message": "요청에 성공했습니다.",
  "data": {
    "status": "<OrderStatus>",
    "refundedAt": "2026-06-21T18:30:00"
  }
}
```

에러 — ORDER_403, ORDER_409_CANCEL_WINDOW, PAYMENT_409_REFUND

### 채팅

#### API-180 · `GET /api/v1/chat-rooms`

채팅방 목록 (전체/구매/판매/읽지않음). 인증 **필요**. 관련 화면 SCR-041.

쿼리 `tab=ALL|BUYING|SELLING|UNREAD` + 페이징. 정렬 `lastMessageAt,desc`. 항목마다 `unreadCount` 와 아이템 요약(`최근 착용 N일 전` 포함)을 내려준다.

**쿼리 파라미터**

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|:--:|---|---|
| `tab` | String |  | ALL \| BUYING \| SELLING \| UNREAD | — |
| `page` | Integer |  | — | — |
| `size` | Integer |  | — | — |

**응답** (`data` — PageResponse)

| 필드 | 타입 | 설명 |
|---|---|---|
| `roomId` | Long | — |
| `counterpart.nickname` | String | — |
| `counterpart.profileImageUrl` | String | — |
| `lastMessage` | String | 미리보기 |
| `lastMessageAt` | LocalDateTime | '6월 27일' |
| `unreadCount` | Integer | 배지 |
| `item.brandName` | String | 'Levis / LVC 19…' |
| `item.name` | String | — |
| `item.coverImageUrl` | String | 목록 썸네일 |
| `recentWornDate` | LocalDate | '최근 착용 1일 전' |

```json
{
  "success": true,
  "code": "COMMON_200",
  "message": "요청에 성공했습니다.",
  "data": {
    "content": [
      {
        "roomId": 1,
        "counterpart": {
          "nickname": "<String>",
          "profileImageUrl": "https://cdn.tenure.app/post/2026/06/ab12.jpg"
        },
        "lastMessage": "<String>",
        "lastMessageAt": "2026-06-21T18:30:00",
        "unreadCount": 1,
        "item": {
          "brandName": "<String>",
          "name": "<String>",
          "coverImageUrl": "https://cdn.tenure.app/item/2026/06/ab12.jpg"
        },
        "recentWornDate": "2026-06-21"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 137,
    "totalPages": 7,
    "hasNext": true
  }
}
```

에러 — COMMON_401

#### API-181 · `POST /api/v1/items/{itemId}/chat-rooms`

채팅방 개설. 인증 **필요**. 관련 화면 SCR-026.

`미판매 + 구매제안 off` 상품은 개설이 막힌다. 이미 있으면 기존 방을 돌려준다(멱등).

**경로 파라미터**

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|:--:|---|---|
| `itemId` | Long | O | — | — |

**응답** (`data`)

| 필드 | 타입 | 설명 |
|---|---|---|
| `roomId` | Long | 이미 있으면 기존 방 |

```json
{
  "success": true,
  "code": "COMMON_200",
  "message": "요청에 성공했습니다.",
  "data": {
    "roomId": 1
  }
}
```

에러 — CHAT_400_NOT_OPENABLE, CHAT_400_SELF, CHAT_403_BLOCKED, ITEM_404

#### API-182 · `GET /api/v1/chat-rooms/{roomId}`

채팅방 헤더 (아이템·거래 단계 CTA). 인증 **필요**. 관련 화면 SCR-042.

응답에 아이템·가격·판매 상태와 함께 **거래 단계별 CTA** 를 내려준다 — `응답 대기 중` / `거래 진행 중` / `거래 완료 이후` 3종이며 구매자·판매자에 따라 또 갈린다. 상태는 `purchase_offer`·`trade_order` 에서 파생한다.

**경로 파라미터**

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|:--:|---|---|
| `roomId` | Long | O | — | — |

**응답** (`data`)

| 필드 | 타입 | 설명 |
|---|---|---|
| `roomId` | Long | — |
| `counterpart.nickname` | String | — |
| `item.brandName` | String | — |
| `item.name` | String | — |
| `item.coverImageUrl` | String | — |
| `price` | String | — |
| `saleStatus` | SaleStatus | '판매 중' |
| `recentWornDate` | LocalDate | '최근 착용 1일 전' |
| `cta` | String | 거래 단계별 CTA — 상품 상세 / 구매하기 / 거래 상세 |
| `orderId` | Long | 진행 중 거래가 있으면 |

```json
{
  "success": true,
  "code": "COMMON_200",
  "message": "요청에 성공했습니다.",
  "data": {
    "roomId": 1,
    "counterpart": {
      "nickname": "<String>"
    },
    "item": {
      "brandName": "<String>",
      "name": "<String>",
      "coverImageUrl": "https://cdn.tenure.app/post/2026/06/ab12.jpg"
    },
    "price": "360000",
    "saleStatus": "<SaleStatus>",
    "recentWornDate": "2026-06-21",
    "cta": "<String>",
    "orderId": 1
  }
}
```

에러 — CHAT_404_ROOM, CHAT_403

#### API-183 · `GET /api/v1/chat-rooms/{roomId}/messages`

메시지 목록 (커서 페이징). 인증 **필요**. 관련 화면 SCR-042.

커서 페이징 (`cursor` = 마지막 메시지 id, `size`). 오래된 순으로 거슬러 올라간다.

**쿼리 파라미터**

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|:--:|---|---|
| `cursor` | Long |  | 마지막 메시지 id | 커서 페이징 |
| `size` | Integer |  | 기본 30 | — |

**응답** (`data` 배열)

| 필드 | 타입 | 설명 |
|---|---|---|
| `id` | Long | — |
| `senderId` | Long | — |
| `messageType` | ChatMessageType | TEXT \| IMAGE |
| `content` | String | IMAGE 면 URL 로 변환해 내려준다 |
| `createdAt` | LocalDateTime | '오후 6:30' |

```json
{
  "success": true,
  "code": "COMMON_200",
  "message": "요청에 성공했습니다.",
  "data": [
    {
      "id": 1,
      "senderId": 1,
      "messageType": "<ChatMessageType>",
      "content": "<String>",
      "createdAt": "2026-06-21T18:30:00"
    }
  ]
}
```

에러 — CHAT_404_ROOM, CHAT_403

#### API-184 · `POST /api/v1/chat-rooms/{roomId}/messages`

메시지 전송 (텍스트 / 이미지). 인증 **필요**. 관련 화면 SCR-042.

요청 `{ messageType: TEXT|IMAGE, content }`. `IMAGE` 면 `content` 에 API-060 이 돌려준 저장소 키를 넣는다. 전송과 같은 트랜잭션에서 `chat_room.last_message_at` 을 갱신한다.

**요청 본문**

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|:--:|---|---|
| `messageType` | ChatMessageType | O | TEXT \| IMAGE | — |
| `content` | String | O | IMAGE 면 API-060 의 키 | — |

**응답** (`data`)

| 필드 | 타입 | 설명 |
|---|---|---|
| `id` | Long | — |
| `createdAt` | LocalDateTime | — |

```json
{
  "success": true,
  "code": "COMMON_200",
  "message": "요청에 성공했습니다.",
  "data": {
    "id": 1,
    "createdAt": "2026-06-21T18:30:00"
  }
}
```

에러 — CHAT_403, CHAT_403_BLOCKED

#### API-185 · `PATCH /api/v1/chat-rooms/{roomId}/read`

읽음 처리. 인증 **필요**. 관련 화면 SCR-042.

요청 `{ lastReadMessageId }`.

**요청 본문**

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|:--:|---|---|
| `lastReadMessageId` | Long | O | — | — |

**응답** `data` 는 `null` 이다.

에러 — CHAT_403

#### API-186 · `DELETE /api/v1/chat-rooms/{roomId}/me`

채팅방 나가기. 인증 **필요**. 관련 화면 SCR-043.

`left_at` 을 세팅한다. 방과 메시지는 남는다.

**경로 파라미터**

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|:--:|---|---|
| `roomId` | Long | O | — | — |

**응답** `data` 는 `null` 이다.

에러 — CHAT_403

### 알림

#### API-190 · `GET /api/v1/notifications`

알림 목록 (탭 5종). 인증 **필요**. 관련 화면 SCR-044.

쿼리 `category=ALL|ACTION_REQUIRED|ITEM_NEWS|TRADE_STATUS|WISHLIST` + 페이징. 정렬 `createdAt,desc`. 대상이 삭제된 알림은 딥링크 진입 시 `삭제된 게시물입니다` 를 표시한다.

**쿼리 파라미터**

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|:--:|---|---|
| `category` | NotificationCategory |  | ACTION_REQUIRED \| ITEM_NEWS \| TRADE_STATUS \| WISHLIST | 미지정 시 전체 |
| `page` | Integer |  | — | — |
| `size` | Integer |  | — | — |

**응답** (`data` — PageResponse)

| 필드 | 타입 | 설명 |
|---|---|---|
| `id` | Long | — |
| `category` | NotificationCategory | — |
| `title` | String | 'GilDong 님이 구매 제안을 보냈습니다!' |
| `body` | String | — |
| `targetType` | NotificationTargetType | POST \| ITEM \| ORDER \| OFFER \| CHAT_ROOM |
| `targetId` | Long | — |
| `targetDeleted` | Boolean | true 면 '삭제된 게시물입니다' |
| `readAt` | LocalDateTime | null 이면 미읽음 |
| `createdAt` | LocalDateTime | '어제', '4:28 pm' |

```json
{
  "success": true,
  "code": "COMMON_200",
  "message": "요청에 성공했습니다.",
  "data": {
    "content": [
      {
        "id": 1,
        "category": "<NotificationCategory>",
        "title": "<String>",
        "body": "<String>",
        "targetType": "<NotificationTargetType>",
        "targetId": 1,
        "targetDeleted": true,
        "readAt": "2026-06-21T18:30:00",
        "createdAt": "2026-06-21T18:30:00"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 137,
    "totalPages": 7,
    "hasNext": true
  }
}
```

에러 — COMMON_401

#### API-191 · `PATCH /api/v1/notifications/{notificationId}/read`

알림 읽음. 인증 **필요**. 관련 화면 SCR-044.

**경로 파라미터**

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|:--:|---|---|
| `notificationId` | Long | O | — | — |

**응답** `data` 는 `null` 이다.

에러 — NOTIFICATION_404, NOTIFICATION_403

#### API-192 · `PATCH /api/v1/notifications/read-all`

전체 읽음. 인증 **필요**. 관련 화면 SCR-044.

쿼리 `category?` — 특정 탭만 읽음 처리할 수 있다.

**쿼리 파라미터**

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|:--:|---|---|
| `category` | NotificationCategory |  | — | 특정 탭만 읽음 처리 |

**응답** (`data`)

| 필드 | 타입 | 설명 |
|---|---|---|
| `updatedCount` | Integer | — |

```json
{
  "success": true,
  "code": "COMMON_200",
  "message": "요청에 성공했습니다.",
  "data": {
    "updatedCount": 1
  }
}
```

에러 — COMMON_401

### 검색

#### API-200 · `GET /api/v1/search/posts`

게시물 검색 (필터 적용). 인증 **필요**. 관련 화면 SCR-046, SCR-047.

쿼리 `keyword?` `saleStatus=ALL|INCLUDE_ON_SALE|ONLY_ON_SALE` `authorGender=ALL|MALE|FEMALE` `categoryIds[]` `heightMin` `heightMax` `weightMin` `weightMax` + 페이징. 판매 상태는 **3모드**이고 키·몸무게는 범위 슬라이더(0~200cm / 0~150kg)다. 검색 시 `search_history` 에 기록한다.

**쿼리 파라미터**

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|:--:|---|---|
| `keyword` | String |  | — | — |
| `saleStatus` | String |  | ALL \| INCLUDE_ON_SALE \| ONLY_ON_SALE | 판매 상태 3모드 |
| `authorGender` | String |  | ALL \| MALE \| FEMALE | 게시자 성별 |
| `categoryIds` | Long[] |  | — | 세부 카테고리 |
| `heightMin` | Integer |  | 0~200 | — |
| `heightMax` | Integer |  | 0~200 | — |
| `weightMin` | Integer |  | 0~150 | — |
| `weightMax` | Integer |  | 0~150 | — |
| `page` | Integer |  | — | — |
| `size` | Integer |  | — | — |

**응답** (`data` — PageResponse)

| 필드 | 타입 | 설명 |
|---|---|---|
| `id` | Long | — |
| `imageUrl` | String | 그리드 썸네일 |
| `aspectRatio` | AspectRatio | — |

```json
{
  "success": true,
  "code": "COMMON_200",
  "message": "요청에 성공했습니다.",
  "data": {
    "content": [
      {
        "id": 1,
        "imageUrl": "https://cdn.tenure.app/post/2026/06/ab12.jpg",
        "aspectRatio": "<AspectRatio>"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 137,
    "totalPages": 7,
    "hasNext": true
  }
}
```

에러 — COMMON_401

#### API-201 · `GET /api/v1/search/users`

계정 검색. 인증 **필요**. 관련 화면 SCR-046.

쿼리 `keyword` + 페이징. 응답에 `followerCount` · `postCount` 를 포함한다 — 목록 반복 노출이라 카운터 컬럼을 읽는다.
**부수효과** — `API-200` 과 동일하게 `search_history` 에 `keyword` 를 기록한다. 탭을 옮겨 검색해도 `최근 검색어`(`API-203`)에 남아야 하므로 두 엔드포인트가 같게 동작한다.

**쿼리 파라미터**

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|:--:|---|---|
| `keyword` | String | O | — | — |
| `page` | Integer |  | — | — |
| `size` | Integer |  | — | — |

**응답** (`data` — PageResponse)

| 필드 | 타입 | 설명 |
|---|---|---|
| `id` | Long | — |
| `nickname` | String | — |
| `profileImageUrl` | String | — |
| `followerCount` | Integer | '팔로우 101명' |
| `postCount` | Integer | '게시물 60개' |
| `isFollowing` | Boolean | — |

```json
{
  "success": true,
  "code": "COMMON_200",
  "message": "요청에 성공했습니다.",
  "data": {
    "content": [
      {
        "id": 1,
        "nickname": "<String>",
        "profileImageUrl": "https://cdn.tenure.app/post/2026/06/ab12.jpg",
        "followerCount": 1,
        "postCount": 1,
        "isFollowing": true
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 137,
    "totalPages": 7,
    "hasNext": true
  }
}
```

에러 — COMMON_401

#### API-202 · `GET /api/v1/search/recommendations`

추천 섹션 (유사·인기·최신·인기 사용자). 인증 **필요**. 관련 화면 SCR-045.

응답 `{ similarPosts[], popularPosts[], latestPosts[], popularUsers[] }` — `방금 보신 OOTD와 유사한 게시물` · `지금 인기 있는 스타일` · `새로 올라온 OOTD` · `인기 사용자`. 유사 추천의 입력은 Redis 의 `recent-view:{userId}:POST` 다(`DEC-014`).

**응답** (`data`)

| 필드 | 타입 | 설명 |
|---|---|---|
| `similarPosts[].id` | Long | '방금 보신 OOTD와 유사한 게시물' |
| `similarPosts[].imageUrl` | String | — |
| `popularPosts[].id` | Long | '지금 인기 있는 스타일' |
| `popularPosts[].imageUrl` | String | — |
| `latestPosts[].id` | Long | '새로 올라온 OOTD' |
| `latestPosts[].imageUrl` | String | — |
| `popularUsers[].id` | Long | '인기 사용자' |
| `popularUsers[].nickname` | String | — |
| `popularUsers[].profileImageUrl` | String | — |
| `popularUsers[].followerCount` | Integer | '팔로워 2,000명' |
| `popularUsers[].postCount` | Integer | 'OOTD 289개' |

```json
{
  "success": true,
  "code": "COMMON_200",
  "message": "요청에 성공했습니다.",
  "data": {
    "similarPosts": [
      {
        "id": 1,
        "imageUrl": "https://cdn.tenure.app/post/2026/06/ab12.jpg"
      }
    ],
    "popularPosts": [
      {
        "id": 1,
        "imageUrl": "https://cdn.tenure.app/post/2026/06/ab12.jpg"
      }
    ],
    "latestPosts": [
      {
        "id": 1,
        "imageUrl": "https://cdn.tenure.app/post/2026/06/ab12.jpg"
      }
    ],
    "popularUsers": [
      {
        "id": 1,
        "nickname": "<String>",
        "profileImageUrl": "https://cdn.tenure.app/post/2026/06/ab12.jpg",
        "followerCount": 1,
        "postCount": 1
      }
    ]
  }
}
```

에러 — COMMON_401

#### API-203 · `GET /api/v1/search/histories`

최근 검색어 + 최근 본 사용자. 인증 **필요**. 관련 화면 SCR-045.

응답 `{ keywords[{ id, keyword, searchedAt }], recentUsers[{ id, nickname, profileImageUrl }] }`.

**응답** (`data`)

| 필드 | 타입 | 설명 |
|---|---|---|
| `keywords[].id` | Long | — |
| `keywords[].keyword` | String | — |
| `keywords[].searchedAt` | LocalDateTime | — |
| `recentUsers[].id` | Long | '최근 본 사용자' |
| `recentUsers[].nickname` | String | — |
| `recentUsers[].profileImageUrl` | String | — |

```json
{
  "success": true,
  "code": "COMMON_200",
  "message": "요청에 성공했습니다.",
  "data": {
    "keywords": [
      {
        "id": 1,
        "keyword": "<String>",
        "searchedAt": "2026-06-21T18:30:00"
      }
    ],
    "recentUsers": [
      {
        "id": 1,
        "nickname": "<String>",
        "profileImageUrl": "https://cdn.tenure.app/post/2026/06/ab12.jpg"
      }
    ]
  }
}
```

에러 — COMMON_401

#### API-204 · `DELETE /api/v1/search/histories/{historyId}`

최근 검색어 개별 삭제. 인증 **필요**. 관련 화면 SCR-045.

**경로 파라미터**

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|:--:|---|---|
| `historyId` | Long | O | — | — |

**응답** `data` 는 `null` 이다.

에러 — SEARCH_404_HISTORY, COMMON_403

#### API-205 · `DELETE /api/v1/search/histories`

최근 검색어 전체 삭제. 인증 **필요**. 관련 화면 SCR-045.

**응답** `data` 는 `null` 이다.

에러 — COMMON_401
## 7. 미확정 항목

`schema.yaml` 의 `open_questions` 중 이 명세에 영향을 주는 것이다.

| ID | 내용 | 영향받는 API |
|---|---|---|
| **Q6** | `category.size_scheme` 값 목록 미확정 | `API-090` 이 `sizeScheme` 을 `null` 로 내려준다. `API-092`·`API-094` 의 `sizeLabel` 검증 규칙도 함께 미정이다 |

### 이번 점검에서 확정한 것

판단의 정본은 `schema.yaml` 의 `design_decisions` 다. 여기에는 결론만 적는다.

| ID | 내용 |
|---|---|
| `DEC-009` | `email_verification` 테이블을 없애고 **Redis** 로 옮겼다. TTL 휘발 데이터인데 refresh token(`DEC-010`)과 저장소 결정이 엇갈려 있었다. `V3` 가 테이블을 내린다 |
| `DEC-101` | `API-017` 의 노출 필드를 피그마로 확정했다 — `grade`·`heightCm`·`weightKg`·`itemCount` 를 싣고 `gender` 는 뺐다 |
| `DEC-102` | `API-071` 태그에 `saleStatus` 를 추가했다. 말풍선이 `#판매중` 을 2행으로 붙인다 |
| `DEC-103` | 판매·구매 내역을 엔드포인트 하나로 합쳤다. `rowType` 유니온으로 4개 탭을 담는다 |
| `DEC-104` | `API-079` 의 조회 단위를 게시물에서 **사진**으로 바꿨다 |
| `DEC-105` | ErrorCode enum 이름을 코드 접두사에 맞췄다. 예외 없음 |
| `IDX-003` | `ix_item_search`·`ix_item_listing_browse` 를 제거했다. 쓰는 엔드포인트가 없다 |
| `DEC-014` | `recent_view` 테이블을 없애고 **Redis Sorted Set** 으로 옮겼다. 보관 정책이 없어 영구 보관되던 고부하 테이블이다. `V4` 가 내린다 |
| `DEC-015` | `notification` 보관 기간을 **90일** 로 정했다. 지우는 경로가 없어 무한 증가하고 있었다 |

그 밖에 **도메인 패키지 배치표를 §5 에 넣었다** — 도메인과 ErrorCode 가 1:1 이 아니라
어느 패키지에 놓이는지 근거가 없었다.

### 범위 밖으로 둔 것

- **관리자 기능** — `report` 는 접수까지만 담는다. 처리 상태·운영자 계정이 없다
- **AI 아이템 인식 결과** — `API-080` 은 후보를 돌려줄 뿐 저장하지 않는다
- **태그 일괄 수정** — `API-083` 은 `MVP 외`
- **탈퇴 후 재가입 시 제안 기회 초기화** — 알고 넘어간다

---

## 8. 다음 단계

이 명세는 구현의 입력이다. Controller 는 `{도메인}Api` 인터페이스를 implements 하고
Swagger 어노테이션은 그 인터페이스에 단다. 구현이 끝나면 springdoc 이 생성하는 OpenAPI 가
스키마의 정본이 되고, 이 문서는 **API ID · 화면 매핑 · 설계 근거**를 담는 문서로 남는다.
