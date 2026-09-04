package com.ailearn.platform.core.masterdata.application;

import com.ailearn.platform.core.masterdata.dto.MasterDataPageQuery;
import com.ailearn.platform.core.masterdata.dto.MasterDataPageResult;
import com.ailearn.platform.core.masterdata.dto.StatusChangeRequest;
import com.ailearn.platform.core.masterdata.dto.SupplierSaveRequest;
import com.ailearn.platform.core.masterdata.dto.SupplierView;
import java.util.UUID;

/**
 * 供应商主数据应用服务。
 */
public interface SupplierApplicationService {

    /**
     * 分页查询当前租户供应商。
     *
     * @param query 分页查询参数
     * @return 当前租户分页结果
     */
    MasterDataPageResult<SupplierView> page(MasterDataPageQuery query);

    /**
     * 查询当前租户供应商详情。
     *
     * @param id 供应商 ID
     * @return 供应商详情
     */
    SupplierView detail(UUID id);

    /**
     * 创建供应商。
     *
     * @param request 创建请求
     * @return 创建后的详情
     */
    SupplierView create(SupplierSaveRequest request);
    SupplierView create(SupplierSaveRequest request, String idempotencyKey);

    /**
     * 修改供应商。
     *
     * @param id 供应商 ID
     * @param request 修改请求
     * @return 修改后的详情
     */
    SupplierView update(UUID id, SupplierSaveRequest request);
    SupplierView update(UUID id, SupplierSaveRequest request, String idempotencyKey);

    /**
     * 启用或停用供应商。
     *
     * @param id 供应商 ID
     * @param request 目标状态
     * @return 状态变更后的详情
     */
    SupplierView changeStatus(UUID id, StatusChangeRequest request);
    SupplierView changeStatus(UUID id, StatusChangeRequest request, String idempotencyKey);

    /**
     * 逻辑删除供应商。
     *
     * @param id 供应商 ID
     */
    void delete(UUID id);
    void delete(UUID id, String idempotencyKey);
}
