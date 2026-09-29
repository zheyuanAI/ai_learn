package com.ailearn.platform.core.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ailearn.platform.core.inventory.application.InventoryBalanceQuery;
import com.ailearn.platform.core.inventory.infrastructure.InventoryAllocationMapper;
import com.ailearn.platform.core.inventory.infrastructure.InventoryBalanceMapper;
import com.ailearn.platform.core.inventory.infrastructure.InventoryReservationMapper;
import com.ailearn.platform.core.inventory.infrastructure.InventoryTransactionMapper;
import com.ailearn.platform.core.inventory.infrastructure.PostgresInventoryRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** 库存分页边界测试；只检查 Mapper 参数，不连接数据库。 */
class InventoryPaginationOffsetTest {

    private static final UUID TENANT_ID = UUID.fromString("a0000000-0000-0000-0000-000000000001");

    /**
     * 用途：验证余额查询在极大页码下不向 Mapper 传入负偏移量。
     * 入参：最大 int 页码及 1000 条页大小；出参：Mapper 收到未截断的 long 偏移量。
     */
    @Test
    void largeBalancePageDoesNotOverflowOffset() {
        InventoryBalanceMapper balanceMapper = mock(InventoryBalanceMapper.class);
        PostgresInventoryRepository repository = new PostgresInventoryRepository(balanceMapper,
                mock(InventoryReservationMapper.class), mock(InventoryAllocationMapper.class),
                mock(InventoryTransactionMapper.class));
        when(balanceMapper.selectPage(eq(TENANT_ID), isNull(), isNull(), isNull(), isNull(),
                eq(1000), anyLong())).thenReturn(List.of());

        repository.queryBalances(TENANT_ID,
                new InventoryBalanceQuery(TENANT_ID, null, null, null, null, Integer.MAX_VALUE, 1000));

        ArgumentCaptor<Long> offset = ArgumentCaptor.forClass(Long.class);
        verify(balanceMapper).selectPage(eq(TENANT_ID), isNull(), isNull(), isNull(), isNull(),
                eq(1000), offset.capture());
        assertEquals(2_147_483_646_000L, offset.getValue());
    }
}
