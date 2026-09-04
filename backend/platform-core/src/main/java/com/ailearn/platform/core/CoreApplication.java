package com.ailearn.platform.core;

import com.ailearn.platform.shared.security.SharedSecurityConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Import;

/**
 * 业务核心微服务启动入口。
 */
@SpringBootApplication(
        scanBasePackages = {"com.ailearn.platform.shared", "com.ailearn.platform.core"}
)
// Core 作为下游服务显式启用网关透传身份的共享安全链，避免 Auth 服务误加载该配置。
@Import(SharedSecurityConfig.class)
public class CoreApplication {
    public static void main(String[] args) {
        SpringApplication.run(CoreApplication.class, args);
    }
}
