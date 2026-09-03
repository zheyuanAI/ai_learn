package com.ailearn.platform.auth.config;

import com.ailearn.platform.auth.security.filter.JwtAuthenticationFilter;
import com.ailearn.platform.auth.security.handler.AuthAccessDeniedHandler;
import com.ailearn.platform.auth.security.handler.AuthAuthenticationEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security 统一安全过滤链与密码加解密配置。
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final AuthAuthenticationEntryPoint authAuthenticationEntryPoint;
    private final AuthAccessDeniedHandler authAccessDeniedHandler;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          AuthAuthenticationEntryPoint authAuthenticationEntryPoint,
                          AuthAccessDeniedHandler authAccessDeniedHandler) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.authAuthenticationEntryPoint = authAuthenticationEntryPoint;
        this.authAccessDeniedHandler = authAccessDeniedHandler;
    }

    /**
     * 密码哈希编码器（采用 BCrypt 强哈希算法）。
     *
     * @return PasswordEncoder 实例
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 配置安全过滤链规则与放行端点。
     *
     * @param http HttpSecurity 配置构建器
     * @return SecurityFilterChain 实例
     * @throws Exception 配置异常
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 禁用 CSRF（无状态 RESTful API）
                .csrf(AbstractHttpConfigurer::disable)
                // 无状态会话管理
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // 路由权限规则
                .authorizeHttpRequests(auth -> auth
                        // 开放端点：登录、JWKS 公钥、健康检查与 Swagger 文档
                        .requestMatchers(
                                "/api/auth/login",
                                "/api/auth/jwks",
                                "/actuator/**",
                                "/internal/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**",
                                "/error"
                        ).permitAll()
                        // 其它接口一律需要认证
                        .anyRequest().authenticated()
                )
                // 异常处理器配置
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authAuthenticationEntryPoint)
                        .accessDeniedHandler(authAccessDeniedHandler)
                )
                // 在账密过滤器前插入 JWT 认证与单会话顶替过滤器
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
