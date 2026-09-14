package com.ailearn.platform.core.operationaudit.application;

import com.ailearn.platform.core.operationaudit.domain.OperationAuditEntry;
import java.util.List;

/** Core 服务操作审计的本地持久化端口。 */
public interface OperationAuditStore {
    /** 保存结构化审计；异常必须向上传播以便成功业务事务回滚。 */
    void save(OperationAuditEntry entry);

    /** 按可信租户和业务对象读取操作时间线。 */
    List<OperationAuditEntry> find(OperationAuditQuery query);
}
