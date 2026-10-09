package com.tenure.domain.block.repository;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.NumberPath;
import com.querydsl.jpa.JPAExpressions;
import com.tenure.domain.block.entity.QUserBlock;

/** 차단 제외 조건을 쓰는 도메인(post·item 등)이 공유하는 QueryDSL 조건 모음. */
public final class BlockPredicates {

	private BlockPredicates() {
	}

	/**
	 * 차단 관계를 양방향으로 제외한다.
	 *
	 * 내가 차단한 사람의 글이 안 보이는 것은 당연하고, 나를 차단한 사람의 글도 내리는 게
	 * 맞다 — 차단은 '서로 마주치지 않게' 하는 조치이므로 한쪽만 막으면 절반만 지켜진다.
	 *
	 * 한 쿼리에 같은 테이블이 두 번 들어가야 해서 별칭을 메서드 안에서 새로 만든다 — 호출하는
	 * 쪽마다 별칭이 겹칠 걱정 없이 쓸 수 있다.
	 */
	public static BooleanExpression excludeBlockedEitherWay(NumberPath<Long> authorId, Long viewerId) {
		QUserBlock blockedByMe = new QUserBlock("blockedByMe");
		QUserBlock blockedMe = new QUserBlock("blockedMe");

		return authorId.notIn(
						JPAExpressions.select(blockedByMe.blocked.id)
								.from(blockedByMe)
								.where(blockedByMe.blocker.id.eq(viewerId)))
				.and(authorId.notIn(
						JPAExpressions.select(blockedMe.blocker.id)
								.from(blockedMe)
								.where(blockedMe.blocked.id.eq(viewerId))));
	}
}
