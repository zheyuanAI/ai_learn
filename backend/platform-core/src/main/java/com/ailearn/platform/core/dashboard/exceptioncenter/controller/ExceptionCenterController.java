package com.ailearn.platform.core.dashboard.exceptioncenter.controller;

import com.ailearn.platform.core.dashboard.exceptioncenter.application.ExceptionCenterApplicationService;
import com.ailearn.platform.core.dashboard.exceptioncenter.domain.ExceptionCenterPage;
import com.ailearn.platform.core.traceability.web.TrustedFactsQueryContextFactory;
import com.ailearn.platform.shared.api.ApiResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 异常中心 REST API；租户只从可信认证上下文取得。 */
@RestController
@RequestMapping("/api/exception-center")
// S7 控制器由同一开关统一启用，避免组件扫描早于条件 Bean 注册造成启动顺序依赖。
@ConditionalOnProperty(prefix = "core.facts.iot", name = "enabled", havingValue = "true")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class ExceptionCenterController {
    private final ExceptionCenterApplicationService service;
    private final TrustedFactsQueryContextFactory contextFactory;

    /** 注入异常中心服务和可信上下文工厂。 */
    public ExceptionCenterController(ExceptionCenterApplicationService service,
                                     TrustedFactsQueryContextFactory contextFactory) {
        this.service = service;
        this.contextFactory = contextFactory;
    }

    /** 查询库存、生产和设备告警派生异常，支持 source/severity 分页筛选。 */
    @GetMapping
    @PreAuthorize("hasAuthority('dashboard:view') or hasAuthority('dashboard:exception:view')")
    public ApiResponse<ExceptionCenterPage> query(
            @RequestParam(name = "time_range", required = false) String timeRange,
            @RequestParam(name = "source", required = false) String source,
            @RequestParam(name = "severity", required = false) String severity,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        return ApiResponse.success(service.query(contextFactory.current(), timeRange, source, severity, page, size));
    }
}
