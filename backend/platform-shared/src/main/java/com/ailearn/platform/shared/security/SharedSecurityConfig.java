package com.ailearn.platform.shared.security;

import com.ailearn.platform.shared.api.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * 下游微服务共享 Spring Security 配置类。
 * <p>
 * 仅在包含 Spring Security 与 Servlet Web 环境下生效。
 * </p>
 */
@AutoConfiguration
@ConditionalOnClass(name = {
        "org.springframework.security.config.annotation.web.builders.HttpSecurity",
        "org.springframework.security.web.SecurityFilterChain"
})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SharedSecurityConfig {

    private final ObjectMapper objectMapper;

    public SharedSecurityConfig(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 内部安全配置装配器。
     */
    @Configuration(proxyBeanMethods = false)
    @EnableWebSecurity
    @EnableMethodSecurity(prePostEnabled = true)
    public class InnerSecurityConfiguration {

        @Bean
        public DownstreamSecurityFilter downstreamSecurityFilter(PermissionContextReader permissionContextReader) {
            return new DownstreamSecurityFilter(permissionContextReader, objectMapper);
        }

        @Bean
        public SecurityFilterChain downstreamSecurityFilterChain(
                HttpSecurity http, DownstreamSecurityFilter downstreamSecurityFilter) throws Exception {
            http
                    .csrf(AbstractHttpConfigurer::disable)
                    .formLogin(AbstractHttpConfigurer::disable)
                    .httpBasic(AbstractHttpConfigurer::disable)
                    .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                    .authorizeHttpRequests(auth -> auth
                            .requestMatchers(
                                    "/actuator/**",
                                    "/internal/**",
                                    "/v3/api-docs/**",
                                    "/swagger-ui/**",
                                    "/swagger-ui.html",
                                    "/webjars/**",
                                    "/doc.html",
                                    "/error"
                            ).permitAll()
                            // 除健康、文档和内部白名单外，所有业务请求必须先通过下游身份认证。
                            .anyRequest().authenticated()
                    )
                    .exceptionHandling(exceptions -> exceptions
                            .authenticationEntryPoint(customAuthenticationEntryPoint())
                            .accessDeniedHandler(customAccessDeniedHandler())
                    )
                    .addFilterBefore(downstreamSecurityFilter, UsernamePasswordAuthenticationFilter.class);

            return http.build();
        }

        @Bean
        public AuthenticationEntryPoint customAuthenticationEntryPoint() {
            return (request, response, authException) -> {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                ApiResponse<Void> apiResponse = ApiResponse.error(HttpServletResponse.SC_UNAUTHORIZED, "未登录或登录已过期: " + authException.getMessage());
                try (PrintWriter writer = response.getWriter()) {
                    writer.write(objectMapper.writeValueAsString(apiResponse));
                    writer.flush();
                }
            };
        }

        @Bean
        public AccessDeniedHandler customAccessDeniedHandler() {
            return (request, response, accessDeniedException) -> {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                ApiResponse<Void> apiResponse = ApiResponse.error(HttpServletResponse.SC_FORBIDDEN, "没有操作权限: " + accessDeniedException.getMessage());
                try (PrintWriter writer = response.getWriter()) {
                    writer.write(objectMapper.writeValueAsString(apiResponse));
                    writer.flush();
                }
            };
        }
    }
}
