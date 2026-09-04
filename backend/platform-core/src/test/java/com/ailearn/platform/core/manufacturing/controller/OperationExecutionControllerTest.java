package com.ailearn.platform.core.manufacturing.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ailearn.platform.core.manufacturing.operation.application.OperationExecutionApplicationService;
import com.ailearn.platform.core.manufacturing.operation.controller.OperationExecutionController;
import com.ailearn.platform.core.manufacturing.operation.dto.OperationExecutionCreateRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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
 * 工序执行 Controller focused tests。
 * <p>
 * 只验证 REST 路由、事件时间/暂停原因绑定和幂等键透传；非法状态、设备冲突和工单联动由应用服务负责。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class OperationExecutionControllerTest {

    private static final UUID EXECUTION_ID = UUID.fromString("a7000000-0000-0000-0000-000000000001");
    private static final UUID DISPATCH_ID = UUID.fromString("a7000000-0000-0000-0000-000000000002");
    private static final UUID WORK_ORDER_ID = UUID.fromString("a7000000-0000-0000-0000-000000000003");
    private static final UUID OPERATION_ID = UUID.fromString("a7000000-0000-0000-0000-000000000004");
    private static final String OCCURRED_AT = "2026-09-04T08:00:00Z";

    @Mock
    private OperationExecutionApplicationService service;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    /**
     * 准备独立 MVC 测试入口和 Java 时间 JSON 转换器。
     * 入参：无；出参：无；流程：仅装配待测工序执行 Controller，不连接数据库。
     */
    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        mockMvc = MockMvcBuilders.standaloneSetup(new OperationExecutionController(service))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    /**
     * 验证工序执行创建使用现有 OperationExecutionCreateRequest，并保留幂等键。
     * 入参：派工、工单和工序 JSON；出参：200 ApiResponse；流程：Controller 只调用工序执行应用端口。
     */
    @Test
    void createBindsExistingDtoAndIdempotencyKey() throws Exception {
        OperationExecutionCreateRequest request = new OperationExecutionCreateRequest(
                DISPATCH_ID, WORK_ORDER_ID, OPERATION_ID, null);

        mockMvc.perform(post("/api/operation-executions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "execution-create")
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(service).create(eq(request), eq("execution-create"));
    }

    /**
     * 验证执行详情对空查询结果保持不可见。
     * 入参：执行实例 ID；出参：200 且 data 为空；流程：透传应用端口的 Optional.empty()。
     */
    @Test
    void detailKeepsEmptyResultInvisible() throws Exception {
        when(service.find(EXECUTION_ID)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/operation-executions/{id}", EXECUTION_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(service).find(EXECUTION_ID);
    }

    /**
     * 验证开始、暂停、恢复、完成四条事件路由绑定 occurred_at/reason 并透传幂等键。
     * 入参：执行实例 ID、事件时间和暂停原因；出参：四个成功响应；流程：分别调用应用端口对应状态动作。
     */
    @Test
    void timelineCommandsBindEventFieldsAndDelegate() throws Exception {
        mockMvc.perform(post("/api/operation-executions/{id}/start", EXECUTION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "execution-start")
                        .content("{\"occurred_at\":\"" + OCCURRED_AT + "\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/operation-executions/{id}/pause", EXECUTION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "execution-pause")
                        .content("{\"occurred_at\":\"" + OCCURRED_AT + "\",\"reason\":\"设备换刀\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/operation-executions/{id}/resume", EXECUTION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "execution-resume")
                        .content("{\"occurred_at\":\"" + OCCURRED_AT + "\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/operation-executions/{id}/complete", EXECUTION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "execution-complete")
                        .content("{\"occurred_at\":\"" + OCCURRED_AT + "\"}"))
                .andExpect(status().isOk());

        OffsetDateTime occurredAt = OffsetDateTime.parse(OCCURRED_AT);
        verify(service).start(EXECUTION_ID, occurredAt, "execution-start");
        verify(service).pause(EXECUTION_ID, "设备换刀", occurredAt, "execution-pause");
        verify(service).resume(EXECUTION_ID, occurredAt, "execution-resume");
        verify(service).complete(EXECUTION_ID, occurredAt, "execution-complete");
    }
}
