package com.tenure.domain.category.repository;

import com.tenure.domain.category.entity.Category;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CategoryRepository extends JpaRepository<Category, Long> {

	/**
	 * 아이템이 가리키는 세부 카테고리. 사이즈 칩 세트는 부모가 갖고 있으므로 함께 읽는다.
	 *
	 * depth 를 조건에 넣어 대분류 id 를 보낸 요청을 여기서 걸러낸다 — 화면은 세부 카테고리까지
	 * 고르게 하고, 대분류만 보내면 ITEM_400_CATEGORY 다.
	 */
	@Query("select c from Category c join fetch c.parent where c.id = :categoryId and c.depth = 2")
	Optional<Category> findLeafWithParent(@Param("categoryId") Long categoryId);
}
