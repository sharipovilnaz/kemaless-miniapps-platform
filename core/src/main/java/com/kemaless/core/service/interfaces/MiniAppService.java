package com.kemaless.core.service.interfaces;

import com.kemaless.core.dto.request.CreateMiniAppRequest;
import com.kemaless.core.dto.request.UpdateMiniAppRequest;
import com.kemaless.core.dto.response.MiniAppResponse;
import com.kemaless.core.dto.response.PageResponse;
import org.springframework.data.domain.Pageable;

public interface MiniAppService {

    MiniAppResponse create(CreateMiniAppRequest request);

    PageResponse<MiniAppResponse> findAll(String search, Pageable pageable);

    MiniAppResponse getByCode(String code);

    MiniAppResponse update(String code, UpdateMiniAppRequest request);

    void delete(String code);
}
