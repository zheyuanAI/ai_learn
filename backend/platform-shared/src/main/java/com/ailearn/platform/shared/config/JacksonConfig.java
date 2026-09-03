package com.ailearn.platform.shared.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.TimeZone;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

/**
 * Jackson 全局序列化与反序列化配置。
 * <p>
 * 主要特性：
 * <ul>
 *   <li>{@link OffsetDateTime} 全局格式化为标准 ISO-8601 格式，且自动规范化为东八区（Asia/Shanghai, +08:00 偏移）</li>
 *   <li>{@link BigDecimal} 序列化禁用科学计数法，防止数值精度损失与前端展示失真</li>
 *   <li>反序列化遇到未知属性不抛出异常（容错处理）</li>
 *   <li>禁用日期转为数字时间戳</li>
 * </ul>
 * </p>
 */
@AutoConfiguration
public class JacksonConfig {

    public static final ZoneId SHANGHAI_ZONE = ZoneId.of("Asia/Shanghai");
    public static final String DATE_TIME_PATTERN = "yyyy-MM-dd'T'HH:mm:ssXXX";
    public static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern(DATE_TIME_PATTERN).withZone(SHANGHAI_ZONE);

    /**
     * 自定义 Jackson Builder，确保通过 Spring MVC / RestTemplate / OpenDoc 获取的 ObjectMapper 统一应用此规则。
     *
     * @return {@link Jackson2ObjectMapperBuilderCustomizer}
     */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jackson2ObjectMapperBuilderCustomizer() {
        return builder -> {
            // 时区配置
            builder.timeZone(TimeZone.getTimeZone(SHANGHAI_ZONE));

            // 禁用未知属性校验与日期数字序列化
            builder.featuresToDisable(
                    DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                    SerializationFeature.WRITE_DATES_AS_TIMESTAMPS
            );

            // 启用 BigDecimal 纯数字字符串模式（避免 1E+7 这类科学计数法）
            builder.featuresToEnable(
                    JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN
            );

            // 注册 JavaTime 时间模块定制序列化器
            JavaTimeModule javaTimeModule = new JavaTimeModule();
            javaTimeModule.addSerializer(OffsetDateTime.class, new OffsetDateTimeSerializer());
            javaTimeModule.addDeserializer(OffsetDateTime.class, new OffsetDateTimeDeserializer());
            javaTimeModule.addSerializer(BigDecimal.class, new BigDecimalPlainSerializer());

            builder.modules(javaTimeModule);
        };
    }

    /**
     * 构建主要的全局通用 ObjectMapper Bean。
     *
     * @param builder Jackson2ObjectMapperBuilder
     * @return 配置完毕的 {@link ObjectMapper}
     */
    @Bean
    @Primary
    @ConditionalOnMissingBean(ObjectMapper.class)
    public ObjectMapper objectMapper(Jackson2ObjectMapperBuilder builder) {
        return builder.build();
    }

    /**
     * OffsetDateTime 统一序列化器：转换为 Asia/Shanghai 并格式化为 ISO-8601 带时区偏移。
     */
    public static class OffsetDateTimeSerializer extends JsonSerializer<OffsetDateTime> {
        @Override
        public void serialize(OffsetDateTime value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            if (value == null) {
                gen.writeNull();
            } else {
                OffsetDateTime shanghaiTime = value.atZoneSameInstant(SHANGHAI_ZONE).toOffsetDateTime();
                gen.writeString(DATE_TIME_FORMATTER.format(shanghaiTime));
            }
        }
    }

    /**
     * OffsetDateTime 统一反序列化器：支持 ISO-8601 字符串解析并自动转换为 OffsetDateTime。
     */
    public static class OffsetDateTimeDeserializer extends JsonDeserializer<OffsetDateTime> {
        @Override
        public OffsetDateTime deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            String text = p.getText();
            if (text == null || text.trim().isEmpty()) {
                return null;
            }
            return OffsetDateTime.parse(text.trim(), DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        }
    }

    /**
     * BigDecimal 序列化器：输出为 PlainString 防止浮点精度丢失与科学计数法表示。
     */
    public static class BigDecimalPlainSerializer extends JsonSerializer<BigDecimal> {
        @Override
        public void serialize(BigDecimal value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            if (value == null) {
                gen.writeNull();
            } else {
                gen.writeNumber(value.toPlainString());
            }
        }
    }
}
