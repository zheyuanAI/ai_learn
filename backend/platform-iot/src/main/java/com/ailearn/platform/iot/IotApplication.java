package com.ailearn.platform.iot;

import com.ailearn.platform.shared.security.SharedSecurityConfig;
import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 物联网与设备遥测微服务启动入口。
 */
@SpringBootApplication(
        scanBasePackages = {"com.ailearn.platform.shared", "com.ailearn.platform.iot"}
)
// IoT 作为下游服务显式启用网关透传身份的共享安全链，避免 Auth 服务误加载该配置。
@Import(SharedSecurityConfig.class)
@EnableScheduling
public class IotApplication {
    public static void main(String[] args) {
        SpringApplication.run(IotApplication.class, args);
    }
}
