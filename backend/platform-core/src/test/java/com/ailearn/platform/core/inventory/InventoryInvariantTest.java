package com.ailearn.platform.core.inventory;

import com.ailearn.platform.core.inventory.application.InventoryCommandMetadata;
import com.ailearn.platform.core.inventory.domain.InventoryBalance;
import com.ailearn.platform.core.inventory.domain.InventoryDimension;
import com.ailearn.platform.core.inventory.domain.InventoryInvariant;
import com.ailearn.platform.core.inventory.domain.LocationType;
import com.ailearn.platform.shared.exception.ValidationException;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 库存内核纯领域规则测试，不连接数据库、Redis 或开发环境服务。
 */
class InventoryInvariantTest {

    private static final UUID TENANT_ID = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final UUID PRODUCT_ID = UUID.fromString("b0000000-0000-0000-0000-000000000001");
    private static final UUID WAREHOUSE_ID = UUID.fromString("c0000000-0000-0000-0000-000000000001");
    private static final UUID LOCATION_ID = UUID.fromString("d0000000-0000-0000-0000-000000000001");
    private static final UUID USER_ID = UUID.fromString("e0000000-0000-0000-0000-000000000001");

    @Test
    void availableQuantityMustBeOnHandMinusReservedAndQualityHoldCannotAllocate() {
        InventoryDimension dimension = new InventoryDimension(PRODUCT_ID, WAREHOUSE_ID, LOCATION_ID, "");
        InventoryBalance balance = new InventoryBalance(
                UUID.randomUUID(), TENANT_ID, dimension,
                new BigDecimal("10.000000"), new BigDecimal("3.000000"), 4L,
                OffsetDateTime.parse("2026-09-03T10:00:00+08:00"));

        assertEquals(new BigDecimal("7.000000"), balance.availableQty());
        assertEquals(new BigDecimal("7.000000"), balance.allocatableQty(LocationType.Storage));
        assertEquals(BigDecimal.ZERO.setScale(6), balance.allocatableQty(LocationType.QualityHold));
    }

    @Test
    void dimensionsMustHaveStableOrderingKey() {
        InventoryDimension first = new InventoryDimension(PRODUCT_ID, WAREHOUSE_ID, LOCATION_ID, "");
        InventoryDimension second = new InventoryDimension(PRODUCT_ID, WAREHOUSE_ID,
                UUID.fromString("d0000000-0000-0000-0000-000000000002"), "LOT-2");

        assertEquals(first.lockKey(TENANT_ID).compareTo(second.lockKey(TENANT_ID)) < 0, true);
        assertEquals(first.normalizedLotNo(), "");
        assertEquals(second.normalizedLotNo(), "LOT-2");
    }

    @Test
    void quantityMustBePositiveAndFitNumericContract() {
        assertThrows(ValidationException.class,
                () -> InventoryInvariant.requirePositive("quantity", BigDecimal.ZERO));
        assertThrows(ValidationException.class,
                () -> InventoryInvariant.requirePositive("quantity", new BigDecimal("1.1234567")));
        assertEquals(new BigDecimal("1.250000"),
                InventoryInvariant.requirePositive("quantity", new BigDecimal("1.25")));
    }

    @Test
    void commandMetadataMustCarryAuditAndIdempotencyFields() {
        InventoryCommandMetadata incomplete = new InventoryCommandMetadata(
                TENANT_ID, USER_ID, "jti-1", "request-1", "", "sha256",
                "PURCHASE_RECEIPT", UUID.randomUUID(), null, "RECEIPT", 
                OffsetDateTime.parse("2026-09-03T10:00:00+08:00"));

        assertThrows(ValidationException.class, incomplete::validate);
    }
}
