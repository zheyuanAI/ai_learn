package com.ailearn.platform.core.masterdata.application;

import com.ailearn.platform.core.masterdata.dto.MasterDataPageQuery;
import com.ailearn.platform.core.masterdata.dto.MasterDataPageResult;
import com.ailearn.platform.core.masterdata.dto.ProductSaveRequest;
import com.ailearn.platform.core.masterdata.dto.ProductView;
import com.ailearn.platform.core.masterdata.dto.StatusChangeRequest;
import java.util.UUID;

/**
 * 商品主数据应用服务。
 */
public interface ProductApplicationService {

    /**
     * 分页查询当前租户商品。
     *
     * @param query 分页查询参数
     * @return 当前租户分页结果
     */
    MasterDataPageResult<ProductView> page(MasterDataPageQuery query);

    /**
     * 查询当前租户商品详情。
     *
     * @param id 商品 ID
     * @return 商品详情
     */
    ProductView detail(UUID id);

    /**
     * 创建商品。
     *
     * @param request 创建请求
     * @return 创建后的详情
     */
    ProductView create(ProductSaveRequest request);
    ProductView create(ProductSaveRequest request, String idempotencyKey);

    /**
     * 修改商品。
     *
     * @param id 商品 ID
     * @param request 修改请求
     * @return 修改后的详情
     */
    ProductView update(UUID id, ProductSaveRequest request);
    ProductView update(UUID id, ProductSaveRequest request, String idempotencyKey);

    /**
     * 启用或停用商品。
     *
     * @param id 商品 ID
     * @param request 目标状态
     * @return 状态变更后的详情
     */
    ProductView changeStatus(UUID id, StatusChangeRequest request);
    ProductView changeStatus(UUID id, StatusChangeRequest request, String idempotencyKey);

    /**
     * 逻辑删除商品。
     *
     * @param id 商品 ID
     */
    void delete(UUID id);
    void delete(UUID id, String idempotencyKey);
}
