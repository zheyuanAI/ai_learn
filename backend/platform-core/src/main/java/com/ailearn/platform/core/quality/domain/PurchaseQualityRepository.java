package com.ailearn.platform.core.quality.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 采购质量事实持久化端口；只读复用采购收货事实，质量写入仅落质量表。
 */
public interface PurchaseQualityRepository {

    /** 按可信租户读取已确认收货单。 */
    Optional<QualityReceiptFact> findReceipt(UUID tenantId, UUID receiptId, boolean forUpdate);

    /** 按可信租户读取质检事实。 */
    Optional<QualityInspectionFact> findInspection(UUID tenantId, UUID inspectionId, boolean forUpdate);

    /** 读取某收货明细的全部质检事实。 */
    List<QualityInspectionFact> findInspectionsByLine(UUID tenantId, UUID receiptLineId, boolean forUpdate);

    /** 写入一条质检事实。 */
    QualityInspectionFact insertInspection(QualityInspectionFact inspection);

    /** 读取某质检事实的全部处置决定；写操作时由实现锁定行。 */
    List<QualityDispositionFact> findDispositionsByInspection(UUID tenantId, UUID inspectionId, boolean forUpdate);

    /** 按可信租户读取单条处置决定。 */
    Optional<QualityDispositionFact> findDisposition(UUID tenantId, UUID dispositionId, boolean forUpdate);

    /** 写入待执行处置决定。 */
    QualityDispositionFact insertDisposition(QualityDispositionFact disposition);

    /** 锁定处置并写入仓库执行审计。 */
    QualityDispositionFact completeDisposition(QualityDispositionFact disposition,
                                               UUID operatorId,
                                               java.time.OffsetDateTime executedAt,
                                               UUID inventoryTransactionId);

    /** 查询当前租户的质检事实。 */
    List<QualityInspectionFact> listInspections(UUID tenantId);

    /** 查询当前租户的处置事实。 */
    List<QualityDispositionFact> listDispositions(UUID tenantId);
}
