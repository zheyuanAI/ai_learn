package com.ailearn.platform.core.manufacturing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ailearn.platform.core.manufacturing.dispatch.application.DispatchApplicationServiceImpl;
import com.ailearn.platform.core.manufacturing.dispatch.domain.DispatchOrder;
import com.ailearn.platform.core.manufacturing.dispatch.domain.DispatchRepository;
import com.ailearn.platform.core.manufacturing.dispatch.dto.DispatchPageQuery;
import com.ailearn.platform.core.manufacturing.dispatch.port.WorkOrderReleasePort;
import com.ailearn.platform.core.manufacturing.foundation.application.ManufacturingFoundationServiceImpl;
import com.ailearn.platform.core.manufacturing.foundation.domain.BomComponentFact;
import com.ailearn.platform.core.manufacturing.foundation.domain.BomFact;
import com.ailearn.platform.core.manufacturing.foundation.domain.BomStatus;
import com.ailearn.platform.core.manufacturing.foundation.domain.FoundationRepository;
import com.ailearn.platform.core.manufacturing.foundation.dto.ManufacturingPageQuery;
import com.ailearn.platform.shared.context.RequestContextHolder;
import com.ailearn.platform.shared.context.TenantContextHolder;
import com.ailearn.platform.shared.context.UserContextHolder;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** 制造分页偏移回归：只使用内存 mock，不启动服务或连接数据库。 */
class ManufacturingPaginationOffsetTest {
    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();
    private static final UUID PRODUCT = UUID.randomUUID();

    @AfterEach
    void clearContext() {
        UserContextHolder.clear();
        RequestContextHolder.clear();
    }

    /** 验证派工页偏移不在 int 乘法溢出时回绕；入参为极大页码，出参为空页并保留总数。 */
    @Test
    void dispatchHugePageDoesNotWrapToFirstPage() {
        bindContext();
        DispatchRepository repository = mock(DispatchRepository.class);
        DispatchOrder order = DispatchOrder.draft(UUID.randomUUID(), TENANT, UUID.randomUUID(),
                UUID.randomUUID(), null, USER, OffsetDateTime.now());
        when(repository.findAll(TENANT)).thenReturn(List.of(order));
        DispatchApplicationServiceImpl service = new DispatchApplicationServiceImpl(repository,
                mock(WorkOrderReleasePort.class));
        DispatchPageQuery first = new DispatchPageQuery();
        first.setSize(2);
        assertEquals(1, service.page(first).records().size());

        DispatchPageQuery huge = new DispatchPageQuery();
        huge.setPage(1_073_741_825); // (page - 1) * 2 在 int 中恰好回绕为 0。
        huge.setSize(2);
        var result = service.page(huge);
        assertTrue(result.records().isEmpty());
        assertEquals(1, result.total());
        assertEquals(huge.getPage(), result.page());
    }

    /** 验证 BOM、Routing、工单共用的分页函数不回绕；入参为溢出页码，出参为空页。 */
    @Test
    void foundationHugePageDoesNotWrapToFirstPage() {
        bindContext();
        FoundationRepository repository = mock(FoundationRepository.class);
        BomFact bom = new BomFact(UUID.randomUUID(), TENANT, PRODUCT, "BOM-1", "V1",
                BomStatus.ACTIVE, List.of(new BomComponentFact(UUID.randomUUID(), BigDecimal.ONE,
                "PCS", BigDecimal.ZERO)), false, USER, OffsetDateTime.now());
        when(repository.findBoms(TENANT)).thenReturn(List.of(bom));
        ManufacturingFoundationServiceImpl service = new ManufacturingFoundationServiceImpl(repository);
        ManufacturingPageQuery first = new ManufacturingPageQuery();
        first.setSize(2);
        assertEquals(1, service.listBoms(first).getRecords().size());

        ManufacturingPageQuery huge = new ManufacturingPageQuery();
        huge.setPage(1_073_741_825);
        huge.setSize(2);
        var result = service.listBoms(huge);
        assertTrue(result.getRecords().isEmpty());
        assertEquals(1, result.getTotal());
        assertEquals(huge.getPage(), result.getPage());
    }

    /** 绑定可信租户和用户；入参和出参均为空，流程不涉及持久化。 */
    private void bindContext() {
        TenantContextHolder.setTenantId(TENANT);
        RequestContextHolder.getContext().setUserId(USER);
        RequestContextHolder.getContext().setJti("pagination-test-session");
        RequestContextHolder.getContext().setRequestId("pagination-test-request");
    }
}
