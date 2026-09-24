package com.tenure.global.logging;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Service 메서드의 START/END 로그를 AOP 에 맡긴다. 수동으로 로그를 쓰지 않는다.
 *
 * <p>값 출력은 기본으로 꺼져 있다 — 전부 켜면 로그량과 개인정보 노출이 둘 다 문제가 된다.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Loggable {

	/** 한글 기능명. 비우면 메서드명을 쓴다. */
	String value() default "";

	/** 파라미터 값 출력 여부. */
	boolean params() default false;

	/** 반환값 요약 출력 여부. */
	boolean result() default false;
}
