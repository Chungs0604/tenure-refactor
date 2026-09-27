package com.tenure.domain.wishlist.repository;

import com.tenure.domain.wishlist.entity.Wishlist;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WishlistRepository extends JpaRepository<Wishlist, Long> {

	// 마이페이지의 '위시 4'. 위시리스트 목록과 같은 조건이다
	int countByUserId(Long userId);
}
