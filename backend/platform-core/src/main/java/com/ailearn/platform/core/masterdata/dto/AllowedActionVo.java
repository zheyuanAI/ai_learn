package com.ailearn.platform.core.masterdata.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 后端计算的主数据操作入口。
 * <p>
 * 用途：让前端按服务端返回的状态能力显示按钮，而不是自行推导状态操作。
 * 入参：动作名称、是否可执行及不可执行原因。
 * 出参：camelCase JSON 字段 action、enabled、reason。
 * </p>
 */
public class AllowedActionVo {

    private final String action;
    private final boolean enabled;
    private final String reason;

    /**
     * 创建一个动作能力描述。
     *
     * @param action  动作名称
     * @param enabled 是否允许执行
     * @param reason  禁用原因，可为空
     */
    @JsonCreator
    public AllowedActionVo(@JsonProperty("action") String action,
                           @JsonProperty("enabled") boolean enabled,
                           @JsonProperty("reason") String reason) {
        this.action = action;
        this.enabled = enabled;
        this.reason = reason;
    }

    public String getAction() {
        return action;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getReason() {
        return reason;
    }
}
