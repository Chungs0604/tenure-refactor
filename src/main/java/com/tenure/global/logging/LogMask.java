package com.tenure.global.logging;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 이 파라미터를 로그에서 {@code ***} 로 가린다.
 *
 * <p>파라미터를 통째로 가릴 때만 쓴다. DTO 안의 필드는 {@code @ToString.Exclude} 로
 * DTO 쪽에서 막는다 — DTO 를 통째로 찍는 상황이 언제든 생기기 때문이다.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface LogMask {
}
