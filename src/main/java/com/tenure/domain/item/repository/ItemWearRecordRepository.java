package com.tenure.domain.item.repository;

import com.tenure.domain.item.entity.ItemWearRecord;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ItemWearRecordRepository extends JpaRepository<ItemWearRecord, Long> {

	/**
	 * 게시물이 적립한 인증 전체. 게시물을 지울 때 함께 무효화한다.
	 *
	 * 소유 구간을 함께 가져온다 — 종료된 구간의 기록은 손대지 않아야 하므로 판정에 필요하다.
	 */
	@Query("select r from ItemWearRecord r join fetch r.ownership where r.post.id = :postId")
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
			@Param("postId") Long postId, @Param("itemId") Long itemId
	);
}
