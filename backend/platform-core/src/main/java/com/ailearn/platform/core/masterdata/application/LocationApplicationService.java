package com.ailearn.platform.core.masterdata.application;

import com.ailearn.platform.core.masterdata.dto.LocationSaveRequest;
import com.ailearn.platform.core.masterdata.dto.LocationView;
import com.ailearn.platform.core.masterdata.dto.MasterDataPageQuery;
import com.ailearn.platform.core.masterdata.dto.MasterDataPageResult;
import com.ailearn.platform.core.masterdata.dto.StatusChangeRequest;
import java.util.UUID;

/**
 * 库位主数据应用服务。
 */
public interface LocationApplicationService {

    /**
     * 分页查询当前租户库位。
     *
     * @param query 分页查询参数
     * @return 当前租户分页结果
     */
    MasterDataPageResult<LocationView> page(MasterDataPageQuery query);

    /**
     * 查询当前租户库位详情。
     *
     * @param id 库位 ID
     * @return 库位详情
     */
    LocationView detail(UUID id);

    /**
     * 创建库位。
     *
     * @param request 创建请求
     * @return 创建后的详情
     */
    LocationView create(LocationSaveRequest request);
    LocationView create(LocationSaveRequest request, String idempotencyKey);

    /**
     * 修改库位。
     *
     * @param id 库位 ID
     * @param request 修改请求
     * @return 修改后的详情
     */
    LocationView update(UUID id, LocationSaveRequest request);
    LocationView update(UUID id, LocationSaveRequest request, String idempotencyKey);

    /**
     * 启用或停用库位。
     *
     * @param id 库位 ID
     * @param request 目标状态
     * @return 状态变更后的详情
     */
    LocationView changeStatus(UUID id, StatusChangeRequest request);
    LocationView changeStatus(UUID id, StatusChangeRequest request, String idempotencyKey);

    /**
     * 逻辑删除库位。
     *
     * @param id 库位 ID
     */
    void delete(UUID id);
    void delete(UUID id, String idempotencyKey);
}
