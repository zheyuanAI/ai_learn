package com.ailearn.platform.core.stocktake.application;

import com.ailearn.platform.core.stocktake.dto.StocktakeConfirmRequest;
import com.ailearn.platform.core.stocktake.dto.StocktakeCreateRequest;
import com.ailearn.platform.core.stocktake.dto.StocktakeView;
import java.util.UUID;

/**
 * 盘点应用服务端口。
 */
public interface StocktakeApplicationService {

    /**
     * 创建未盘点盘点单。
     *
     * @param request 创建请求
     * @param idempotencyKey 幂等键
     * @return 盘点单视图
     */
    StocktakeView create(StocktakeCreateRequest request, String idempotencyKey);

    /**
     * 开始盘点并冻结库存系统快照。
     *
     * @param id 盘点单 ID
     * @param idempotencyKey 幂等键
     * @return 盘点中视图
     */
    StocktakeView start(UUID id, String idempotencyKey);

    /**
     * 确认实盘数量并执行差异调整。
     *
     * @param id 盘点单 ID
     * @param request 确认请求
     * @param idempotencyKey 幂等键
     * @return 已确认视图
     */
    StocktakeView confirm(UUID id, StocktakeConfirmRequest request, String idempotencyKey);
}
