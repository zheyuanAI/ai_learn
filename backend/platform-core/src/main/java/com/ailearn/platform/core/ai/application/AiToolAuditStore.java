package com.ailearn.platform.core.ai.application;

import com.ailearn.platform.core.ai.domain.ToolAuditEntry;

/** AI 工具审计持久化端口。 */
public interface AiToolAuditStore {
    /** 保存一条成功、失败、超时或拒绝审计。 */
    void save(ToolAuditEntry entry);
}
