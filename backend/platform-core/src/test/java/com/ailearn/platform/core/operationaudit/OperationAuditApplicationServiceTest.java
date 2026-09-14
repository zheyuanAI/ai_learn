package com.ailearn.platform.core.operationaudit;

import com.ailearn.platform.core.operationaudit.application.OperationAuditApplicationService;
import com.ailearn.platform.core.operationaudit.application.OperationAuditCommand;
import com.ailearn.platform.core.operationaudit.application.OperationAuditStore;
import com.ailearn.platform.core.operationaudit.domain.OperationAuditEntry;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** Core 操作审计应用服务测试。 */
class OperationAuditApplicationServiceTest {

    /** 成功记录必须保留可信身份，并且只保存原始幂等键的 SHA-256 摘要。 */
    @Test
    void shouldPersistStructuredSuccessWithHashedIdempotencyKey() {
        OperationAuditStore store = mock(OperationAuditStore.class);
        OperationAuditApplicationService service = new OperationAuditApplicationService(store);
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        OffsetDateTime occurredAt = OffsetDateTime.of(2026, 9, 12, 1, 2, 3, 0, ZoneOffset.UTC);

        service.recordSuccess(new OperationAuditCommand(tenantId, "USER", userId, "sales-user",
                "jti-raw", "request-1", "sales:order:submit", "SALES_ORDER", orderId,
                "SO-001", "Draft", "Submitted", "提交销售订单", "status",
                "raw-idempotency-key", occurredAt));

        ArgumentCaptor<OperationAuditEntry> captor = ArgumentCaptor.forClass(OperationAuditEntry.class);
        verify(store).save(captor.capture());
        OperationAuditEntry entry = captor.getValue();
        assertEquals(tenantId, entry.tenantId());
        assertEquals(userId, entry.actorId());
        assertEquals("sales-user", entry.actorAccount());
        assertEquals("jti-raw", entry.sessionId());
        assertEquals("SUCCESS", entry.result().name());
        assertEquals(64, entry.idempotencyKeyHash().length());
        assertNotEquals("raw-idempotency-key", entry.idempotencyKeyHash());
        assertNull(entry.errorReason());
        assertEquals(occurredAt, entry.occurredAt());
    }
}
