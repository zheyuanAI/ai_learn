package com.ailearn.platform.iot.telemetry;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ailearn.platform.iot.telemetry.domain.DeviceStatus;
import com.ailearn.platform.iot.telemetry.infrastructure.PostgresTelemetryStore;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class PostgresTelemetryStatusConcurrencyTest {

    /**
     * 用途：保护独立告警写入的快照不被并发遥测使用过期值覆盖；无业务入参，出参为断言结果。
     * 流程：模拟已有设备状态的 UPSERT，检查冲突更新只推进遥测字段，不覆盖 alarm_status。
     */
    @Test
    void newerTelemetryMustNotOverwriteIndependentlyUpdatedAlarmStatus() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        UUID tenantId = UUID.randomUUID();
        UUID deviceId = UUID.randomUUID();
        OffsetDateTime time = OffsetDateTime.parse("2026-09-04T10:00:00Z");
        DeviceStatus candidate = new DeviceStatus(tenantId, deviceId, "Online", "Running", "Normal",
                time, "message:2", time);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of(candidate));

        new PostgresTelemetryStore(jdbc).updateIfNewer(candidate);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).update(sql.capture(), any(Object[].class));
        String conflictUpdate = sql.getValue().split("ON CONFLICT \\(tenant_id, device_id\\) DO UPDATE", 2)[1];
        assertFalse(conflictUpdate.contains("alarm_status ="),
                "并发告警更新后，遥测 UPSERT 不得将读取到的旧告警状态写回");
    }
}
