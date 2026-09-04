package com.ailearn.platform.core.quality.application;

import com.ailearn.platform.core.quality.dto.QualityDispositionConfirmRequest;
import com.ailearn.platform.core.quality.dto.QualityDispositionRequest;
import com.ailearn.platform.core.quality.dto.QualityDispositionView;
import com.ailearn.platform.core.quality.dto.QualityInspectionRequest;
import com.ailearn.platform.core.quality.dto.QualityInspectionView;
import java.util.List;
import java.util.UUID;

/**
 * 采购到货质检、质量处置决定和仓库执行应用端口。
 */
public interface PurchaseQualityApplicationService {

    QualityInspectionView inspect(UUID receiptId, QualityInspectionRequest request, String idempotencyKey);

    QualityDispositionView release(UUID receiptId, QualityDispositionRequest request, String idempotencyKey);

    QualityDispositionView returnToSupplier(UUID receiptId, QualityDispositionRequest request, String idempotencyKey);

    QualityDispositionView scrap(UUID receiptId, QualityDispositionRequest request, String idempotencyKey);

    QualityDispositionView confirmDisposition(UUID dispositionId,
                                              QualityDispositionConfirmRequest request,
                                              String idempotencyKey);

    List<QualityInspectionView> listInspections();

    List<QualityDispositionView> listDispositions();
}
