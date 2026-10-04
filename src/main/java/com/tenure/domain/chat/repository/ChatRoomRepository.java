package com.tenure.domain.chat.repository;

import com.tenure.domain.chat.entity.ChatRoom;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

	/** 아이템·구매자·판매자 고정 조합의 방. uk_chat_room 이 보장하는 유일 행임 */
	@Query("select r from ChatRoom r "
			+ "where r.item.id = :itemId and r.buyer.id = :buyerId and r.seller.id = :sellerId")
	Optional<ChatRoom> findByItemIdAndBuyerIdAndSellerId(
			@Param("itemId") Long itemId, @Param("buyerId") Long buyerId, @Param("sellerId") Long sellerId);
}
