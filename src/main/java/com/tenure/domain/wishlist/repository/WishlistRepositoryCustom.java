package com.tenure.domain.wishlist.repository;

import com.tenure.domain.wishlist.entity.Wishlist;
import com.tenure.domain.wishlist.enums.WishlistTab;
import com.tenure.global.pagination.Cursor;
import java.util.List;

public interface WishlistRepositoryCustom {

	/** 위시리스트 목록. 탭으로 거르고 커서 페이징함(createdAt desc, id desc) */
	List<Wishlist> findAllForList(Long userId, WishlistTab tab, Cursor cursor, int size);
}
