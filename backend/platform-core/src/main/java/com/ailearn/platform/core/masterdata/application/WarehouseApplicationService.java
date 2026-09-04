package com.ailearn.platform.core.masterdata.application;

import com.ailearn.platform.core.masterdata.dto.MasterDataPageQuery;
import com.ailearn.platform.core.masterdata.dto.MasterDataPageResult;
import com.ailearn.platform.core.masterdata.dto.StatusChangeRequest;
import com.ailearn.platform.core.masterdata.dto.WarehouseSaveRequest;
import com.ailearn.platform.core.masterdata.dto.WarehouseView;
import java.util.UUID;

/**
 * 仓库主数据应用服务。
 */
public interface WarehouseApplicationService {

    /**
     * 分页查询当前租户仓库。
     *
     * @param query 分页查询参数
     * @return 当前租户分页结果
     */
    MasterDataPageResult<WarehouseView> page(MasterDataPageQuery query);

    /**
     * 查询当前租户仓库详情。
     *
     * @param id 仓库 ID
     * @return 仓库详情
     */
    WarehouseView detail(UUID id);

    /**
     * 创建仓库。
     *
     * @param request 创建请求
     * @return 创建后的详情
     */
    WarehouseView create(WarehouseSaveRequest request);
    WarehouseView create(WarehouseSaveRequest request, String idempotencyKey);

    /**
     * 修改仓库。
     *
     * @param id 仓库 ID
     * @param request 修改请求
     * @return 修改后的详情
     */
    WarehouseView update(UUID id, WarehouseSaveRequest request);
    WarehouseView update(UUID id, WarehouseSaveRequest request, String idempotencyKey);

    /**
     * 启用或停用仓库。
     *
     * @param id 仓库 ID
     * @param request 目标状态
     * @return 状态变更后的详情
     */
    WarehouseView changeStatus(UUID id, StatusChangeRequest request);
    WarehouseView changeStatus(UUID id, StatusChangeRequest request, String idempotencyKey);

    /**
     * 逻辑删除仓库。
     *
     * @param id 仓库 ID
     */
    void delete(UUID id);
    void delete(UUID id, String idempotencyKey);
}
