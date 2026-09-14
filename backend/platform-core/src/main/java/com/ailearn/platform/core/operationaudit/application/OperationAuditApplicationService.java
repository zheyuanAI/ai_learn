package com.ailearn.platform.core.operationaudit.application;

import com.ailearn.platform.core.operationaudit.domain.OperationAuditEntry;
import com.ailearn.platform.core.operationaudit.domain.OperationAuditResult;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Core 结构化业务操作审计应用服务。 */
@Service
public class OperationAuditApplicationService implements OperationAuditRecorder {
    private final OperationAuditStore store;

    /** 注入 Core 本地审计存储。 */
    public OperationAuditApplicationService(OperationAuditStore store) {
        this.store = store;
    }

    /**
     * 用途：把领域服务提供的成功操作转换为统一审计记录。
     * 入参：可信操作者、业务对象和幂等键；出参：无；流程：只保存幂等键摘要后写入本地事务表。
     */
    @Override
    public void recordSuccess(OperationAuditCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("操作审计命令不能为空");
        }
        OffsetDateTime occurredAt = command.occurredAt() == null
                ? OffsetDateTime.now(ZoneOffset.UTC) : command.occurredAt();
        store.save(new OperationAuditEntry(UUID.randomUUID(), command.tenantId(),
                text(command.actorType(), 16), command.actorId(), nullable(command.actorAccount(), 128),
                nullable(command.sessionId(), 128), text(command.requestId(), 128),
                text(command.actionCode(), 128), text(command.entityType(), 64), command.entityId(),
                nullable(command.entityNo(), 128), nullable(command.beforeStatus(), 64),
                nullable(command.afterStatus(), 64), nullable(command.operationSummary(), 2000),
                nullable(command.changedFieldsSummary(), 2000), OperationAuditResult.SUCCESS,
                null, null, hash(command.idempotencyKey()), occurredAt));
    }

    /** 按可信租户查询业务对象时间线。 */
    public List<OperationAuditEntry> query(OperationAuditQuery query) {
        return List.copyOf(store.find(query));
    }

    private static String text(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("操作审计必要文本不能为空");
        }
        return truncate(value.trim(), maxLength);
    }

    private static String nullable(String value, int maxLength) {
        return value == null || value.isBlank() ? null : truncate(value.trim(), maxLength);
    }

    private static String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private static String hash(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK 必须提供 SHA-256", exception);
        }
    }
}
