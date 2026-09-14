package com.ailearn.platform.core.inventory.infrastructure;

import com.ailearn.platform.core.inventory.application.LowStockItem;
import com.ailearn.platform.core.inventory.application.LowStockPage;
import com.ailearn.platform.core.inventory.application.LowStockQueryService;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** 基于 PostgreSQL 聚合投影实现低库存只读查询。 */
@Service
public class PostgresLowStockQueryService implements LowStockQueryService {
    private final LowStockMapper mapper;

    /** 注入低库存只读 Mapper。 */
    public PostgresLowStockQueryService(LowStockMapper mapper) {
        this.mapper = mapper;
    }

    /** 校验上限后多取一条判断截断，不读取或修改任何库存命令事实。 */
    @Override
    public LowStockPage query(UUID tenantId, UUID warehouseId, int limit) {
        if (tenantId == null) {
            throw new IllegalArgumentException("可信租户不能为空");
        }
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("limit 必须在 1 到 100 之间");
        }
        List<LowStockRow> rows = mapper.selectLowStock(tenantId, warehouseId, limit + 1);
        boolean truncated = rows.size() > limit;
        List<LowStockItem> records = rows.stream().limit(limit).map(PostgresLowStockQueryService::toItem).toList();
        return new LowStockPage(records, truncated);
    }

    private static LowStockItem toItem(LowStockRow row) {
        return new LowStockItem(row.getProductId(), row.getSku(), row.getProductName(), row.getUom(),
                row.getSafetyStock(), row.getAvailableQty(), row.getShortfallQty(), row.getLastTransactionAt());
    }
}
