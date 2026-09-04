package com.ailearn.platform.core.manufacturing.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ailearn.platform.core.manufacturing.dispatch.application.DispatchApplicationService;
import com.ailearn.platform.core.manufacturing.dispatch.controller.DispatchController;
import com.ailearn.platform.core.manufacturing.dispatch.dto.DispatchCreateRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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
 * 派工 Controller focused tests。
 * <p>
 * 只验证路由、现有 DTO 绑定和幂等键透传，不在 Controller 层复制工单状态或派工状态规则。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class DispatchControllerTest {

    private static final UUID DISPATCH_ID = UUID.fromString("a6000000-0000-0000-0000-000000000001");
    private static final UUID WORK_ORDER_ID = UUID.fromString("a6000000-0000-0000-0000-000000000002");
    private static final UUID OPERATION_ID = UUID.fromString("a6000000-0000-0000-0000-000000000003");

    @Mock
    private DispatchApplicationService service;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    /**
     * 准备独立 MVC 测试入口。
     * 入参：无；出参：无；流程：仅装配待测派工 Controller，避免加载数据库和其他业务 Bean。
     */
    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        mockMvc = MockMvcBuilders.standaloneSetup(new DispatchController(service))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    /**
     * 验证派工创建使用现有 DispatchCreateRequest，并保留 HTTP 幂等键。
     * 入参：工单、工序和可选设备 JSON；出参：200 ApiResponse；流程：Controller 只调用派工应用端口。
     */
    @Test
    void createBindsExistingDtoAndIdempotencyKey() throws Exception {
        DispatchCreateRequest request = new DispatchCreateRequest(WORK_ORDER_ID, OPERATION_ID, null);

        mockMvc.perform(post("/api/dispatch-orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "dispatch-create")
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(service).create(eq(request), eq("dispatch-create"));
    }

    /**
     * 验证派工详情对空查询结果保持不可见。
     * 入参：派工单 ID；出参：200 且 data 为空；流程：透传应用端口的 Optional.empty()。
     */
    @Test
    void detailKeepsEmptyResultInvisible() throws Exception {
        when(service.find(DISPATCH_ID)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/dispatch-orders/{id}", DISPATCH_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(service).find(DISPATCH_ID);
    }

    /**
     * 验证派工发布、进入处理和完成三个现有端口动作均要求幂等键。
     * 入参：派工单 ID 与命令路径；出参：成功响应；流程：分别透传应用端口，不在 Controller 层提前改变状态。
     */
    @Test
    void stateCommandsDelegateWithIdempotencyKey() throws Exception {
        mockMvc.perform(post("/api/dispatch-orders/{id}/release", DISPATCH_ID)
                        .header("Idempotency-Key", "dispatch-release"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/dispatch-orders/{id}/start-processing", DISPATCH_ID)
                        .header("Idempotency-Key", "dispatch-start"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/dispatch-orders/{id}/complete", DISPATCH_ID)
                        .header("Idempotency-Key", "dispatch-complete"))
                .andExpect(status().isOk());

        verify(service).release(DISPATCH_ID, "dispatch-release");
        verify(service).startProcessing(DISPATCH_ID, "dispatch-start");
        verify(service).complete(DISPATCH_ID, "dispatch-complete");
    }
}
