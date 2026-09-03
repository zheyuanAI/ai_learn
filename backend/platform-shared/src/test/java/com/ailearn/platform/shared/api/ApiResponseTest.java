package com.ailearn.platform.shared.api;

import com.ailearn.platform.shared.context.RequestContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ApiResponse 统一响应结构测试")
class ApiResponseTest {

    @AfterEach
    void tearDown() {
        RequestContextHolder.clear();
    }

    @Test
    @DisplayName("测试构建成功响应")
    void testSuccessResponse() {
        String testRequestId = UUID.randomUUID().toString();
        RequestContextHolder.setRequestId(testRequestId);

        ApiResponse<String> response = ApiResponse.success("测试数据");

        assertTrue(response.isSuccess());
        assertEquals(200, response.getCode());
        assertEquals("操作成功", response.getMessage());
        assertEquals("测试数据", response.getData());
        assertEquals(testRequestId, response.getRequestId());
        assertNotNull(response.getTimestamp());
    }

    @Test
    @DisplayName("测试构建错误响应")
    void testErrorResponse() {
        ApiResponse<Void> response = ApiResponse.error(CommonErrorCode.NOT_FOUND, "指定的物料未找到");

        assertFalse(response.isSuccess());
        assertEquals(404, response.getCode());
        assertEquals("指定的物料未找到", response.getMessage());
        assertNull(response.getData());
        assertNotNull(response.getTimestamp());
    }
}
