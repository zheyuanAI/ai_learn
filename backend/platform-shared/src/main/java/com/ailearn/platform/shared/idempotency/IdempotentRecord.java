package com.ailearn.platform.shared.idempotency;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * 接口幂等性执行记录数据模型。
 * <p>
 * 用于记录写操作命令在幂等窗口期的执行状态、请求摘要与缓存响应报文。
 * </p>
 */
public class IdempotentRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 幂等状态枚举
     */
    public enum Status {
        /**
         * 正在执行中
         */
        PENDING,
        /**
         * 已成功完成
         */
        SUCCESS,
        /**
         * 执行失败
         */
        FAILED
    }

    /**
     * 客户端提交的唯一幂等键 (Idempotency-Key)
     */
    private String idempotencyKey;

    /**
     * 当前 PENDING 执行者的所有权凭证；SUCCESS/FAILED 记录可为空。
     */
    private UUID claimToken;

    /**
     * 所属租户 ID
     */
    private UUID tenantId;

    /**
     * 幂等当前处理状态
     */
    private Status status;

    /**
     * 请求参数 Hash 摘要（防参数被篡改但 key 相同）
     */
    private String requestHash;

    /**
     * 成功响应的 JSON 报文缓存
     */
    private String responseBody;

    /**
     * 记录创建时间
     */
    private OffsetDateTime createdAt;

    /**
     * 幂等记录过期失效时间
     */
    private OffsetDateTime expireAt;

    public IdempotentRecord() {
    }

    public IdempotentRecord(String idempotencyKey, UUID tenantId, Status status, String requestHash, String responseBody, OffsetDateTime createdAt, OffsetDateTime expireAt) {
        this.idempotencyKey = idempotencyKey;
        this.tenantId = tenantId;
        this.status = status;
        this.requestHash = requestHash;
        this.responseBody = responseBody;
        this.createdAt = createdAt;
        this.expireAt = expireAt;
    }

    /**
     * 创建带 claim token 的幂等记录。
     */
    public IdempotentRecord(String idempotencyKey, UUID tenantId, Status status, String requestHash,
                            String responseBody, OffsetDateTime createdAt, OffsetDateTime expireAt,
                            UUID claimToken) {
        this(idempotencyKey, tenantId, status, requestHash, responseBody, createdAt, expireAt);
        this.claimToken = claimToken;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public UUID getClaimToken() {
        return claimToken;
    }

    public void setClaimToken(UUID claimToken) {
        this.claimToken = claimToken;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
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

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getExpireAt() {
        return expireAt;
    }

    public void setExpireAt(OffsetDateTime expireAt) {
        this.expireAt = expireAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof IdempotentRecord that)) return false;
        return Objects.equals(idempotencyKey, that.idempotencyKey) &&
                Objects.equals(tenantId, that.tenantId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(idempotencyKey, tenantId);
    }

    @Override
    public String toString() {
        return "IdempotentRecord{" +
                "idempotencyKey='" + idempotencyKey + '\'' +
                ", tenantId=" + tenantId +
                ", status=" + status +
                ", requestHash='" + requestHash + '\'' +
                ", createdAt=" + createdAt +
                ", expireAt=" + expireAt +
                '}';
    }
}
