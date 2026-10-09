package com.tenure.domain.post.repository;

import com.tenure.domain.post.entity.PostItemTag;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.tenure.domain.post.entity.Post;

public interface PostItemTagRepository extends JpaRepository<PostItemTag, Long> {

	/** 태그된 아이템 목록(SCR-016). 단위가 게시물이 아니라 사진이다 (DEC-104). */
	@Query("select t from PostItemTag t join fetch t.item " +
			"where t.postImage.id = :imageId " +
			"order by t.id")
	List<PostItemTag> findAllByPostImageId(@Param("imageId") Long imageId);

	/** 상세의 말풍선. 사진 순서대로, 사진 안에서는 부착 순서대로 그린다. */
	@Query("select t from PostItemTag t join fetch t.item join fetch t.postImage i " +
			"where i.post.id = :postId " +
			"order by i.sortOrder, t.id")
	List<PostItemTag> findAllByPostId(@Param("postId") Long postId);
	/**
	 * '자주 같이 입은 옷'. 이 아이템과 같은 게시물에 함께 태그된 아이템을 많이 겹친 순으로 센다.
	 *
	 * 단위가 사진이 아니라 게시물이다 — 한 코디에서 같이 입었다는 뜻이라 사진이 여러 장이어도
	 * 한 번으로 센다. 그래서 게시물 id 를 distinct 로 센다.
	 *
	 * 돌려주는 배열은 {아이템 id, 겹친 게시물 수} 다.
	 */
	@Query("select other.item.id, count(distinct other.postImage.post.id) "
			+ "from PostItemTag mine, PostItemTag other "
			+ "where mine.item.id = :itemId "
			+ "and other.postImage.post.id = mine.postImage.post.id "
			+ "and other.item.id <> :itemId "
			+ "and other.item.deletedAt is null "
			+ "and other.postImage.post.deletedAt is null "
			+ "group by other.item.id "
			+ "order by count(distinct other.postImage.post.id) desc, other.item.id asc")
	List<Object[]> findFrequentlyWornWith(@Param("itemId") Long itemId, Pageable pageable);

	/**
	 * 이 아이템이 태그된 OOTD. 판매 등록 이력이 없는 아이템의 상세가 '태그된 OOTD' 로 그린다.
	 *
	 * 한 게시물의 여러 사진에 같은 아이템이 태그될 수 있어 distinct 로 묶는다.
	 */
	@Query("select distinct p from PostItemTag t join t.postImage i join i.post p "
			+ "where t.item.id = :itemId and p.deletedAt is null "
			+ "order by p.wornDate desc, p.id desc")
	List<Post> findTaggedPosts(@Param("itemId") Long itemId, Pageable pageable);

	/**
	 * 이 게시물 안에서 이 아이템이 아직 태그된 횟수.
	 *
	 * 같은 아이템을 여러 사진에 태그할 수 있어서, 태그 하나를 지우거나 바꿔도 다른 사진에
	 * 그대로 남아있으면 인증을 무효화하면 안 된다. 그 잔존 여부를 이걸로 판단한다.
	 */
	@Query("select count(t) from PostItemTag t join t.postImage i "
			+ "where i.post.id = :postId and t.item.id = :itemId")
	long countByPostIdAndItemId(@Param("postId") Long postId, @Param("itemId") Long itemId);
}
