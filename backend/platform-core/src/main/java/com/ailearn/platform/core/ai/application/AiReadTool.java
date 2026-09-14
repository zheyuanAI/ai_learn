package com.ailearn.platform.core.ai.application;

import java.util.Map;
import java.util.Set;

/** AI 可调用的受控只读工具端口。 */
public interface AiReadTool {
    /** 工具稳定名称，必须与协议和审计一致。 */
    String name();

    /** 向模型公开的只读用途说明，不得描述任何写操作能力。 */
    default String description() {
        return name() + " 只读业务查询";
    }

    /** 向模型公开的 JSON Schema；服务端执行时仍必须再次校验参数。 */
    default Map<String, Object> parametersSchema() {
        return Map.of("type", "object", "additionalProperties", false, "properties", Map.of());
    }

    /** 调用该工具至少需要命中的一个领域查看权限。 */
    Set<String> anyOfPermissions();

    /** 执行只读查询；实现只能调用领域应用服务或 Facts 端口。 */
    AiToolResult execute(AiRequestContext context, Map<String, Object> arguments);

    /** 判断当前用户是否具备该工具所需的领域权限。 */
    default boolean isAllowed(AiRequestContext context) {
        Set<String> required = anyOfPermissions();
        return required == null || required.isEmpty()
                || required.stream().anyMatch(context::hasPermission);
    }
}
