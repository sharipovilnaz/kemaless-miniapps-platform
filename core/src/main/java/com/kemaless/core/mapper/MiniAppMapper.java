package com.kemaless.core.mapper;

import com.kemaless.core.dto.request.CreateMiniAppRequest;
import com.kemaless.core.dto.request.UpdateMiniAppRequest;
import com.kemaless.core.dto.response.MiniAppResponse;
import com.kemaless.core.model.MiniApp;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.util.List;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface MiniAppMapper {

    MiniAppResponse toResponse(MiniApp entity);

    List<MiniAppResponse> toResponseList(List<MiniApp> entities);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "baseUrl", source = "defaultEntryUrl")
    @Mapping(target = "entryPath", constant = "/")
    @Mapping(target = "themeColor", ignore = true)
    @Mapping(target = "allowedRoles", ignore = true)
    MiniApp toEntity(CreateMiniAppRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "code", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "baseUrl", source = "defaultEntryUrl")
    @Mapping(target = "entryPath", ignore = true)
    @Mapping(target = "themeColor", ignore = true)
    @Mapping(target = "allowedRoles", ignore = true)
    void updateEntityFromRequest(UpdateMiniAppRequest request, @MappingTarget MiniApp entity);
}
