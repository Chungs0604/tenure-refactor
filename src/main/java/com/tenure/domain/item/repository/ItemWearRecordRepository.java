package com.tenure.domain.item.repository;

import com.tenure.domain.item.entity.ItemWearRecord;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ItemWearRecordRepository extends JpaRepository<ItemWearRecord, Long>, ItemWearRecordRepositoryCustom {

	/**
	 * 게시물이 적립한 인증 전체. 게시물을 지울 때 함께 무효화한다.
	 *
	 * 소유 구간을 함께 가져온다 — 종료된 구간의 기록은 손대지 않아야 하므로 판정에 필요하다.
	 */
	@Query("select r from ItemWearRecord r " +
			"join fetch r.ownership " +
			"where r.post.id = :postId")
	List<ItemWearRecord> findAllByPostId(@Param("postId") Long postId);

	/**
	 * 한 게시물이 한 아이템에 적립한 인증.
	 *
	 * uk_item_wear_record(item_id, post_id) 때문에 무효화된 행이 남아 있으면 같은 조합을
	 * 다시 INSERT 할 수 없다. 태그를 되돌릴 때 이 행을 찾아 살린다.
	 */
	@Query("select r from ItemWearRecord r join fetch r.ownership "
			+ "where r.post.id = :postId and r.item.id = :itemId")
	Optional<ItemWearRecord> findByPostIdAndItemId(
			@Param("postId") Long postId,
			@Param("itemId") Long itemId
	);
	/**
	 * 목록의 '최근 착용 N일 전'. 페이지의 아이템을 한 번에 센다 — 행마다 조회하면 카드 수만큼 쿼리가 는다.
	 *
	 * ix_item_wear_record_item(item_id, voided_at, worn_date) 가 그대로 받는다.
	 */
	@Query("select new com.tenure.domain.item.repository.ItemLastWornDateRow(r.item.id, max(r.wornDate)) " +
			"from ItemWearRecord r " +
			"where r.item.id in :itemIds and r.voidedAt is null " +
			"group by r.item.id")
	List<ItemLastWornDateRow> findLastWornDates(@Param("itemIds") Collection<Long> itemIds);

	/** 소유 구간별 'OOTD 인증 N회'. ix_item_wear_record_ownership 이 등치 두 개로 받는다. */
	@Query("select new com.tenure.domain.item.repository.OwnershipWearCountRow(r.ownership.id, count(r)) " +
			"from ItemWearRecord r " +
			"where r.item.id = :itemId and r.voidedAt is null " +
			"group by r.ownership.id")
	List<OwnershipWearCountRow> countByOwnership(@Param("itemId") Long itemId);

	/**
	 * 소유 구간 카드에 들어가는 OOTD 사진 3장.
	 *
	 * 구간마다 최신 3건만 필요한데 JPQL 에는 그룹별 LIMIT 이 없다. 자기보다 최신인 기록이
	 * 3건 미만인 행만 남기는 상관 서브쿼리로 같은 일을 한다 — 구간 수만큼 쿼리를 나누지 않고
	 * 한 번에 끝난다. 정렬 키가 겹칠 때를 대비해 id 로 tie-break 한다.
	 */
	@Query("select new com.tenure.domain.item.repository.OwnershipPreviewRow(r.ownership.id, image.imageKey) "
			+ "from ItemWearRecord r "
			+ "join PostImage image on image.post.id = r.post.id and image.isCover = true "
			+ "where r.item.id = :itemId and r.voidedAt is null "
			+ "and (select count(other) from ItemWearRecord other "
			+ "     where other.ownership.id = r.ownership.id and other.voidedAt is null "
			+ "     and (other.wornDate > r.wornDate "
			+ "          or (other.wornDate = r.wornDate and other.id > r.id))) < 3 "
			+ "order by r.ownership.id, r.wornDate desc, r.id desc")
	List<OwnershipPreviewRow> findOwnershipPreviews(@Param("itemId") Long itemId);
	/**
	 * 현재 소유 구간의 'OOTD 인증 N회'. 태그 시트가 행마다 이 숫자를 그린다.
	 *
	 * 전체 누적이 아니라 지금 주인이 인증한 횟수다 — 내 옷장 맥락이라 '내가 이 옷을 얼마나
	 * 입었나' 를 보여준다. 페이지의 아이템을 한 번에 센다.
	 */
	@Query("select new com.tenure.domain.item.repository.ItemCurrentWearCountRow(r.item.id, count(r)) "
			+ "from ItemWearRecord r "
			+ "where r.item.id in :itemIds and r.voidedAt is null "
			+ "and r.ownership.endDate is null "
			+ "group by r.item.id")
	List<ItemCurrentWearCountRow> countCurrentOwnershipWears(@Param("itemIds") Collection<Long> itemIds);
}
