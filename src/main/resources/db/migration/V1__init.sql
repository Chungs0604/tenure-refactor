-- Tenure 초기 스키마 — docs/schema.yaml v3 에서 생성
-- 생성기: scratchpad/gen/gen_ddl.py · 손으로 고치지 말고 정본을 고쳐 다시 생성한다
-- 테이블 39 · 컬럼 317 · 인덱스 86 · FK 69

CREATE TABLE `category` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK 관례',
  `parent_id` BIGINT COMMENT 'depth=1 이면 NULL',
  `name` VARCHAR(50) NOT NULL COMMENT '대분류 10종(화면 순서): 아우터·상의·하의·신발·가방·모자·치마·원피스·액세서리·주얼리. 세부 102종. 값은 V2__seed_category.sql 이 정본',
  `depth` TINYINT NOT NULL COMMENT '1=카테고리, 2=세부 카테고리',
  `size_scheme` VARCHAR(30) COMMENT '''아이템 상세 정보 수정 (카테고리마다 세부 카테고리, 사이즈 다름)'' — 카테고리별 사이즈 체계가 다르다(→ Q6)',
  `sort_order` SMALLINT NOT NULL DEFAULT 0 COMMENT '드롭다운 노출 순서',
  PRIMARY KEY (`id`),
  KEY `ix_category_parent` (`parent_id`, `sort_order`),
  CONSTRAINT `fk_category_parent_id` FOREIGN KEY (`parent_id`) REFERENCES `category` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT '2단 카테고리(카테고리 > 세부 카테고리). 자기참조.';

CREATE TABLE `email_verification` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK 관례',
  `email` VARCHAR(255) NOT NULL COMMENT '인증 대상 이메일. user 생성 전이라 FK 불가',
  `purpose` VARCHAR(20) NOT NULL COMMENT '회원가입 / 비밀번호 찾기 두 플로우',
  `code` VARCHAR(10) NOT NULL COMMENT '''인증번호''. ''인증번호가 틀렸습니다''',
  `expires_at` DATETIME(6) NOT NULL COMMENT '인증번호 만료 시각 (발급 +3분). 토큰 수명과는 별개다',
  `verified_at` DATETIME(6) COMMENT '''인증되었습니다.'' 인증번호 확인 시각. 이 시점에 token 이 발급된다',
  `token` VARCHAR(64) COMMENT '인증 완료를 증명하는 1회용 토큰. 인증번호 확인(API-005) 시 발급되며 가입·비밀번호 재설정에 제시한다',
  `token_expires_at` DATETIME(6) COMMENT '토큰 만료 시각 (발급 +10분). 가입 플로우(약관→주소→프로필)를 마칠 여유를 준다',
  `consumed_at` DATETIME(6) COMMENT '토큰 소비 시각. **재사용 차단의 핵심** — NULL 이 아니면 이미 쓴 토큰이다',
  `created_at` DATETIME(6) NOT NULL COMMENT '감사 컬럼 관례',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_email_verification_token` (`token`),
  UNIQUE KEY `uk_email_verification_target` (`email`, `purpose`) COMMENT '(email, purpose) 당 1행. 재발급 시 이전 행을 지우고 새로 넣는다'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT '이메일 인증번호와, 인증 완료를 증명하는 1회용 토큰. 가입 전 단계라 User 와 FK 로 묶이지 않는다. (email, purpose) 당 항상 최신 1행만 존재한다.';

CREATE TABLE `user` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK 관례',
  `email` VARCHAR(255) NOT NULL COMMENT '회원가입 > 이메일. ''이미 존재하는 이메일입니다'' / ''유효하지 않은 이메일입니다''',
  `password_hash` VARCHAR(255) NOT NULL COMMENT '회원가입 > 비밀번호 ''(8자 이상)''. 평문 저장 불가로 해시 컬럼',
  `real_name` VARCHAR(50) NOT NULL COMMENT '로그인 정보 > 이름 ''홍길동''',
  `phone` VARCHAR(20) NOT NULL COMMENT '로그인 정보 > 휴대폰번호 ''821012345678''',
  `nickname` VARCHAR(30) NOT NULL COMMENT '프로필 작성 > 닉네임. ''중복된 닉네임입니다'' / ''닉네임을 설정해주세요.''',
  `profile_image_key` VARCHAR(500) COMMENT '''프로필 사진은 필수로 받지 않습니다! 등록하지 않았다면 기본 프로필 사진''. 저장소 키를 넣는다 (DEC-006)',
  `gender` VARCHAR(10) COMMENT '프로필 작성 > 성별',
  `height_cm` SMALLINT COMMENT '프로필 작성 > 키 ''170cm''. 검색 필터 ''필터/키-몸무게''의 대상',
  `weight_kg` SMALLINT COMMENT '프로필 작성 > 몸무게 ''64kg''',
  `grade` VARCHAR(20) NOT NULL DEFAULT 'BASIC' COMMENT '''유저가 레코드 사용자인지, 기본 사용자인지에 따라 수수료 부담·배송비 섹션이 서로 다릅니다''',
  `status` VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '회원탈퇴 화면 존재 + 탈퇴 후에도 이력 참조 필요',
  `anonymized_at` DATETIME(6) COMMENT '탈퇴 시 개인정보 파기 시점. NULL이면 파기 전 (→ DEC-004)',
  `grade_expires_at` DATETIME(6) COMMENT '레코드 등급 보장 만료 시각(승격 +30일). 재평가 배치의 대상 선별 기준',
  `follower_count` INT NOT NULL DEFAULT 0 COMMENT '검색 계정 탭·인기 사용자·OOTD 상세 헤더에 ''팔로우 101명''이 목록으로 반복 노출된다. 정본은 follow 테이블',
  `post_count` INT NOT NULL DEFAULT 0 COMMENT '같은 목록에 ''게시물 60개''·''피드 60개''·''OOTD 289개''가 함께 노출된다. 정본은 post 테이블',
  `created_at` DATETIME(6) NOT NULL COMMENT '감사 컬럼 관례',
  `updated_at` DATETIME(6) NOT NULL COMMENT '감사 컬럼 관례',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_email` (`email`),
  UNIQUE KEY `uk_user_nickname` (`nickname`),
  KEY `ix_user_body` (`height_cm`, `weight_kg`) COMMENT '검색 필터 ''키-몸무게''',
  KEY `ix_user_grade_expiry` (`grade`, `grade_expires_at`) COMMENT '보장 만료된 레코드 사용자 재평가 대상 조회'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT '서비스 사용자. 계정 정보(실명·휴대폰)와 프로필(닉네임·체형)을 함께 보유한다.';

CREATE TABLE `follow` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK 관례',
  `follower_id` BIGINT NOT NULL COMMENT '팔로우를 건 사용자',
  `following_id` BIGINT NOT NULL COMMENT '팔로우 대상',
  `created_at` DATETIME(6) NOT NULL COMMENT '감사 컬럼 관례',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_follow` (`follower_id`, `following_id`),
  KEY `ix_follow_following` (`following_id`),
  CONSTRAINT `fk_follow_follower_id` FOREIGN KEY (`follower_id`) REFERENCES `user` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_follow_following_id` FOREIGN KEY (`following_id`) REFERENCES `user` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_follow_self` CHECK (`follower_id` <> `following_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT '팔로우 관계. 마이페이지 팔로워/팔로잉 수의 원천.';

