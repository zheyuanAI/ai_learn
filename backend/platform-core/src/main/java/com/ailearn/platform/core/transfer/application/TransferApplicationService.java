package com.ailearn.platform.core.transfer.application;

import com.ailearn.platform.core.transfer.dto.TransferCreateRequest;
import com.ailearn.platform.core.transfer.dto.TransferView;
import java.util.UUID;

/**
 * 调拨应用服务端口。
 */
public interface TransferApplicationService {

    /**
     * 创建调拨草稿。
     *
     * @param request 创建请求
     * @param idempotencyKey 幂等键
     * @return 调拨草稿视图
     */
    TransferView create(TransferCreateRequest request, String idempotencyKey);

    /**
     * 确认调拨并执行库存移动。
     *
     * @param id 调拨单 ID
     * @param idempotencyKey 幂等键
     * @return 已确认调拨视图
     */
    TransferView confirm(UUID id, String idempotencyKey);
}
