package com.tenure.global.logging;

import com.tenure.global.exception.CustomException;
import jakarta.persistence.Entity;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.StringJoiner;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

/** 도메인 Service 의 START/END/FAIL 을 한 곳에서 찍는다. */
@Slf4j
@Aspect
@Component
public class ServiceLoggingAspect {

	private static final int MAX_LEN = 200;

	/**
	 * 도메인 서비스 전체와, 그 밖에서 Loggable 을 붙인 메서드를 잡는다.
	 *
	 * 패키지 조건만 두면 global 에 있는 서비스(이미지 업로드)가 빠진다. 어노테이션을
	 * 붙였는데 로그가 안 남는 쪽이 규칙이 하나 더 있는 것보다 알아채기 어렵다.
	 */
	@Around("execution(* com.tenure.domain..service..*.*(..)) "
			+ "|| @annotation(com.tenure.global.logging.Loggable)")
	public Object log(ProceedingJoinPoint pjp) throws Throwable {
		MethodSignature signature = (MethodSignature) pjp.getSignature();
		Method method = signature.getMethod();
		Loggable annotation = method.getAnnotation(Loggable.class);

		String name = (annotation != null && !annotation.value().isBlank())
				? annotation.value()
				: method.getName();
		long start = System.currentTimeMillis();

		if (annotation != null && annotation.params()) {
			log.info("[{}] START {}", name, formatArgs(signature, method, pjp.getArgs()));
		} else {
			log.info("[{}] START", name);
		}

		try {
			Object result = pjp.proceed();
			long elapsed = System.currentTimeMillis() - start;
			if (annotation != null && annotation.result()) {
				log.info("[{}] END ({}ms) result={}", name, elapsed, summarize(result));
			} else {
				log.info("[{}] END ({}ms)", name, elapsed);
			}
			return result;

		} catch (CustomException e) {
			// 비즈니스 예외: 어느 지점에서 어떤 코드로 끊겼는지만 남긴다
			log.warn("[{}] FAIL ({}ms) code={}",
					name, System.currentTimeMillis() - start, e.getErrorCode().getCode());
			throw e;

		} catch (Exception e) {
			log.error("[{}] ERROR ({}ms)", name, System.currentTimeMillis() - start, e);
			throw e;
		}
	}

	private String formatArgs(MethodSignature signature, Method method, Object[] args) {
		String[] names = signature.getParameterNames();
		Annotation[][] parameterAnnotations = method.getParameterAnnotations();
		StringJoiner joiner = new StringJoiner(", ", "(", ")");
		for (int i = 0; i < args.length; i++) {
			boolean masked = Arrays.stream(parameterAnnotations[i])
					.anyMatch(a -> a.annotationType() == LogMask.class);
			joiner.add(names[i] + "=" + (masked ? "***" : summarize(args[i])));
		}
		return joiner.toString();
	}

	private String summarize(Object value) {
		if (value == null) {
			return "null";
		}
		// 엔티티는 toString 하지 않는다 — LAZY 프록시가 초기화되고 양방향이면 순환한다
		if (value.getClass().isAnnotationPresent(Entity.class)) {
			return value.getClass().getSimpleName() + "(...)";
		}
		if (value instanceof Collection<?> collection) {
			return value.getClass().getSimpleName() + "(size=" + collection.size() + ")";
		}
		if (value instanceof Page<?> page) {
			return "Page(size=%d, total=%d)".formatted(page.getNumberOfElements(), page.getTotalElements());
		}
		String text = String.valueOf(value);
		return text.length() > MAX_LEN ? text.substring(0, MAX_LEN) + "…" : text;
	}
}
