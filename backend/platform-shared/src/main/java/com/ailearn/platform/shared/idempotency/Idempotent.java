package com.ailearn.platform.shared.idempotency;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口幂等控制注解。
 * <p>
 * 标注在 Controller 或 Service 写操作方法上，自动基于请求头 {@code Idempotency-Key} 或 SpEL 表达式提取幂等键并执行防重控制。
 * </p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Idempotent {

    /**
     * 幂等 Key 的 SpEL 表达式，留空时默认从 HTTP 请求头 {@code Idempotency-Key} 中提取。
     *
     * @return SpEL 表达式或空字符串
     */
    String key() default "";

    /**
     * 幂等记录在存储中的保留有效期（单位：秒），默认 86400 秒（24小时）。
     *
     * @return 有效期秒数
     */
    long expireSeconds() default 86400L;

    /**
     * 当检测到重复提交或冲突时的提示信息。
     *
     * @return 错误描述
     */
    String message() default "请求正在处理或重复提交，请勿频繁操作";
}
