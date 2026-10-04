package com.tenure.domain.address.repository;

import com.tenure.domain.address.entity.ShippingAddress;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ShippingAddressRepository extends JpaRepository<ShippingAddress, Long> {

	/** 목록 정렬은 화면 규칙이라 쿼리에 박는다 — 기본 배송지가 맨 위, 그다음 최근 등록 순. */
	@Query("select a from ShippingAddress a where a.user.id = :userId "
			+ "order by a.isDefault desc, a.createdAt desc")
	List<ShippingAddress> findAllOf(@Param("userId") Long userId);

	/** 본인 배송지만 제안에 쓸 수 있는지 확인함 */
	Optional<ShippingAddress> findByIdAndUserId(Long id, Long userId);

	/**
	 * 기존 기본 배송지를 내린다.
	 *
	 * 엔티티를 고쳐서 내리지 않는 이유 — Hibernate 는 flush 할 때 INSERT 를 UPDATE 보다 먼저
	 * 실행한다. 그러면 새 기본 배송지가 먼저 들어가 uk_shipping_address_default 에 걸린다.
	 * 벌크 UPDATE 는 호출 즉시 DB 에 닿으므로 해제가 항상 앞선다.
	 */
	@Modifying
	@Query("update ShippingAddress a set a.isDefault = false "
			+ "where a.user.id = :userId and a.isDefault = true")
	int releaseDefault(@Param("userId") Long userId);
}
