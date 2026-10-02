package com.tenure.domain.wishlist.repository;

import com.tenure.domain.wishlist.entity.Wishlist;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WishlistRepository extends JpaRepository<Wishlist, Long> {

	// 마이페이지의 '위시 4'. 위시리스트 목록과 같은 조건이다
	int countByUserId(Long userId);

	/**
	 * 태그된 아이템 목록의 벨 아이콘 상태. 행마다 묻지 않고 한 번에 받는다.
	 *
	 * 관심 등록과 알림이 한 토글이라 '담겨 있고 알림도 켜진' 아이템만 켜진 것으로 본다 —
	 * 위시리스트 화면에서 알림만 끈 아이템은 여기서도 꺼진 벨로 보여야 한다.
	 */
	@Query("select w.item.id from Wishlist w " +
			"where w.user.id = :userId " +
			"and w.item.id in :itemIds " +
			"and w.alarmEnabled = true")
	List<Long> findAlarmEnabledItemIds(
			@Param("userId") Long userId,
			@Param("itemIds") Collection<Long> itemIds
	);

	/** 히어로 우측 벨 상태. 담기만 하고 알림을 껐어도 관심 등록은 되어 있다. */
	boolean existsByUserIdAndItemId(Long userId, Long itemId);
}
