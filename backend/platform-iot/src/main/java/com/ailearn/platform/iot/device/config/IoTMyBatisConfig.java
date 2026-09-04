package com.ailearn.platform.iot.device.config;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/** IoT 预留事实 Mapper 扫描配置；Task17 当前管理基础域使用 JdbcTemplate。 */
@Configuration
@MapperScan(basePackages = "com.ailearn.platform.iot", annotationClass = Mapper.class)
public class IoTMyBatisConfig {
}
