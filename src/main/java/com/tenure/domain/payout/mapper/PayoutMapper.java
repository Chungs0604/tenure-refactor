package com.tenure.domain.payout.mapper;

import com.tenure.domain.payout.dto.response.PayoutAccountResponse;
import com.tenure.domain.payout.entity.PayoutAccount;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/** 엔티티 → 응답 단방향 변환. */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface PayoutMapper {

	/** 표시명은 enum 이 들고 있다 — 화면이 한글을 하드코딩하지 않도록 함께 내려준다. */
	@Mapping(target = "bankName", expression = "java(account.getBankCode().getBankName())")
	PayoutAccountResponse toPayoutAccountResponse(PayoutAccount account);

	List<PayoutAccountResponse> toPayoutAccountResponses(List<PayoutAccount> accounts);
}