CREATE TABLE `item` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK 관례',
  `registrant_id` BIGINT NOT NULL COMMENT '최초 등록자. ''Tenure 기록 기준'' 최초 구간의 ''최초 등록'' 뱃지',
  `name` VARCHAR(100) NOT NULL COMMENT '''아이템명''. 예 ''LVC 1955 501''',
  `brand_name` VARCHAR(100) NOT NULL COMMENT '''브랜드명''. 예 ''Levis''',
  `category_id` BIGINT NOT NULL COMMENT '''세부 카테고리''(depth=2)를 가리킨다',
  `size_label` VARCHAR(20) COMMENT '''사이즈''. 예 ''L''',
  `wear_target` VARCHAR(10) COMMENT '''착용 대상''. 예 ''남성복''',
  `cover_image_key` VARCHAR(500) NOT NULL COMMENT '''대표 사진'' (필수 표시 *). 저장소 키를 넣는다 (DEC-006)',
  `description` TEXT COMMENT '''제품 설명'' / ''상품 설명''',
  `first_owned_date` DATE COMMENT '''최초 보유 날짜''. 예 25.02.8. 최초 item_ownership 구간의 start_date 와 같은 값이며, 거래 전까지만 수정할 수 있다',
  `deleted_at` DATETIME(6) COMMENT '''삭제된 아이템'' 상태. soft delete',
  `created_at` DATETIME(6) NOT NULL COMMENT '감사 컬럼 관례',
  `updated_at` DATETIME(6) NOT NULL COMMENT '''상세 수정''',
  PRIMARY KEY (`id`),
  KEY `ix_item_category` (`category_id`, `deleted_at`) COMMENT 'category_id 가 등치 조건이라 앞에 둔다',
  KEY `ix_item_registrant` (`registrant_id`, `deleted_at`),
  CONSTRAINT `fk_item_category_id` FOREIGN KEY (`category_id`) REFERENCES `category` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_item_registrant_id` FOREIGN KEY (`registrant_id`) REFERENCES `user` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT '패션 아이템. 소유자가 바뀌어도 이 행은 유지된다 — 착용 기록이 아이템에 영구 귀속되어야 "구매자는 아이템의 기록을 이어서 받게됩니다"가 성립한다. 현재 소유자와 보유 기간은 ItemOwnership 에서 조회한다.';

CREATE TABLE `notification` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK 관례',
  `user_id` BIGINT NOT NULL COMMENT '수신자',
  `category` VARCHAR(30) NOT NULL COMMENT '탭 4종',
  `title` VARCHAR(200) NOT NULL COMMENT '알림 목록 항목 제목',
  `body` VARCHAR(500) COMMENT '알림 본문',
  `target_type` VARCHAR(30) COMMENT '딥링크 대상 종류',
  `target_id` BIGINT COMMENT '다형 참조라 FK 없음',
  `read_at` DATETIME(6) COMMENT '읽음 표시 (읽지 않은 항목 강조)',
  `created_at` DATETIME(6) NOT NULL COMMENT '''어제'', ''10월 24일'', ''4:28 pm''',
  PRIMARY KEY (`id`),
  KEY `ix_notification_list` (`user_id`, `category`, `created_at`),
  KEY `ix_notification_unread` (`user_id`, `read_at`),
  CONSTRAINT `fk_notification_user_id` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT '알림. 화면의 탭 구성이 그대로 category 다.';

CREATE TABLE `notification_setting` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK 관례',
  `user_id` BIGINT NOT NULL COMMENT '소유자 FK',
  `category` VARCHAR(30) NOT NULL COMMENT '알림 화면 탭 4종',
  `enabled` BOOLEAN NOT NULL DEFAULT TRUE COMMENT '''알림 활성화시킬 때마다 토스트 메시지''',
  `updated_at` DATETIME(6) NOT NULL COMMENT '감사 컬럼 관례',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_notification_setting` (`user_id`, `category`),
  CONSTRAINT `fk_notification_setting_user_id` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT '알림 유형별 수신 여부.';

CREATE TABLE `payout_account` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK 관례',
  `user_id` BIGINT NOT NULL COMMENT '소유자 FK',
  `bank_code` VARCHAR(20) NOT NULL COMMENT '''은행'' 드롭다운. 예 ''토스뱅크''',
  `account_number` VARCHAR(50) NOT NULL COMMENT '''계좌 번호'' 1111-1111-1111',
  `account_holder` VARCHAR(50) NOT NULL COMMENT '''예금주'' 김유진',
  `is_default` BOOLEAN NOT NULL DEFAULT FALSE COMMENT '복수 계좌 등록 가능한 목록 화면(''정산계좌 관리'')',
  `created_at` DATETIME(6) NOT NULL COMMENT '감사 컬럼 관례',
  `updated_at` DATETIME(6) NOT NULL COMMENT '''정산계좌 수정하기''',
  PRIMARY KEY (`id`),
  KEY `ix_payout_account_user` (`user_id`),
  CONSTRAINT `fk_payout_account_user_id` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT '정산 계좌. 판매 전환의 선행 조건이다.';

CREATE TABLE `post` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK 관례',
  `author_id` BIGINT NOT NULL COMMENT '게시물 작성자. OOTD 상세 상단 프로필',
  `content` TEXT COMMENT '게시물 본문. 예 ''안녕하세요 SSU님!''',
  `worn_date` DATE NOT NULL COMMENT '''달력 아이콘 클릭'' → ''날짜 선택''. 마이페이지 날짜별 캘린더와 ''최근 착용''의 기준',
  `aspect_ratio` VARCHAR(15) NOT NULL COMMENT '촬영 비율 3종',
  `quick_posted` BOOLEAN NOT NULL DEFAULT FALSE COMMENT '''자동 태그되어 바로 게시됨''. 태그 일괄 수정 대상 판별(3일 이내)에 필요',
  `deleted_at` DATETIME(6) COMMENT '''게시물 삭제하기''. soft delete',
  `like_count` INT NOT NULL DEFAULT 0 COMMENT 'OOTD 상세 하단 ''♡ 1234''. 정본은 post_like 테이블',
  `save_count` INT NOT NULL DEFAULT 0 COMMENT 'OOTD 상세 하단 '' 1234''. 정본은 post_save 테이블',
  `created_at` DATETIME(6) NOT NULL COMMENT '감사 컬럼 관례',
  `updated_at` DATETIME(6) NOT NULL COMMENT '''완료를 누르면 게시물 전체가 수정''',
  PRIMARY KEY (`id`),
  KEY `ix_post_author_date` (`author_id`, `deleted_at`, `worn_date`) COMMENT '마이페이지 날짜별 조회',
  KEY `ix_post_feed` (`deleted_at`, `created_at`) COMMENT '홈 피드 / 새로 올라온 OOTD',
  KEY `ix_post_author_created` (`author_id`, `deleted_at`, `created_at`) COMMENT '레코드 승격 판정 — 직전 30일 유효 OOTD 카운트. 게시일 기준이라 worn_date 인덱스와 별개',
  CONSTRAINT `fk_post_author_id` FOREIGN KEY (`author_id`) REFERENCES `user` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT 'OOTD 게시물. 착용 날짜를 갖고 마이페이지에서 날짜별로 조회된다.';

CREATE TABLE `recent_view` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK 관례',
  `user_id` BIGINT NOT NULL COMMENT '열람자',
  `target_type` VARCHAR(20) NOT NULL COMMENT '게시물 / 사용자',
  `target_id` BIGINT NOT NULL COMMENT '다형 참조라 FK 없음',
  `viewed_at` DATETIME(6) NOT NULL COMMENT '''최근'' 정렬 기준',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_recent_view` (`user_id`, `target_type`, `target_id`) COMMENT '재열람 시 viewed_at 만 갱신',
  KEY `ix_recent_view_user` (`user_id`, `viewed_at`),
  CONSTRAINT `fk_recent_view_user_id` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT '최근 본 게시물·사용자. 검색 첫 화면의 ''최근 본 사용자''와 ''방금 보신 OOTD와 유사한 게시물'' 추천의 입력이다.';

