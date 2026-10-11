package com.tenure.domain.report.mapper;

import com.tenure.domain.report.dto.response.ReportResponse;
import com.tenure.domain.report.entity.Report;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/** 엔티티 → 응답 단방향 변환. */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface ReportMapper {

	ReportResponse toResponse(Report report);
}
