package com.ailearn.platform.core.traceability.ports;

/** 上游事实查询不可用，供看板决定返回陈旧缓存还是明确失败。 */
public class FactQueryUnavailableException extends RuntimeException {

    public FactQueryUnavailableException(String message) {
        super(message);
    }

    public FactQueryUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
