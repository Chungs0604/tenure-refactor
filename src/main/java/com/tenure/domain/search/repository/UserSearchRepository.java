package com.tenure.domain.search.repository;

import com.tenure.domain.user.entity.User;
import com.tenure.global.pagination.Cursor;
import java.util.List;

/** API-201. 닉네임 부분 일치 + 팔로워 수 순(명세에 정렬 선택지가 없어 인기 순으로 고정). */
public interface UserSearchRepository {

	List<User> searchByNickname(String keyword, Long viewerId, Cursor cursor, int size);
}
