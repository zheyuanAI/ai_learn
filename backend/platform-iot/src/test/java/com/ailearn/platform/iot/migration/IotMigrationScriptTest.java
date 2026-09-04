package com.ailearn.platform.iot.migration;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class IotMigrationScriptTest {

    @Test
    void v2DefinesTenantScopedDeviceFoundationAndRestrictsPhysicalDeletes() throws IOException {
        String sql;
        try (InputStream input = getClass().getResourceAsStream(
                "/db/migration/iot/V2__device_mqtt_telemetry_status_alarm.sql")) {
            assertTrue(input != null, "IoT V2 迁移脚本必须存在");
            sql = new String(input.readAllBytes(), StandardCharsets.UTF_8).toLowerCase();
        }
        String compact = sql.replaceAll("\\s+", " ");
        List<String> tables = List.of("iot_idempotency_record", "iot_device_profile", "iot_device_profile_metric", "iot_device",
                "iot_device_credential", "iot_message_dedup", "iot_device_telemetry", "iot_device_status",
                "iot_device_alarm_rule", "iot_device_alarm", "iot_alarm_context_task");
        for (String table : tables) {
            assertTrue(compact.contains("create table if not exists " + table), "缺少 IoT V2 表: " + table);
        }
        assertTrue(compact.contains("protocol_type varchar(16) not null check (protocol_type = 'mqtt')"),
                "设备协议必须由数据库限制为 MQTT");
        assertTrue(compact.contains("lifecycle_status varchar(16) not null default 'active' check (lifecycle_status in ('active', 'disabled'))"),
                "设备生命周期必须限制为 Active/Disabled");
        assertTrue(compact.contains("foreign key (tenant_id, profile_id) references iot_device_profile(tenant_id, id)"),
                "指标必须通过复合外键绑定同租户设备模型");
        assertTrue(compact.contains("foreign key (tenant_id, device_profile_id) references iot_device_profile(tenant_id, id)"),
                "设备必须通过复合外键绑定同租户设备模型");
        assertTrue(compact.contains("foreign key (tenant_id, device_id) references iot_device(tenant_id, id)"),
                "凭证和设备事实必须通过复合外键绑定同租户设备");
        assertTrue(compact.contains("secret_hash varchar(255) not null") && compact.contains("secret_salt varchar(96) not null"),
                "凭证只能保存摘要和盐，不得保存明文列");
        assertTrue(!compact.contains("plain_secret") && !compact.contains("on delete cascade"),
                "迁移不得保存明文凭证或级联物理删除历史设备事实");
        assertTrue(compact.contains("create or replace function iot_prevent_device_physical_delete()")
                        && compact.contains("before delete on iot_device")
                        && compact.contains("iot historical device facts cannot be physically deleted"),
                "设备物理删除必须由数据库触发器拒绝");
        assertTrue(compact.contains("isdel smallint not null default 0"),
                "设备基础表必须保留逻辑删除标志");
        assertTrue(compact.contains("on iot_idempotency_record (tenant_id, operation, idempotency_key) where isdel = 0"),
                "幂等唯一键必须按租户和操作域隔离");
        assertTrue(compact.contains("updated_at timestamptz not null default current_timestamp")
                        && compact.contains("updated_by uuid"),
                "告警事实必须保留可变审计字段");
        assertTrue(compact.contains("create unique index if not exists uq_iot_context_task_open_alarm")
                        && compact.contains("where status <> 'completed'"),
                "未完成上下文任务必须按租户和告警保持唯一");
    }
}
