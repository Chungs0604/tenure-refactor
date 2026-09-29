package com.tenure.domain.item.repository;

import com.tenure.domain.item.entity.ItemOwnership;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ItemOwnershipRepository extends JpaRepository<ItemOwnership, Long> {

	/**
	 * 현재 소유 구간. end_date IS NULL 인 행은 아이템당 1개다.
	 *
	 * 태그 자격 판정과 착용 기록 적립이 모두 이 구간을 기준으로 한다 — 인증 횟수가 소유 구간별로
	 * 집계되므로 어느 구간에 적립하는지가 화면의 'OOTD 인증 N회' 를 가른다.
	 */
	@Query("select o from ItemOwnership o where o.item.id = :itemId and o.endDate is null")
	Optional<ItemOwnership> findCurrent(@Param("itemId") Long itemId);
}
