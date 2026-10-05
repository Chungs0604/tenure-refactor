package com.tenure.domain.order.service;

import com.tenure.domain.order.enums.CourierCode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 택배사 추적 URL 조합 — 클라이언트가 URL 패턴을 갖고 있으면 택배사
 * 추가가 앱 배포에 묶인다. URL 은 저장하지 않고 courier_code + tracking_no 로 매번 조합한다.
 *
 * 각 택배사의 추적 페이지 URL 패턴이다. 택배사 쪽에서 URL 구조를 바꾸면 이 맵만 고치면 된다
 */
@Component
public class TrackingUrlResolver {

	private static final Map<CourierCode, String> URL_TEMPLATES = Map.of(
			CourierCode.CJ_LOGISTICS, "https://www.cjlogistics.com/ko/tool/parcel/tracking?gnbInvcNo=%s",
			CourierCode.KOREA_POST, "https://service.epost.go.kr/trace.RetrieveDomRigiTraceList.comm?sid1=%s",
			CourierCode.GS_POSTBOX, "https://www.cvsnet.co.kr/invoice/tracking.do?invoice_no=%s",
			CourierCode.CU_CONVENIENCE, "https://www.cupost.co.kr/postbox/delivery/local_delivery_result_db.jsp?invoice_no=%s"
	);

	/** 운송장 번호가 없으면(아직 발송 전) null — 버튼이 비활성인 상태와 같다 */
	public String resolve(CourierCode courierCode, String trackingNo) {
		if (courierCode == null || trackingNo == null) {
			return null;
		}
		String encoded = URLEncoder.encode(trackingNo, StandardCharsets.UTF_8);
		return URL_TEMPLATES.get(courierCode).formatted(encoded);
	}
}
