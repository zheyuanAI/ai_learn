package com.ailearn.platform.core.operationaudit;

import com.ailearn.platform.core.operationaudit.application.OperationAuditQuery;
import com.ailearn.platform.core.operationaudit.domain.OperationAuditEntry;
import com.ailearn.platform.core.operationaudit.infrastructure.PostgresOperationAuditStore;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Core 操作审计 PostgreSQL 适配器的租户隔离查询测试。 */
class PostgresOperationAuditStoreTest {

    /** 查询 SQL 必须固定包含租户、实体类型、实体 ID 和逻辑删除过滤。 */
    @SuppressWarnings("unchecked")
    @Test
    void shouldAlwaysFilterByTrustedTenantAndEntity() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.<OperationAuditEntry>of());
        PostgresOperationAuditStore store = new PostgresOperationAuditStore(jdbcTemplate);
        UUID tenantId = UUID.randomUUID();
        UUID entityId = UUID.randomUUID();

        store.find(new OperationAuditQuery(tenantId, "SALES_ORDER", entityId, null, null, 50));

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> argumentsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).query(sqlCaptor.capture(), any(RowMapper.class), argumentsCaptor.capture());
        String sql = sqlCaptor.getValue().toLowerCase();
        assertTrue(sql.contains("tenant_id = ?"));
        assertTrue(sql.contains("entity_type = ?"));
        assertTrue(sql.contains("entity_id = ?"));
        assertTrue(sql.contains("isdel = 0"));
        assertEquals(tenantId, argumentsCaptor.getValue()[0]);
        assertEquals("SALES_ORDER", argumentsCaptor.getValue()[1]);
        assertEquals(entityId, argumentsCaptor.getValue()[2]);
        assertEquals(50, argumentsCaptor.getValue()[3]);
    }
}
