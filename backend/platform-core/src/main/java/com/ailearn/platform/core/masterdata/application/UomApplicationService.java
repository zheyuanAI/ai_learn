package com.ailearn.platform.core.masterdata.application;

import com.ailearn.platform.core.masterdata.dto.MasterDataPageQuery;
import com.ailearn.platform.core.masterdata.dto.MasterDataPageResult;
import com.ailearn.platform.core.masterdata.dto.StatusChangeRequest;
import com.ailearn.platform.core.masterdata.dto.UomSaveRequest;
import com.ailearn.platform.core.masterdata.dto.UomView;
import java.util.UUID;

/**
 * 计量单位主数据应用服务。
 */
public interface UomApplicationService {

    /**
     * 分页查询当前租户的计量单位。
     *
     * @param query 分页查询参数
     * @return 当前租户分页结果
     */
    MasterDataPageResult<UomView> page(MasterDataPageQuery query);

    /**
     * 查询当前租户计量单位详情。
     *
     * @param id 计量单位 ID
     * @return 计量单位详情
     */
    UomView detail(UUID id);

    /**
     * 创建计量单位。
     *
     * @param request 创建请求
     * @return 创建后的详情
     */
    UomView create(UomSaveRequest request);
    UomView create(UomSaveRequest request, String idempotencyKey);

    /**
     * 修改计量单位。
     *
     * @param id 计量单位 ID
     * @param request 修改请求
     * @return 修改后的详情
     */
    UomView update(UUID id, UomSaveRequest request);
    UomView update(UUID id, UomSaveRequest request, String idempotencyKey);

    /**
     * 启用或停用计量单位。
     *
     * @param id 计量单位 ID
     * @param request 目标状态
     * @return 状态变更后的详情
     */
    UomView changeStatus(UUID id, StatusChangeRequest request);
    UomView changeStatus(UUID id, StatusChangeRequest request, String idempotencyKey);

    /**
     * 逻辑删除计量单位。
     *
     * @param id 计量单位 ID
     */
    void delete(UUID id);
    void delete(UUID id, String idempotencyKey);
}