CREATE TABLE `report` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK 관례',
  `reporter_id` BIGINT NOT NULL COMMENT '신고자',
  `target_type` VARCHAR(20) NOT NULL COMMENT '신고 진입점 3종',
  `target_id` BIGINT NOT NULL COMMENT '다형 참조라 FK 없음',
  `reason` VARCHAR(40) NOT NULL COMMENT '''신고 사유를 선택해주세요.'' 드롭다운',
  `detail` VARCHAR(500) COMMENT '상세 사유 입력란. 화면 미확인',
  `created_at` DATETIME(6) NOT NULL COMMENT '감사 컬럼 관례',
  PRIMARY KEY (`id`),
  KEY `ix_report_target` (`target_type`, `target_id`),
  KEY `ix_report_reporter` (`reporter_id`),
  CONSTRAINT `fk_report_reporter_id` FOREIGN KEY (`reporter_id`) REFERENCES `user` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT '게시물·상품·사용자 신고. ReportReason 10종의 발생 근거.';

CREATE TABLE `search_history` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK 관례',
  `user_id` BIGINT NOT NULL COMMENT '소유자',
  `keyword` VARCHAR(100) NOT NULL COMMENT '''검색에 반팔 입력 시 화면''',
  `searched_at` DATETIME(6) NOT NULL COMMENT '''최근 검색'' 정렬',
  PRIMARY KEY (`id`),
  KEY `ix_search_history_user` (`user_id`, `searched_at`),
  CONSTRAINT `fk_search_history_user_id` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT '최근 검색어.';

CREATE TABLE `shipping_address` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK 관례',
  `user_id` BIGINT NOT NULL COMMENT '소유자 FK',
  `recipient_name` VARCHAR(50) NOT NULL COMMENT '''이름'' / ''받는 분'' / ''수령인''',
  `phone` VARCHAR(20) NOT NULL COMMENT '''휴대폰 번호'' / ''연락처'' 010-1234-5678',
  `address` VARCHAR(255) NOT NULL COMMENT '''주소'' 서울특별시 동작구 상도로 369. ''계정 주소 검색'' 프레임 존재',
  `address_detail` VARCHAR(255) COMMENT '''상세 주소'' 104동 201호',
  `delivery_request` VARCHAR(255) COMMENT '''배송 요청사항(선택)''',
  `is_default` BOOLEAN NOT NULL DEFAULT FALSE COMMENT '''기본 배송지로 설정'', ''클릭으로 기본 배송지 변경''',
  `created_at` DATETIME(6) NOT NULL COMMENT '감사 컬럼 관례',
  `updated_at` DATETIME(6) NOT NULL COMMENT '''배송지 수정'' 존재',
  PRIMARY KEY (`id`),
  KEY `ix_shipping_address_user` (`user_id`, `is_default`),
  CONSTRAINT `fk_shipping_address_user_id` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT '배송지 주소록. 주문 시에는 OrderShipment 로 값을 스냅샷한다.';

CREATE TABLE `user_agreement` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK 관례',
  `user_id` BIGINT NOT NULL COMMENT '소유자 FK',
  `agreement_type` VARCHAR(40) NOT NULL COMMENT '약관 3종',
  `agreed` BOOLEAN NOT NULL COMMENT '''모두 동의'' 체크박스',
  `agreed_at` DATETIME(6) NOT NULL COMMENT '동의 시점 기록',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_agreement` (`user_id`, `agreement_type`),
  CONSTRAINT `fk_user_agreement_user_id` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT '필수 약관 3종 동의 이력.';

CREATE TABLE `user_block` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK 관례',
  `blocker_id` BIGINT NOT NULL COMMENT '차단한 사용자',
  `blocked_id` BIGINT NOT NULL COMMENT '''이 사용자를 차단할까요?''',
  `created_at` DATETIME(6) NOT NULL COMMENT '감사 컬럼 관례',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_block` (`blocker_id`, `blocked_id`),
  KEY `ix_user_block_blocked` (`blocked_id`) COMMENT '역방향 조회 — ''나를 차단한 사용자''',
  CONSTRAINT `fk_user_block_blocker_id` FOREIGN KEY (`blocker_id`) REFERENCES `user` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_user_block_blocked_id` FOREIGN KEY (`blocked_id`) REFERENCES `user` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT '사용자 차단. 해제가 있으므로 행 삭제로 처리한다.';

CREATE TABLE `user_grade_history` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK 관례',
  `user_id` BIGINT NOT NULL COMMENT '대상 사용자',
  `before_grade` VARCHAR(20) COMMENT '최초 부여 시 NULL',
  `after_grade` VARCHAR(20) NOT NULL COMMENT '변경 후 등급',
  `window_start` DATE COMMENT '평가 구간 시작(재평가 시점 -30일)',
  `window_end` DATE COMMENT '평가 구간 종료',
  `valid_ootd_count` INT COMMENT '그 구간의 유효 OOTD 수. 승격·강등 판정의 근거값',
  `changed_at` DATETIME(6) NOT NULL COMMENT '변경 시각',
  PRIMARY KEY (`id`),
  KEY `ix_user_grade_history_user` (`user_id`, `changed_at`),
  CONSTRAINT `fk_user_grade_history_user_id` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT '등급 변경 이력. UserGrade.RECORD 로의 승격이 언제·왜 일어났는지 기록할 자리.';

CREATE TABLE `user_withdrawal` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK 관례',
  `user_id` BIGINT NOT NULL COMMENT '사용자당 1건',
  `reason` VARCHAR(500) COMMENT '''Tenure를 떠나시는 이유를 알려주세요''',
  `withdrawn_at` DATETIME(6) NOT NULL COMMENT '탈퇴 시각',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_withdrawal_user` (`user_id`),
  CONSTRAINT `fk_user_withdrawal_user_id` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT '회원탈퇴 사유. UserStatus.WITHDRAWN 의 발생 근거.';

