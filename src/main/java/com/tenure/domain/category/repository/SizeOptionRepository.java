package com.tenure.domain.category.repository;

import com.tenure.domain.category.entity.SizeOption;
import com.tenure.domain.category.enums.SizeScheme;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SizeOptionRepository extends JpaRepository<SizeOption, Long> {

	/** 카테고리 트리에 실어 보낼 칩 전체. 82행뿐이라 세트별로 나눠 읽지 않는다. */
	@Query("select o from SizeOption o order by o.scheme, o.sortOrder")
	List<SizeOption> findAllOrdered();

	/** 등록·수정이 size_label 을 검증할 때 쓴다. 대소문자는 DB collation 이 무시한다. */
	boolean existsBySchemeAndLabel(SizeScheme scheme, String label);

	/** 에러 메시지에 고를 수 있는 값을 적어 주려면 목록이 필요하다. */
	@Query("select o.label from SizeOption o where o.scheme = :scheme order by o.sortOrder")
	List<String> findLabels(@Param("scheme") SizeScheme scheme);
}
