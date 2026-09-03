package com.ailearn.platform.shared.config;

import com.ailearn.platform.shared.filter.RequestIdFilter;
import com.ailearn.platform.shared.interceptor.HeaderContextInterceptor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 与 Servlet 过滤器通用自动配置。
 * <p>
 * 仅在 Servlet Web 环境下生效，注册 {@link RequestIdFilter} 与 {@link HeaderContextInterceptor}。
 * </p>
 */
@AutoConfiguration
@ConditionalOnClass(name = "org.springframework.web.servlet.config.annotation.WebMvcConfigurer")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class WebMvcConfig {

    @Configuration(proxyBeanMethods = false)
    public static class WebMvcConfigurerAdapter implements WebMvcConfigurer {

        @Override
        public void addInterceptors(InterceptorRegistry registry) {
            registry.addInterceptor(headerContextInterceptor())
                    .addPathPatterns("/**")
                    .excludePathPatterns("/actuator/**", "/internal/ping", "/swagger-ui/**", "/v3/api-docs/**");
        }

        @Bean
        @ConditionalOnMissingBean(HeaderContextInterceptor.class)
        public HeaderContextInterceptor headerContextInterceptor() {
            return new HeaderContextInterceptor();
        }
    }

    /**
     * 注册 Request-Id 过滤器为最高优先级 Filter。
     *
     * @return FilterRegistrationBean 包装的 {@link RequestIdFilter}
     */
    @Bean
    @ConditionalOnMissingBean(name = "requestIdFilterRegistrationBean")
    public FilterRegistrationBean<RequestIdFilter> requestIdFilterRegistrationBean() {
        FilterRegistrationBean<RequestIdFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(new RequestIdFilter());
        registrationBean.addUrlPatterns("/*");
        registrationBean.setName("requestIdFilter");
        registrationBean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registrationBean;
    }
}