CREATE TABLE `item_condition` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK 관례',
  `item_id` BIGINT NOT NULL COMMENT '부모 FK',
  `condition_code` VARCHAR(30) NOT NULL COMMENT '체크박스 5종',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_item_condition` (`item_id`, `condition_code`),
  CONSTRAINT `fk_item_condition_item_id` FOREIGN KEY (`item_id`) REFERENCES `item` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT '상태 이상 체크(복수 선택).';

CREATE TABLE `item_image` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK 관례',
  `item_id` BIGINT NOT NULL COMMENT '부모 FK',
  `image_key` VARCHAR(500) NOT NULL COMMENT '상품 상세 이미지 캐러셀. 저장소 키를 넣는다 (DEC-006)',
  `sort_order` SMALLINT NOT NULL COMMENT '노출 순서',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_item_image_order` (`item_id`, `sort_order`),
  CONSTRAINT `fk_item_image_item_id` FOREIGN KEY (`item_id`) REFERENCES `item` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT '아이템 사진.';

CREATE TABLE `item_listing` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK 관례',
  `item_id` BIGINT NOT NULL COMMENT '판매 대상 아이템',
  `seller_id` BIGINT NOT NULL COMMENT '''판매자 정보'' 섹션',
  `sale_status` VARCHAR(20) NOT NULL COMMENT '''판매 중'' / ''미판매'' / 판매 완료 배지',
  `offer_allowed` BOOLEAN NOT NULL DEFAULT FALSE COMMENT '''구매 제안 O'' / ''구매 제안 X'', ''구매 제안 허용'' 토글',
  `price` INT COMMENT '''가격'' 360,000원. 미판매면 NULL',
  `shipping_fee` INT NOT NULL DEFAULT 0 COMMENT '''배송비'' +5,000원. 기본 사용자는 고정값',
  `fee_bearer` VARCHAR(10) NOT NULL COMMENT '''수수료 부담''. 기본 사용자는 고정값',
  `seller_note` TEXT COMMENT '''판매자 설명''',
  `wish_count` INT NOT NULL DEFAULT 0 COMMENT '''관심 14명'' — 화면에 수치가 표시된다. 비정규화 카운터이며 정본은 wishlist 테이블(→ 파생 컬럼 대장)',
  `listed_at` DATETIME(6) COMMENT '''판매 게시가 완료됐습니다.''',
  `closed_at` DATETIME(6) COMMENT '''미판매로 변경시 화면'' — 판매 내림 시각',
  `created_at` DATETIME(6) NOT NULL COMMENT '감사 컬럼 관례',
  `updated_at` DATETIME(6) NOT NULL COMMENT '감사 컬럼 관례',
  PRIMARY KEY (`id`),
  KEY `ix_item_listing_item` (`item_id`, `created_at`),
  KEY `ix_item_listing_seller` (`seller_id`, `sale_status`) COMMENT '판매 내역 탭',
  CONSTRAINT `fk_item_listing_item_id` FOREIGN KEY (`item_id`) REFERENCES `item` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_item_listing_seller_id` FOREIGN KEY (`seller_id`) REFERENCES `user` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT '아이템의 판매 등록(''판매 전환''). 아이템이 재판매될 때마다 새 행이 생기므로 Item 과 1:N 이다. 생성 전제로 판매자에게 PayoutAccount 가 있어야 한다.';

CREATE TABLE `item_measurement` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK 관례',
  `item_id` BIGINT NOT NULL COMMENT '아이템당 1행 (1:1)',
  `shoulder_width` DECIMAL(5,1) COMMENT '어깨 너비(cm). 카테고리별로 필수·금지가 갈린다 (사용자 지정 사양)',
  `chest_width` DECIMAL(5,1) COMMENT '가슴 단면(cm). 카테고리별로 필수·금지가 갈린다 (사용자 지정 사양)',
  `sleeve_length` DECIMAL(5,1) COMMENT '소매길이(cm). 카테고리별로 필수·금지가 갈린다 (사용자 지정 사양)',
  `total_length` DECIMAL(5,1) COMMENT '총 기장(cm). 카테고리별로 필수·금지가 갈린다 (사용자 지정 사양)',
  `waist_width` DECIMAL(5,1) COMMENT '허리 단면(cm). 카테고리별로 필수·금지가 갈린다 (사용자 지정 사양)',
  `thigh_width` DECIMAL(5,1) COMMENT '허벅지 단면(cm). 카테고리별로 필수·금지가 갈린다 (사용자 지정 사양)',
  `rise` DECIMAL(5,1) COMMENT '밑위(cm). 카테고리별로 필수·금지가 갈린다 (사용자 지정 사양)',
  `inseam` DECIMAL(5,1) COMMENT '인심(cm). 카테고리별로 필수·금지가 갈린다 (사용자 지정 사양)',
  `hem_width` DECIMAL(5,1) COMMENT '밑단(cm). 카테고리별로 필수·금지가 갈린다 (사용자 지정 사양)',
  `hip_width` DECIMAL(5,1) COMMENT '엉덩이 단면(cm). 카테고리별로 필수·금지가 갈린다 (사용자 지정 사양)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_item_measurement_item` (`item_id`),
  CONSTRAINT `fk_item_measurement_item_id` FOREIGN KEY (`item_id`) REFERENCES `item` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT '판매 상품 실측. 10개 고정 필드이며 카테고리별 차이는 ''어떤 걸 채우느냐''로만 표현된다. JPA 에서는 값 객체(@Embeddable)로 매핑한다.';

