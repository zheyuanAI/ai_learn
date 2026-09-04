package com.ailearn.platform.core.manufacturing.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ailearn.platform.core.manufacturing.execution.application.WorkOrderExecutionService;
import com.ailearn.platform.core.manufacturing.execution.controller.WorkOrderExecutionController;
import com.ailearn.platform.core.manufacturing.foundation.dto.WorkOrderCreateRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

/**
 * 工单生命周期 Controller focused tests。
 * <p>
 * 只验证 HTTP 路由、统一响应、幂等键透传和人工原因绑定；状态机与租户安全由应用服务测试负责。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class WorkOrderExecutionControllerTest {

    private static final UUID WORK_ORDER_ID = UUID.fromString("a5000000-0000-0000-0000-000000000001");
    private static final UUID PRODUCT_ID = UUID.fromString("a5000000-0000-0000-0000-000000000002");
    private static final UUID BOM_ID = UUID.fromString("a5000000-0000-0000-0000-000000000003");
    private static final UUID ROUTING_ID = UUID.fromString("a5000000-0000-0000-0000-000000000004");

    @Mock
    private WorkOrderExecutionService service;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    /**
     * 准备不启动 Spring 容器的 MVC 测试入口和 Java 时间 JSON 转换器。
     * 入参：无；出参：无；流程：仅装配待测 Controller，避免连接数据库或加载其他模块。
     */
    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        mockMvc = MockMvcBuilders.standaloneSetup(new WorkOrderExecutionController(service))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    /**
     * 验证创建接口绑定现有 WorkOrderCreateRequest，并原样透传 Idempotency-Key。
     * 入参：模拟工单创建 JSON；出参：200 ApiResponse；流程：请求进入 Controller 后只调用一次应用端口。
     */
    @Test
    void createBindsExistingDtoAndIdempotencyKey() throws Exception {
        WorkOrderCreateRequest request = new WorkOrderCreateRequest(null, PRODUCT_ID,
                new BigDecimal("10"), OffsetDateTime.parse("2026-09-04T08:00:00Z"),
                OffsetDateTime.parse("2026-09-04T16:00:00Z"), BOM_ID, ROUTING_ID, null);

        mockMvc.perform(post("/api/work-orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "wo-create")
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(service).createWorkOrder(eq(request), eq("wo-create"));
    }

    /**
     * 验证工单详情只包装应用端口查询结果，空结果不会在 Controller 层猜测跨租户资源。
     * 入参：工单 ID；出参：200 且 data 为空；流程：透传 Optional.empty()。
     */
    @Test
    void detailKeepsEmptyResultInvisible() throws Exception {
        when(service.find(WORK_ORDER_ID)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/work-orders/{id}", WORK_ORDER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(service).find(WORK_ORDER_ID);
    }

    /**
     * 验证提交、审核和正常完成命令共用幂等键 Header，Controller 不自行改变状态。
     * 入参：工单 ID 与三个命令路径；出参：三个成功响应；流程：分别透传到对应应用端口方法。
     */
    @Test
    void lifecycleCommandsDelegateWithIdempotencyKey() throws Exception {
        mockMvc.perform(post("/api/work-orders/{id}/submit", WORK_ORDER_ID)
                        .header("Idempotency-Key", "wo-submit"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/work-orders/{id}/approve", WORK_ORDER_ID)
                        .header("Idempotency-Key", "wo-approve"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/work-orders/{id}/complete", WORK_ORDER_ID)
                        .header("Idempotency-Key", "wo-complete"))
                .andExpect(status().isOk());

        verify(service).submit(WORK_ORDER_ID, "wo-submit");
        verify(service).approve(WORK_ORDER_ID, "wo-approve");
        verify(service).complete(WORK_ORDER_ID, "wo-complete");
    }

    /**
     * 验证驳回与人工完成请求体支持契约字段，并将原因交给应用端口做非空和状态校验。
     * 入参：rejection_reason、completion_reason；出参：成功响应；流程：Controller 只提取人工原因并透传幂等键。
     */
    @Test
    void reasonCommandsBindContractFields() throws Exception {
        mockMvc.perform(post("/api/work-orders/{id}/reject", WORK_ORDER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "wo-reject")
                        .content("{\"rejection_reason\":\"需要调整数量\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/work-orders/{id}/manual-complete", WORK_ORDER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "wo-manual")
                        .content("{\"completion_reason\":\"客户取消剩余数量\"}"))
                .andExpect(status().isOk());

        verify(service).reject(WORK_ORDER_ID, "需要调整数量", "wo-reject");
        verify(service).manualComplete(WORK_ORDER_ID, "客户取消剩余数量", "wo-manual");
    }
}
