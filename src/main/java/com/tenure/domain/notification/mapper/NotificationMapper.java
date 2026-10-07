package com.tenure.domain.notification.mapper;

import com.tenure.domain.notification.dto.response.NotificationResponse;
import com.tenure.domain.notification.dto.response.NotificationSettingResponse;
import com.tenure.domain.notification.entity.Notification;
import com.tenure.domain.notification.enums.NotificationSettingType;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * 엔티티 → 응답 단방향 변환.
 *
 * 저장소 키를 URL 로 바꾸는 일과 대상 삭제 여부를 확인하는 일은 Service 가 끝내고 넘김 — 여기는
 * 이름이 맞는 값을 그대로 옮기기만 함
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface NotificationMapper {

	@Mapping(target = "id", source = "notification.id")
	@Mapping(target = "category", source = "notification.category")
	@Mapping(target = "imageUrl", source = "imageUrl")
	@Mapping(target = "title", source = "notification.title")
	@Mapping(target = "body", source = "notification.body")
	@Mapping(target = "targetType", source = "notification.targetType")
	@Mapping(target = "targetId", source = "notification.targetId")
	@Mapping(target = "targetDeleted", source = "targetDeleted")
	@Mapping(target = "readAt", source = "notification.readAt")
	@Mapping(target = "createdAt", source = "notification.createdAt")
	NotificationResponse toResponse(Notification notification, String imageUrl, Boolean targetDeleted);

	@Mapping(target = "settingType", source = "settingType")
	@Mapping(target = "group", expression = "java(settingType.getGroup().getLabel())")
	@Mapping(target = "title", source = "settingType.title")
	@Mapping(target = "description", source = "settingType.description")
	@Mapping(target = "enabled", source = "enabled")
	NotificationSettingResponse toSettingResponse(NotificationSettingType settingType, Boolean enabled);
}