CREATE TABLE `post_image` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK 관례',
  `post_id` BIGINT NOT NULL COMMENT '부모 FK',
  `image_key` VARCHAR(500) NOT NULL COMMENT '촬영·앨범 이미지. 저장소 키를 넣는다 (DEC-006)',
  `sort_order` SMALLINT NOT NULL COMMENT '''바로 게시한 사진들이 순서대로 보여요''',
  `is_cover` BOOLEAN NOT NULL DEFAULT FALSE COMMENT '''대표 사진'' / ''대표 OOTD''',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_post_image_order` (`post_id`, `sort_order`),
  CONSTRAINT `fk_post_image_post_id` FOREIGN KEY (`post_id`) REFERENCES `post` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT '게시물 사진(다중). 대표 사진 플래그를 갖는다.';

CREATE TABLE `post_like` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK 관례',
  `post_id` BIGINT NOT NULL COMMENT '대상 게시물',
  `user_id` BIGINT NOT NULL COMMENT '누른 사용자',
  `created_at` DATETIME(6) NOT NULL COMMENT '마이페이지 좋아요 탭 정렬',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_post_like` (`post_id`, `user_id`),
  KEY `ix_post_like_user` (`user_id`, `created_at`),
  CONSTRAINT `fk_post_like_post_id` FOREIGN KEY (`post_id`) REFERENCES `post` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_post_like_user_id` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT '좋아요.';

CREATE TABLE `post_save` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK 관례',
  `post_id` BIGINT NOT NULL COMMENT '대상 게시물',
  `user_id` BIGINT NOT NULL COMMENT '저장한 사용자',
  `created_at` DATETIME(6) NOT NULL COMMENT '마이페이지 저장 탭 정렬',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_post_save` (`post_id`, `user_id`),
  KEY `ix_post_save_user` (`user_id`, `created_at`),
  CONSTRAINT `fk_post_save_post_id` FOREIGN KEY (`post_id`) REFERENCES `post` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_post_save_user_id` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT '저장(북마크). 마이페이지 탭이 좋아요와 분리돼 있어 별도 테이블이다.';

CREATE TABLE `wishlist` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK 관례',
  `user_id` BIGINT NOT NULL COMMENT '관심 등록한 사용자',
  `item_id` BIGINT NOT NULL COMMENT '관심 아이템',
  `alarm_enabled` BOOLEAN NOT NULL DEFAULT FALSE COMMENT '''위시리스트 알림 활성화'' / ''알림 비활성화'' 프레임 2종, 항목별 벨 아이콘',
  `created_at` DATETIME(6) NOT NULL COMMENT '목록 정렬',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_wishlist` (`user_id`, `item_id`),
  KEY `ix_wishlist_item` (`item_id`) COMMENT '''관심 14명'' 집계',
  CONSTRAINT `fk_wishlist_user_id` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_wishlist_item_id` FOREIGN KEY (`item_id`) REFERENCES `item` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT '관심 등록(위시리스트). Item 기준이다 — 목록에 ''판매중''/''미판매'' 배지가 함께 뜨므로 판매 등록 전 아이템도 담을 수 있어야 한다.';

CREATE TABLE `chat_room` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK 관례',
  `item_id` BIGINT NOT NULL COMMENT '채팅방 헤더의 아이템. ''Levis / LVC 1955 501''',
  `listing_id` BIGINT COMMENT '헤더의 가격·판매 상태. 미판매 아이템이면 NULL 가능',
  `buyer_id` BIGINT NOT NULL COMMENT '''구매 채팅'' 필터의 기준',
  `seller_id` BIGINT NOT NULL COMMENT '''판매 채팅'' 필터의 기준',
  `last_message_at` DATETIME(6) COMMENT '목록의 ''6월 27일'' 최근 메시지 시각 정렬',
  `created_at` DATETIME(6) NOT NULL COMMENT '감사 컬럼 관례',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_chat_room` (`item_id`, `buyer_id`, `seller_id`),
  KEY `ix_chat_room_buyer` (`buyer_id`, `last_message_at`),
  KEY `ix_chat_room_seller` (`seller_id`, `last_message_at`),
  KEY `ix_chat_room_listing` (`listing_id`) COMMENT 'FK 인덱스',
  CONSTRAINT `fk_chat_room_item_id` FOREIGN KEY (`item_id`) REFERENCES `item` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_chat_room_listing_id` FOREIGN KEY (`listing_id`) REFERENCES `item_listing` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_chat_room_buyer_id` FOREIGN KEY (`buyer_id`) REFERENCES `user` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_chat_room_seller_id` FOREIGN KEY (`seller_id`) REFERENCES `user` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT '아이템별 1:1 채팅방. 헤더에 아이템 정보(''판매 중 · 최근 착용 1일 전'')와 거래 단계별 CTA 가 붙는다.';

CREATE TABLE `listing_featured_post` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK 관례',
  `listing_id` BIGINT NOT NULL COMMENT '부모 FK',
  `post_id` BIGINT NOT NULL COMMENT '대표로 건 OOTD 게시물',
  `sort_order` SMALLINT NOT NULL DEFAULT 0 COMMENT '노출 순서',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_listing_featured_post` (`listing_id`, `post_id`),
  KEY `ix_listing_featured_post_post` (`post_id`) COMMENT '게시물 삭제 차단 검사',
  CONSTRAINT `fk_listing_featured_post_listing_id` FOREIGN KEY (`listing_id`) REFERENCES `item_listing` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_listing_featured_post_post_id` FOREIGN KEY (`post_id`) REFERENCES `post` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT '판매 등록에 거는 ''대표 OOTD''. 상품 사진만이 아니라 판매자가 실제로 입은 OOTD 를 함께 보여주는 것이 Tenure 의 판매 방식이라 별도 연결 테이블로 둔다.';

