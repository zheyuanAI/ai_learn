package com.ailearn.platform.shared.domain;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * 平台通用领域实体持久化基类。
 * <p>
 * 定义所有业务表必须具备的核心审计与多租户字段：
 * <ul>
 *   <li>{@code id}：全局唯一主键 (UUID)</li>
 *   <li>{@code tenant_id}：多租户隔离标识 (UUID)</li>
 *   <li>{@code status}：业务状态标识</li>
 *   <li>{@code created_by}：创建者用户 ID</li>
 *   <li>{@code created_at}：创建时间 (带时区 OffsetDateTime)</li>
 *   <li>{@code updated_by}：最近更新者用户 ID</li>
 *   <li>{@code updated_at}：最近更新时间 (带时区 OffsetDateTime)</li>
 *   <li>{@code isdel}：逻辑删除标记（0 未删除，1 已删除）</li>
 * </ul>
 * </p>
 */
public abstract class BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.INPUT)
    @Schema(description = "主键 UUID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID id;

    @TableField(value = "tenant_id", fill = FieldFill.INSERT)
    @JsonProperty("tenant_id")
    @Schema(description = "租户 ID", example = "123e4567-e89b-12d3-a456-426614174000")
    private UUID tenantId;

    @TableField(value = "status")
    @Schema(description = "业务状态", example = "Draft")
    private String status;

    @TableField(value = "created_by", fill = FieldFill.INSERT)
    @JsonProperty("created_by")
    @Schema(description = "创建人用户 ID")
    private UUID createdBy;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    @JsonProperty("created_at")
    @Schema(description = "创建时间 (ISO-8601)")
    private OffsetDateTime createdAt;

    @TableField(value = "updated_by", fill = FieldFill.INSERT_UPDATE)
    @JsonProperty("updated_by")
    @Schema(description = "更新人用户 ID")
    private UUID updatedBy;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    @JsonProperty("updated_at")
    @Schema(description = "更新时间 (ISO-8601)")
    private OffsetDateTime updatedAt;

    @TableLogic(value = "0", delval = "1")
    @TableField(value = "isdel", fill = FieldFill.INSERT)
    @Schema(description = "逻辑删除标识 (0: 正常, 1: 已删除)")
    private Integer isdel;

    /**
     * 默认构造函数。
     */
    public BaseEntity() {
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(UUID createdBy) {
        this.createdBy = createdBy;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public UUID getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(UUID updatedBy) {
        this.updatedBy = updatedBy;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Integer getIsdel() {
        return isdel;
    }

    public void setIsdel(Integer isdel) {
        this.isdel = isdel;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BaseEntity that)) return false;
        return Objects.equals(id, that.id) &&
                Objects.equals(tenantId, that.tenantId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, tenantId);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" +
                "id=" + id +
                ", tenantId=" + tenantId +
                ", status='" + status + '\'' +
                ", createdBy=" + createdBy +
                ", createdAt=" + createdAt +
                ", updatedBy=" + updatedBy +
                ", updatedAt=" + updatedAt +
                ", isdel=" + isdel +
                '}';
    }
}
