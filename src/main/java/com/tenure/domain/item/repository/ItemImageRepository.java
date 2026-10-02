package com.tenure.domain.item.repository;

import com.tenure.domain.item.entity.ItemImage;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ItemImageRepository extends JpaRepository<ItemImage, Long> {

	/** 상세의 상품 사진. 등록 순서대로 그린다. */
	@Query("select i from ItemImage i where i.item.id = :itemId order by i.sortOrder")
	List<ItemImage> findAllByItemId(@Param("itemId") Long itemId);
}