CREATE TABLE `post_item_tag` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK 관례',
  `post_image_id` BIGINT NOT NULL COMMENT '부모 FK',
  `item_id` BIGINT NOT NULL COMMENT '''pill text / Our Legacy 니트'' 태그가 아이템을 가리킨다',
  `pos_x` DECIMAL(5,4) NOT NULL COMMENT '''원하는 아이템 클릭하면 해당 아이템에 사각형이 쳐진다'' — 좌표 필요. 이미지 폭 대비 비율로 저장',
  `pos_y` DECIMAL(5,4) NOT NULL COMMENT '이미지 높이 대비 비율',
  `created_at` DATETIME(6) NOT NULL COMMENT '감사 컬럼 관례',
  `updated_at` DATETIME(6) NOT NULL COMMENT '''사진 속 태그를 누르면 연결된 아이템을 바꿀 수 있어요.''',
  PRIMARY KEY (`id`),
  KEY `ix_post_item_tag_image` (`post_image_id`),
  KEY `ix_post_item_tag_item` (`item_id`) COMMENT '아이템 히스토리 / OOTD 인증 조회',
  CONSTRAINT `fk_post_item_tag_post_image_id` FOREIGN KEY (`post_image_id`) REFERENCES `post_image` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_post_item_tag_item_id` FOREIGN KEY (`item_id`) REFERENCES `item` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT '사진 속 아이템 태그. 게시물과 아이템을 잇는 서비스의 중심 연결고리.';

CREATE TABLE `purchase_offer` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK 관례',
  `listing_id` BIGINT NOT NULL COMMENT '제안 대상 판매 등록',
  `item_id` BIGINT NOT NULL COMMENT '1회 제한 판정을 아이템 기준으로 하기 위해 중복 보유(→ purchase_offer 제약 — 아이템당 1회)',
  `buyer_id` BIGINT NOT NULL COMMENT '''구매자 정보'' 섹션',
  `seller_id` BIGINT NOT NULL COMMENT '''판매자 정보'' 섹션. ''YuJin님에게''',
  `offer_type` VARCHAR(20) NOT NULL COMMENT '구매 제안 / 거래 의사',
  `offer_price` INT NOT NULL COMMENT '''제안 금액'' / ''제안 가격'' 360,000원',
  `shipping_fee` INT NOT NULL DEFAULT 0 COMMENT '''배송비'' +5,000원',
  `fee_amount` INT NOT NULL DEFAULT 0 COMMENT '''수수료''. 화면 예시 0원(판매자 기준) / +12,000원(구매자 기준) (→ purchase_offer 제약 — 수수료 계산)',
  `fee_bearer` VARCHAR(10) NOT NULL COMMENT '요청 시점에 확정되는 수수료 부담. 소유자가 BASIC 이면 SELLER 로 강제된다',
  `fee_rate` DECIMAL(5,4) NOT NULL COMMENT '적용 요율 스냅샷. 요청 전송 시점의 소유자 등급 기준(BASIC 0.06 / RECORD 0.03)',
  `buyer_fee_amount` INT NOT NULL DEFAULT 0 COMMENT '구매자 부담 수수료. SPLIT 이면 floor(총액/2)',
  `seller_fee_amount` INT NOT NULL DEFAULT 0 COMMENT '판매자 부담 수수료. SPLIT 이면 총액 - 구매자분(홀수 1원은 판매자)',
  `total_payment_amount` INT NOT NULL COMMENT '''총 결제 금액'' 377,000원 (구매자 기준)',
  `expected_payout_amount` INT NOT NULL COMMENT '''총 정산 예정 금액'' 365,000원 (판매자 기준)',
  `buyer_request` VARCHAR(500) COMMENT '''거래 요청사항'' / ''구매자 요청사항''',
  `status` VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '''응답 대기'' 칩',
  `expires_at` DATETIME(6) NOT NULL COMMENT '''제안 만료까지 23시간 18분 남음'' = 생성 +24h',
  `responded_at` DATETIME(6) COMMENT '판매자 수락/거절 시각. ACCEPTED·REJECTED 의 근거',
  `canceled_at` DATETIME(6) COMMENT '''구매 제안을 취소할까요?'' — CANCELED 의 근거',
  `cancel_reason` VARCHAR(30) COMMENT '취소 사유. 자동 취소는 1회 제한에서 제외된다',
  `expired_at` DATETIME(6) COMMENT '24h 만료 배치 기록. EXPIRED 의 근거',
  `created_at` DATETIME(6) NOT NULL COMMENT '감사 컬럼 관례',
  `updated_at` DATETIME(6) NOT NULL COMMENT '감사 컬럼 관례',
  PRIMARY KEY (`id`),
  KEY `ix_purchase_offer_seller` (`seller_id`, `status`, `expires_at`) COMMENT '판매 내역 > 제안받음',
  KEY `ix_purchase_offer_buyer` (`buyer_id`, `status`) COMMENT '구매 내역 > 제안중',
  KEY `ix_purchase_offer_listing` (`listing_id`, `status`),
  KEY `ix_purchase_offer_expire` (`status`, `expires_at`) COMMENT '만료 배치',
  KEY `ix_purchase_offer_limit` (`item_id`, `buyer_id`, `offer_type`) COMMENT '1회 제한 판정',
  KEY `ix_purchase_offer_item_pending` (`item_id`, `status`) COMMENT '수락 시 같은 아이템의 나머지 대기 요청을 자동 취소하기 위한 조회',
  CONSTRAINT `fk_purchase_offer_listing_id` FOREIGN KEY (`listing_id`) REFERENCES `item_listing` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_purchase_offer_item_id` FOREIGN KEY (`item_id`) REFERENCES `item` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_purchase_offer_buyer_id` FOREIGN KEY (`buyer_id`) REFERENCES `user` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_purchase_offer_seller_id` FOREIGN KEY (`seller_id`) REFERENCES `user` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT '구매 제안 / 거래 의사. 대상 상태에 따라 두 종류로 나뉘며 생명주기가 같아 한 테이블로 둔다. 두 경우 모두 결제 승인이 먼저 잡히고 판매자 수락 시 최종 결제된다.';

CREATE TABLE `chat_message` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK 관례',
  `chat_room_id` BIGINT NOT NULL COMMENT '부모 FK',
  `sender_id` BIGINT NOT NULL COMMENT '발신자. 좌/우 말풍선 구분',
  `message_type` VARCHAR(10) NOT NULL COMMENT '텍스트 / ''사진''',
  `content` TEXT NOT NULL COMMENT '메시지 본문. IMAGE 면 저장소 키 (DEC-006)',
  `created_at` DATETIME(6) NOT NULL COMMENT '''오후 6:30'', 날짜 구분선 ''2026.06.21 월요일''',
  PRIMARY KEY (`id`),
  KEY `ix_chat_message_room` (`chat_room_id`, `id`) COMMENT '커서 페이징',
  KEY `ix_chat_message_sender` (`sender_id`) COMMENT 'FK 인덱스 (쓰기 빈발 테이블 — 스킬 5-2 ⑥ 경계 대상, 아래 주석 참고)',
  CONSTRAINT `fk_chat_message_chat_room_id` FOREIGN KEY (`chat_room_id`) REFERENCES `chat_room` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_chat_message_sender_id` FOREIGN KEY (`sender_id`) REFERENCES `user` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT '채팅 메시지.';

CREATE TABLE `post_tag_edit_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK 관례',
  `post_item_tag_id` BIGINT NOT NULL COMMENT '수정 대상 태그',
  `before_item_id` BIGINT COMMENT '변경 전 아이템',
  `after_item_id` BIGINT COMMENT '변경 후 아이템',
  `edited_by` BIGINT NOT NULL COMMENT '수정자',
  `edited_at` DATETIME(6) NOT NULL COMMENT '수정 시각',
  PRIMARY KEY (`id`),
  KEY `ix_post_tag_edit_log_tag` (`post_item_tag_id`),
  KEY `ix_post_tag_edit_log_before` (`before_item_id`) COMMENT 'FK 인덱스',
  KEY `ix_post_tag_edit_log_after` (`after_item_id`) COMMENT 'FK 인덱스',
  KEY `ix_post_tag_edit_log_editor` (`edited_by`) COMMENT 'FK 인덱스',
  CONSTRAINT `fk_post_tag_edit_log_post_item_tag_id` FOREIGN KEY (`post_item_tag_id`) REFERENCES `post_item_tag` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_post_tag_edit_log_before_item_id` FOREIGN KEY (`before_item_id`) REFERENCES `item` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_post_tag_edit_log_after_item_id` FOREIGN KEY (`after_item_id`) REFERENCES `item` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_post_tag_edit_log_edited_by` FOREIGN KEY (`edited_by`) REFERENCES `user` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT '태그 수정 이력. MVP 외.';

CREATE TABLE `trade_order` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK 관례',
  `order_no` VARCHAR(30) NOT NULL COMMENT '''주문 번호'' 2026042919502395',
  `offer_id` BIGINT NOT NULL COMMENT '''제안이 수락되는 즉시 상품이 구매 처리되며''',
  `listing_id` BIGINT NOT NULL COMMENT '판매 등록 참조',
  `item_id` BIGINT NOT NULL COMMENT '거래 상세 상단 상품 정보',
  `buyer_id` BIGINT NOT NULL COMMENT '''구매자 정보''',
  `seller_id` BIGINT NOT NULL COMMENT '''판매자 정보''',
  `item_price` INT NOT NULL COMMENT '''상품 금액'' 360,000원',
  `shipping_fee` INT NOT NULL COMMENT '''배송비'' +5,000원',
  `fee_amount` INT NOT NULL COMMENT '''수수료''',
  `fee_rate` DECIMAL(5,4) NOT NULL COMMENT '제안 시점 요율 스냅샷',
  `buyer_fee_amount` INT NOT NULL DEFAULT 0 COMMENT '구매자 부담 수수료 스냅샷',
  `seller_fee_amount` INT NOT NULL DEFAULT 0 COMMENT '판매자 부담 수수료 스냅샷',
  `fee_bearer` VARCHAR(10) NOT NULL COMMENT '판매 등록의 ''수수료 부담''을 스냅샷',
  `total_payment_amount` INT NOT NULL COMMENT '''총 결제 금액'' 377,000원',
  `payout_amount` INT NOT NULL COMMENT '''총 정산 금액'' 365,000원',
  `status` VARCHAR(30) NOT NULL COMMENT '거래 단계 6종 + 취소',
  `auto_confirm_at` DATETIME(6) COMMENT '''자동 구매 확정까지 23시간 44분 남음'' = 배송 완료 +72h',
  `confirmed_at` DATETIME(6) COMMENT '''구매를 확정할까요?''',
  `canceled_at` DATETIME(6) COMMENT '거래 상세의 ''거래 취소'' 버튼 (→ trade_order 제약 — 거래 취소)',
  `created_at` DATETIME(6) NOT NULL COMMENT '감사 컬럼 관례',
  `updated_at` DATETIME(6) NOT NULL COMMENT '감사 컬럼 관례',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_trade_order_no` (`order_no`),
  UNIQUE KEY `uk_trade_order_offer` (`offer_id`),
  KEY `ix_trade_order_buyer` (`buyer_id`, `status`) COMMENT '구매 내역 탭',
  KEY `ix_trade_order_seller` (`seller_id`, `status`) COMMENT '판매 내역 탭',
  KEY `ix_trade_order_auto_confirm` (`status`, `auto_confirm_at`) COMMENT '자동 구매 확정 배치',
  KEY `ix_trade_order_listing` (`listing_id`) COMMENT 'FK 인덱스',
  KEY `ix_trade_order_item` (`item_id`) COMMENT 'FK 인덱스 — 아이템별 거래 이력 조회',
  CONSTRAINT `fk_trade_order_offer_id` FOREIGN KEY (`offer_id`) REFERENCES `purchase_offer` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_trade_order_listing_id` FOREIGN KEY (`listing_id`) REFERENCES `item_listing` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_trade_order_item_id` FOREIGN KEY (`item_id`) REFERENCES `item` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_trade_order_buyer_id` FOREIGN KEY (`buyer_id`) REFERENCES `user` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_trade_order_seller_id` FOREIGN KEY (`seller_id`) REFERENCES `user` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT '거래. 제안이 수락되는 즉시 생성되어 결제 완료 단계부터 시작한다. 금액은 제안 시점 값을 스냅샷한다. 테이블명은 order 가 MySQL 예약어라 trade_order 를 쓴다.';

