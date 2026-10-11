package com.tenure.domain.follow.repository;

import com.tenure.domain.follow.entity.Follow;
import com.tenure.global.pagination.Cursor;
import java.util.List;

public interface FollowRepositoryCustom {

	/** userId 를 팔로우하는 사용자 목록(팔로워). createdAt desc, id desc, size+1 건 */
	List<Follow> findFollowers(Long userId, Cursor cursor, int size);

	/** userId 가 팔로우하는 사용자 목록(팔로잉). createdAt desc, id desc, size+1 건 */
	List<Follow> findFollowings(Long userId, Cursor cursor, int size);
}
