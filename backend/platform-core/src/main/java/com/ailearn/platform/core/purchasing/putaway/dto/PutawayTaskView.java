package com.ailearn.platform.core.purchasing.putaway.dto;

import com.ailearn.platform.core.purchasing.putaway.domain.PutawayTaskFact;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 上架任务接口视图。
 */
public record PutawayTaskView(
        UUID id,
        String taskNo,
        UUID purchaseReceiptId,
        UUID purchaseReceiptLineId,
        UUID productId,
        UUID fromLocationId,
        UUID toLocationId,
        BigDecimal putawayQty,
        String status,
        UUID confirmedBy,
        OffsetDateTime confirmedAt,
        UUID inventoryTransactionId) {

    /**
     * 从持久化事实转换为接口视图。
     */
    public static PutawayTaskView of(PutawayTaskFact task) {
        return new PutawayTaskView(task.id(), task.taskNo(), task.purchaseReceiptId(), task.purchaseReceiptLineId(),
                task.productId(), task.fromLocationId(), task.toLocationId(), task.putawayQty(), task.status(),
                task.confirmedBy(), task.confirmedAt(), task.inventoryTransactionId());
    }
}