CREATE TABLE `chat_room_member` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK 관례',
  `chat_room_id` BIGINT NOT NULL COMMENT '부모 FK',
  `user_id` BIGINT NOT NULL COMMENT '참여자',
  `last_read_message_id` BIGINT COMMENT '안읽음 수 계산 기준. 배지 ''1'' ''3''',
  `left_at` DATETIME(6) COMMENT '''채팅방 나가기''',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_chat_room_member` (`chat_room_id`, `user_id`),
  KEY `ix_chat_room_member_user` (`user_id`) COMMENT 'FK 인덱스 — uk(chat_room_id, user_id) 의 선두가 아니라 별도 필요',
  KEY `ix_chat_room_member_last_read` (`last_read_message_id`) COMMENT 'FK 인덱스',
  CONSTRAINT `fk_chat_room_member_chat_room_id` FOREIGN KEY (`chat_room_id`) REFERENCES `chat_room` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_chat_room_member_user_id` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_chat_room_member_last_read_message_id` FOREIGN KEY (`last_read_message_id`) REFERENCES `chat_message` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT '참여자별 읽음 위치와 나가기 상태. 안읽음 배지와 ''읽지않음'' 필터의 원천.';

CREATE TABLE `item_ownership` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK 관례',
  `item_id` BIGINT NOT NULL COMMENT '대상 아이템',
  `owner_id` BIGINT NOT NULL COMMENT '구간 소유자. 화면에 닉네임 표시',
  `start_date` DATE NOT NULL COMMENT '''25.02.8 ~'' 구간 시작. **최초 구간은 item.first_owned_date 를 그대로 쓴다.** 거래로 승계된 구간은 구매 확정일',
  `end_date` DATE COMMENT '''~ 26.07.10'' 구간 종료. NULL 이면 ''~ 현재''',
  `acquired_by` VARCHAR(30) NOT NULL COMMENT '''최초 등록'' 뱃지 유무',
  `order_id` BIGINT COMMENT 'acquired_by=TRADE 일 때 근거 거래. AcquiredBy.TRADE 의 발생 근거 자리',
  `created_at` DATETIME(6) NOT NULL COMMENT '감사 컬럼 관례',
  PRIMARY KEY (`id`),
  KEY `ix_item_ownership_item` (`item_id`, `start_date`),
  KEY `ix_item_ownership_owner` (`owner_id`),
  KEY `ix_item_ownership_order` (`order_id`) COMMENT 'FK 인덱스 — 거래로 승계된 구간 역추적',
  CONSTRAINT `fk_item_ownership_item_id` FOREIGN KEY (`item_id`) REFERENCES `item` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_item_ownership_owner_id` FOREIGN KEY (`owner_id`) REFERENCES `user` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_item_ownership_order_id` FOREIGN KEY (`order_id`) REFERENCES `trade_order` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT '아이템의 소유 구간. 상품 상세 ''Tenure 기록 기준'' 섹션이 이 테이블 그대로다. 거래 완료 시 이전 행의 end_date 를 닫고 구매자 행을 새로 연다.';

CREATE TABLE `order_shipment` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK 관례',
  `order_id` BIGINT NOT NULL COMMENT '거래당 1건',
  `recipient_name` VARCHAR(50) NOT NULL COMMENT '''수령인'' / ''받는 분'' 홍길동',
  `phone` VARCHAR(20) NOT NULL COMMENT '''연락처'' 010-1234-5678',
  `address` VARCHAR(255) NOT NULL COMMENT '''주소'' 서울특별시 동작구 상도로 369',
  `address_detail` VARCHAR(255) COMMENT '''상세주소'' 112동 201호',
  `delivery_request` VARCHAR(255) COMMENT '''요청사항'' 배송 전에 미리 연락 바랍니다.',
  `courier_code` VARCHAR(30) COMMENT '''택배사'' CJ 택배',
  `tracking_no` VARCHAR(50) COMMENT '''운송장 번호'' 1234123412349',
  `shipped_at` DATETIME(6) COMMENT '''상품 발송'' 25.05.28',
  `delivered_at` DATETIME(6) COMMENT '''배송을 완료 처리할까요?''. 이 시각 +72h 가 auto_confirm_at',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_shipment_order` (`order_id`),
  KEY `ix_order_shipment_tracking` (`tracking_no`),
  CONSTRAINT `fk_order_shipment_order_id` FOREIGN KEY (`order_id`) REFERENCES `trade_order` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT '배송 정보. 배송지는 ShippingAddress 를 FK 로 걸지 않고 값을 스냅샷한다.';

