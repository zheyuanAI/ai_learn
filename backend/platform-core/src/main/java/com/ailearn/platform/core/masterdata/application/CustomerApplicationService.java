package com.ailearn.platform.core.masterdata.application;

import com.ailearn.platform.core.masterdata.dto.CustomerSaveRequest;
import com.ailearn.platform.core.masterdata.dto.CustomerView;
import com.ailearn.platform.core.masterdata.dto.MasterDataPageQuery;
import com.ailearn.platform.core.masterdata.dto.MasterDataPageResult;
import com.ailearn.platform.core.masterdata.dto.StatusChangeRequest;
import java.util.UUID;

/**
 * 客户主数据应用服务。
 */
public interface CustomerApplicationService {

    /**
     * 分页查询当前租户客户。
     *
     * @param query 分页查询参数
     * @return 当前租户分页结果
     */
    MasterDataPageResult<CustomerView> page(MasterDataPageQuery query);

    /**
     * 查询当前租户客户详情。
     *
     * @param id 客户 ID
     * @return 客户详情
     */
    CustomerView detail(UUID id);

    /**
     * 创建客户。
     *
     * @param request 创建请求
     * @return 创建后的详情
     */
    CustomerView create(CustomerSaveRequest request);
    CustomerView create(CustomerSaveRequest request, String idempotencyKey);

    /**
     * 修改客户。
     *
     * @param id 客户 ID
     * @param request 修改请求
     * @return 修改后的详情
     */
    CustomerView update(UUID id, CustomerSaveRequest request);
    CustomerView update(UUID id, CustomerSaveRequest request, String idempotencyKey);

    /**
     * 启用或停用客户。
     *
     * @param id 客户 ID
     * @param request 目标状态
     * @return 状态变更后的详情
     */
    CustomerView changeStatus(UUID id, StatusChangeRequest request);
    CustomerView changeStatus(UUID id, StatusChangeRequest request, String idempotencyKey);

    /**
     * 逻辑删除客户。
     *
     * @param id 客户 ID
     */
    void delete(UUID id);
    void delete(UUID id, String idempotencyKey);
}
