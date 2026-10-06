package com.tenure.domain.chat.repository;

import com.tenure.domain.chat.entity.ChatRoom;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long>, ChatRoomRepositoryCustom {

	/** 아이템·구매자·판매자 고정 조합의 방. uk_chat_room 이 보장하는 유일 행임 */
	@Query("select r from ChatRoom r "
			+ "where r.item.id = :itemId and r.buyer.id = :buyerId and r.seller.id = :sellerId")
	Optional<ChatRoom> findByItemIdAndBuyerIdAndSellerId(
			@Param("itemId") Long itemId, @Param("buyerId") Long buyerId, @Param("sellerId") Long sellerId);

	/** 헤더 조회 전용 — 아이템·판매등록·구매제안·구매자·판매자를 한 번에 읽어 화면 조립에 추가 조회가 없게 함 */
	@Query("select r from ChatRoom r "
			+ "join fetch r.item join fetch r.buyer join fetch r.seller "
			+ "left join fetch r.listing left join fetch r.purchaseOffer "
			+ "where r.id = :roomId")
	Optional<ChatRoom> findDetailById(@Param("roomId") Long roomId);
}