CREATE TABLE `order_status_history` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK 관례',
  `order_id` BIGINT NOT NULL COMMENT '부모 거래',
  `status` VARCHAR(30) NOT NULL COMMENT 'OrderStatus 각 값의 발생 근거 자리',
  `changed_by` BIGINT COMMENT 'NULL 이면 시스템(자동 확정·정산)',
  `changed_at` DATETIME(6) NOT NULL COMMENT '타임라인 각 단계의 날짜',
  PRIMARY KEY (`id`),
  KEY `ix_order_status_history` (`order_id`, `changed_at`),
  KEY `ix_order_status_history_user` (`changed_by`) COMMENT 'FK 인덱스',
  CONSTRAINT `fk_order_status_history_order_id` FOREIGN KEY (`order_id`) REFERENCES `trade_order` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_order_status_history_changed_by` FOREIGN KEY (`changed_by`) REFERENCES `user` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT '거래 진행 단계 이력. 거래 상세의 타임라인이 이 테이블이다.';

CREATE TABLE `payment` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK 관례',
  `offer_id` BIGINT NOT NULL COMMENT '결제는 제안 시점에 발생한다',
  `order_id` BIGINT COMMENT '수락되어 주문이 생긴 뒤 연결',
  `method` VARCHAR(20) NOT NULL COMMENT '''결제 수단'' 3종',
  `status` VARCHAR(20) NOT NULL COMMENT '승인/확정/해제 3단계',
  `amount` INT NOT NULL COMMENT '''최종 결제 금액'' / ''결제 금액''',
  `pg_transaction_id` VARCHAR(100) COMMENT 'PG 연동 식별자. 화면 미표시',
  `authorized_at` DATETIME(6) COMMENT '''결제 승인이 임시로 확보되었습니다''',
  `captured_at` DATETIME(6) COMMENT '''판매자가 수락하면 최종 결제되며''',
  `released_at` DATETIME(6) COMMENT '''거절·취소·만료 시 승인이 해제됩니다''',
  `refunded_at` DATETIME(6) COMMENT '거래 취소에 따른 환불 시각. PaymentStatus.REFUNDED 의 발생 근거 자리(→ trade_order 제약 — 거래 취소)',
  `created_at` DATETIME(6) NOT NULL COMMENT '감사 컬럼 관례',
  `updated_at` DATETIME(6) NOT NULL COMMENT '감사 컬럼 관례',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_payment_offer` (`offer_id`),
  KEY `ix_payment_order` (`order_id`),
  CONSTRAINT `fk_payment_offer_id` FOREIGN KEY (`offer_id`) REFERENCES `purchase_offer` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_payment_order_id` FOREIGN KEY (`order_id`) REFERENCES `trade_order` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT '결제. 승인과 확정이 분리되며 주문보다 먼저 생성된다 — 구매자가 제안을 보낼 때 승인이 잡히고, 판매자가 수락해야 TradeOrder 가 생긴다.';

CREATE TABLE `settlement` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK 관례',
  `order_id` BIGINT NOT NULL COMMENT '거래당 1건',
  `seller_id` BIGINT NOT NULL COMMENT '정산 수취인',
  `bank_code` VARCHAR(20) NOT NULL COMMENT '''정산 계좌'' 토스뱅크 — 스냅샷',
  `account_number` VARCHAR(50) NOT NULL COMMENT '1111-1111-1111 — 스냅샷',
  `account_holder` VARCHAR(50) NOT NULL COMMENT '김유진 — 스냅샷',
  `amount` INT NOT NULL COMMENT '''정산 금액'' / ''총 정산 금액'' 365,000원',
  `settled_at` DATETIME(6) COMMENT '''정산 완료'' 25.06.04 (→ settlement 제약 — 정산 예정일)',
  `created_at` DATETIME(6) NOT NULL COMMENT '감사 컬럼 관례',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_settlement_order` (`order_id`),
  KEY `ix_settlement_seller` (`seller_id`, `settled_at`) COMMENT '정산 미완료 거래 조회 → 계좌 삭제 차단',
  CONSTRAINT `fk_settlement_order_id` FOREIGN KEY (`order_id`) REFERENCES `trade_order` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_settlement_seller_id` FOREIGN KEY (`seller_id`) REFERENCES `user` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT '판매자 정산. 정산 계좌도 스냅샷한다.';

CREATE TABLE `item_wear_record` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK 관례',
  `item_id` BIGINT NOT NULL COMMENT '대상 아이템',
  `ownership_id` BIGINT NOT NULL COMMENT '소유 구간별 ''OOTD 인증 N회'' 집계 단위',
  `post_id` BIGINT NOT NULL COMMENT '인증 근거 게시물. ''아이템 히스토리''가 이 링크로 사진을 모은다',
  `worn_date` DATE NOT NULL COMMENT '''최근 착용'' 계산 기준. post.worn_date 를 복사',
  `created_at` DATETIME(6) NOT NULL COMMENT '감사 컬럼 관례',
  `voided_at` DATETIME(6) COMMENT '태그가 다른 아이템으로 바뀌거나 삭제되면 이 인증을 무효화한다. 행은 지우지 않는다(append-only). 인증 횟수 집계는 WHERE voided_at IS NULL',
  `voided_reason` VARCHAR(30) COMMENT '무효화 사유. voided_at 이 NULL 이 아닐 때만 채워진다',
  PRIMARY KEY (`id`),
  KEY `ix_item_wear_record_item` (`item_id`, `voided_at`, `worn_date`) COMMENT '''최근 착용'' 조회. item_id·voided_at 등치 → worn_date 정렬',
  KEY `ix_item_wear_record_ownership` (`ownership_id`, `voided_at`) COMMENT '소유 구간별 ''OOTD 인증 N회'' 집계. 둘 다 등치 조건',
  UNIQUE KEY `uk_item_wear_record` (`item_id`, `post_id`) COMMENT '같은 게시물이 같은 아이템을 두 번 인증하지 않는다',
  KEY `ix_item_wear_record_post` (`post_id`) COMMENT 'FK 인덱스 — uk(item_id, post_id) 의 선두가 아니라 별도 필요',
  CONSTRAINT `fk_item_wear_record_item_id` FOREIGN KEY (`item_id`) REFERENCES `item` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_item_wear_record_ownership_id` FOREIGN KEY (`ownership_id`) REFERENCES `item_ownership` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_item_wear_record_post_id` FOREIGN KEY (`post_id`) REFERENCES `post` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT '착용 기록(''OOTD 인증''). PostItemTag 에서 파생 가능하지만 별도 테이블로 둔다 — 인증 횟수가 소유 구간별로 집계되므로 ownership_id 가 필요하고, 아이템이 승계된 뒤에도 이전 소유자의 인증 이력이 그대로 남아야 한다.';
