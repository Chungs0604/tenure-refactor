-- 화면 재판독(docs/design-audit.md)에서 나온 나머지 변경을 DB 에 반영한다.
--
-- V1 이 37개 테이블을 한 번에 만들어 둔 구조라, 아직 구현하지 않은 도메인도 DB 는 이미 존재한다.
-- 그래서 문서만 고치고 두면 다음 도메인을 구현할 때 어긋난 스키마 위에서 시작하게 된다.
-- V9(배송 요청사항)·V10(소셜 로그인)이 이미 구현된 도메인을 맞췄고, 여기서 나머지를 닫는다.

-- ── post.content 제거 (묶음 2)
-- 작성 플로우(촬영 → 비율 → 날짜 → 태그 → 미리보기 → 게시하기)에 본문 입력란이 없고
-- OOTD 상세에도 표시 영역이 없다. v3 의 근거 '안녕하세요 SSU님!' 은 홈 헤더 인사말을 잘못 인용한 것이다.
ALTER TABLE `post`
  DROP COLUMN `content`;

-- ── post.view_count 추가 (묶음 5)
-- 검색 결과 정렬에 '조회수 순'이 있다. 조회는 Redis 에 모았다가 배치가 이 컬럼에 더한다 (DEC-019).
ALTER TABLE `post`
  ADD COLUMN `view_count` INT NOT NULL DEFAULT 0 COMMENT '조회수. Redis 카운터를 주기적으로 반영한다 (DEC-019)' AFTER `save_count`;

-- ── item.wear_target 필수화 (묶음 3)
-- 아이템 등록·판매 전환 모두 '착용 대상'에 필수 표시(*)가 있다.
-- 기존 행이 있다면 UNISEX 로 채운 뒤 NOT NULL 을 건다.
UPDATE `item` SET `wear_target` = 'UNISEX' WHERE `wear_target` IS NULL;

ALTER TABLE `item`
  MODIFY COLUMN `wear_target` VARCHAR(10) NOT NULL COMMENT '착용 대상(WearTarget). 등록·판매 전환 모두 필수';

-- ── user_withdrawal.reason 을 enum 으로 (묶음 1)
-- 탈퇴 화면은 드롭다운 4종이고 자유 입력이 아니다. 기존 자유 텍스트는 매핑할 수 없어 버린다.
UPDATE `user_withdrawal` SET `reason` = NULL;

ALTER TABLE `user_withdrawal`
  MODIFY COLUMN `reason` VARCHAR(40) NOT NULL COMMENT '탈퇴 사유(WithdrawalReason). 드롭다운 4종';

-- ── notification_setting 의 축을 바꾼다 (묶음 1)
-- 설정 토글 9종은 알림 목록의 탭(NotificationCategory)과 다른 축이다.
-- 기존 행은 의미가 달라 옮길 수 없으므로 비우고, 가입 시 9종을 새로 만든다.
DELETE FROM `notification_setting`;

-- 인덱스를 지웠다 다시 만들지 않는다 — uk_notification_setting 은 FK(user_id)가 쓰고 있어
-- DROP 이 막히고(MySQL 1553), CHANGE COLUMN 은 컬럼명 변경을 인덱스 정의에 자동으로 반영한다.
ALTER TABLE `notification_setting`
  CHANGE COLUMN `category` `setting_type` VARCHAR(40) NOT NULL COMMENT '알림 설정 토글(NotificationSettingType) 9종';

-- ── notification.image_key 추가 (묶음 5)
-- 알림 행마다 아이템 썸네일이 붙는다. 다형 참조라 조인할 수 없어 발행 시점에 스냅샷한다.
ALTER TABLE `notification`
  ADD COLUMN `image_key` VARCHAR(500) NULL COMMENT '행 썸네일. 발행 시점 스냅샷' AFTER `body`;

-- ── payment.method_detail 추가 (묶음 4)
-- 제안서의 '결제 수단'이 '토스뱅크 1234-1234-1234' 로 표시된다. method enum 으로는 만들 수 없다.
ALTER TABLE `payment`
  ADD COLUMN `method_detail` VARCHAR(100) NULL COMMENT 'PG 가 준 결제 수단 표시 문자열 스냅샷' AFTER `method`;

-- ── 채팅 이미지 메시지를 여러 장으로 (묶음 4)
-- 사진 3장이 한 말풍선으로 묶이고 시각·읽음 표시도 하나다. 메시지를 장수만큼 쪼개면
-- 전송 중 일부만 실패했을 때 묶음이 깨진다.
ALTER TABLE `chat_message`
  MODIFY COLUMN `content` TEXT NULL COMMENT '메시지 본문. TEXT 일 때만 채운다';

CREATE TABLE `chat_message_image` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK 관례',
  `chat_message_id` BIGINT NOT NULL COMMENT '부모 FK',
  `image_key` VARCHAR(500) NOT NULL COMMENT '저장소 키 (DEC-006)',
  `sort_order` INT NOT NULL COMMENT '말풍선 안의 노출 순서',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_chat_message_image_order` (`chat_message_id`, `sort_order`),
  CONSTRAINT `fk_chat_message_image_message_id` FOREIGN KEY (`chat_message_id`) REFERENCES `chat_message` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT '이미지 메시지의 사진. 한 말풍선에 여러 장이 묶인다';
