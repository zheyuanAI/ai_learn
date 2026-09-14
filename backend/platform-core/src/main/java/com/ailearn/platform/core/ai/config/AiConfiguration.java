package com.ailearn.platform.core.ai.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 注册 AI Provider 配置；模型客户端将在配置启用且密钥存在时装配。 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AiProperties.class)
public class AiConfiguration {
    /** AI 流式请求使用虚拟线程隔离阻塞式上游 HTTP 读取，应用关闭时统一回收。 */
    @Bean(destroyMethod = "close")
    public ExecutorService aiStreamExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
