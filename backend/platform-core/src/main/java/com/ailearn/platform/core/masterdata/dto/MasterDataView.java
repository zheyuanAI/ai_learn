package com.ailearn.platform.core.masterdata.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 六类主数据视图的公共响应字段。
 * <p>
 * tenantId 不对外暴露，租户边界由可信上下文和 Repository 查询条件保证；本视图仅包含当前租户可见资源。
 * </p>
 */
public class MasterDataView {

    private UUID id;
    private String code;
    private String name;
    private String status;
    private String remark;
    private UUID createdBy;
    private OffsetDateTime createdAt;
    private UUID updatedBy;
    private OffsetDateTime updatedAt;
    private List<AllowedActionVo> allowedActions = List.of();

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
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

    public List<AllowedActionVo> getAllowedActions() {
        return allowedActions;
    }

    public void setAllowedActions(List<AllowedActionVo> allowedActions) {
        this.allowedActions = allowedActions == null ? List.of() : List.copyOf(allowedActions);
    }
}
