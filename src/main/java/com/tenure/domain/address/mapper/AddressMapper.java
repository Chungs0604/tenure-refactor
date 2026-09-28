package com.tenure.domain.address.mapper;

import com.tenure.domain.address.dto.response.ShippingAddressResponse;
import com.tenure.domain.address.entity.ShippingAddress;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/** 엔티티 → 응답 단방향 변환. */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface AddressMapper {

	ShippingAddressResponse toShippingAddressResponse(ShippingAddress address);

	List<ShippingAddressResponse> toShippingAddressResponses(List<ShippingAddress> addresses);
}
