package com.ailearn.platform.core.operationaudit.application;

/** 关键业务应用服务写入结构化成功审计的端口。 */
@FunctionalInterface
public interface OperationAuditRecorder {
    OperationAuditRecorder NO_OP = command -> { };

    /**
     * 用途：记录一次已经成功完成的业务操作。
     * 入参：只含可信上下文和脱敏摘要的结构化命令；出参：无；流程：由实现与当前业务事务共同提交。
     *
     * @param command 成功操作审计命令
     */
    void recordSuccess(OperationAuditCommand command);
}
