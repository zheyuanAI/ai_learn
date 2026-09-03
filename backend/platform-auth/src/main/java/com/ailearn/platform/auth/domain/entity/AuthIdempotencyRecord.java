package com.ailearn.platform.auth.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * 认证服务幂等控制记录实体（auth_idempotency_record）。
 * <p>
 * 用于防止关键写操作（如会话建立、注销、密码重置）的重复提交或并发竞争。
 * </p>
 */
@TableName("auth_idempotency_record")
public class AuthIdempotencyRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 记录唯一标识 ID
     */
    @TableId(type = IdType.INPUT)
    private UUID id;

    /**
     * 所属租户 ID
     */
    @TableField("tenant_id")
    private UUID tenantId;

    /**
     * 请求接口路径 (如 /api/auth/login)
     */
    @TableField("endpoint")
    private String endpoint;

    /**
     * 幂等键（客户端传入或根据请求派生）
     */
    @TableField("idempotency_key")
    private String idempotencyKey;

    /**
     * 请求参数摘要哈希
     */
    @TableField("request_hash")
    private String requestHash;

    /**
     * 响应缓存结果
     */
    @TableField("response_body")
    private String responseBody;

    /**
     * 执行状态（PROCESSING: 处理中, SUCCESS: 成功完成, FAILED: 失败）
     */
    @TableField("status")
    private String status;

    /**
     * 创建时间
     */
    @TableField("created_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private LocalDateTime createdAt;

    public AuthIdempotencyRecord() {
        this.id = UUID.randomUUID();
        this.status = "PROCESSING";
        this.createdAt = LocalDateTime.now();
    }

    public AuthIdempotencyRecord(UUID id, UUID tenantId, String endpoint, String idempotencyKey, String requestHash, String status) {
        this.id = id != null ? id : UUID.randomUUID();
        this.tenantId = tenantId;
        this.endpoint = endpoint;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.status = status != null ? status : "PROCESSING";
        this.createdAt = LocalDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public void setRequestHash(String requestHash) {
        this.requestHash = requestHash;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public void setResponseBody(String responseBody) {
        this.responseBody = responseBody;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AuthIdempotencyRecord that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
